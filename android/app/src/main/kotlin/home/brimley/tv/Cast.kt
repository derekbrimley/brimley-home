package home.brimley.tv

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

// Home movies are plain MP4 files, and the remote port (6466) can only open app
// links. So they go to the TV's Cast receiver on 8009 instead: launch Google's
// Default Media Receiver and load the file, the same player the site's Play on
// TV button uses. One short session; the receiver keeps playing after we hang up,
// and the remote's play/pause and OK keys still work on it.

data class CastVideo(val url: String, val title: String, val posterUrl: String?)

fun interface VideoCaster { suspend fun cast(host: String, video: CastVideo) }

// One Cast channel message (CastMessage in cast_channel.proto), always a JSON string.
data class CastMessage(val source: String, val destination: String, val namespace: String, val payload: String) {
    fun encode(): ByteArray = ProtoWriter()
        .varint(1, 0)          // protocol_version CASTV2_1_0 (required, so written even as 0)
        .string(2, source)
        .string(3, destination)
        .string(4, namespace)
        .varint(5, 0)          // payload_type STRING
        .string(6, payload)
        .toByteArray()

    companion object {
        fun decode(data: ByteArray): CastMessage {
            val m = ProtoMessage.parse(data)
            return CastMessage(m.string(2).orEmpty(), m.string(3).orEmpty(), m.string(4).orEmpty(), m.string(6).orEmpty())
        }
    }
}

// The conversation, without sockets: start() opens it, and each message from the
// TV gives the replies to send. `done` turns true once the video is loading, and
// `failure` says why it can't.
class CastProtocol(private val video: CastVideo) {
    var done = false; private set
    var failure: String? = null; private set
    private var transport: String? = null

    fun start(): List<CastMessage> = listOf(
        msg(RECEIVER, NS_CONNECTION, buildJsonObject { put("type", "CONNECT") }),
        msg(RECEIVER, NS_RECEIVER, buildJsonObject { put("type", "LAUNCH"); put("appId", DEFAULT_RECEIVER); put("requestId", LAUNCH_ID) }),
    )

    fun onMessage(m: CastMessage): List<CastMessage> {
        val body = runCatching { Json.parseToJsonElement(m.payload).jsonObject }.getOrNull() ?: return emptyList()
        val type = body["type"]?.jsonPrimitive?.contentOrNull
        return when {
            m.namespace == NS_HEARTBEAT && type == "PING" -> listOf(msg(m.source, NS_HEARTBEAT, buildJsonObject { put("type", "PONG") }))
            m.namespace == NS_RECEIVER && type == "LAUNCH_ERROR" -> { failure = "The TV wouldn't open its video player"; emptyList() }
            m.namespace == NS_RECEIVER && type == "RECEIVER_STATUS" && transport == null -> {
                val id = runningTransport(body) ?: return emptyList()
                transport = id
                listOf(msg(id, NS_CONNECTION, buildJsonObject { put("type", "CONNECT") }), load(id))
            }
            m.namespace == NS_MEDIA && type in LOAD_FAILURES -> { failure = "The TV couldn't play this video"; emptyList() }
            m.namespace == NS_MEDIA && type == "MEDIA_STATUS" && body["requestId"]?.jsonPrimitive?.intOrNull == LOAD_ID -> { done = true; emptyList() }
            else -> emptyList()
        }
    }

    private fun runningTransport(status: JsonObject): String? =
        status["status"]?.jsonObject?.get("applications")?.jsonArray
            ?.map { it.jsonObject }
            ?.firstOrNull { it["appId"]?.jsonPrimitive?.contentOrNull == DEFAULT_RECEIVER }
            ?.get("transportId")?.jsonPrimitive?.contentOrNull

    private fun load(to: String) = msg(to, NS_MEDIA, buildJsonObject {
        put("type", "LOAD")
        put("requestId", LOAD_ID)
        put("autoplay", true)
        put("currentTime", 0)
        put("media", buildJsonObject {
            put("contentId", video.url)
            put("contentType", "video/mp4")
            put("streamType", "BUFFERED")
            put("metadata", buildJsonObject {
                put("metadataType", 0)
                put("title", video.title)
                video.posterUrl?.let { url -> put("images", buildJsonArray { add(buildJsonObject { put("url", url) }) }) }
            })
        })
    })

    private fun msg(to: String, namespace: String, body: JsonObject) = CastMessage(SENDER, to, namespace, body.toString())

    companion object {
        const val SENDER = "sender-brimley"
        const val RECEIVER = "receiver-0"
        const val DEFAULT_RECEIVER = "CC1AD845"
        const val NS_CONNECTION = "urn:x-cast:com.google.cast.tp.connection"
        const val NS_HEARTBEAT = "urn:x-cast:com.google.cast.tp.heartbeat"
        const val NS_RECEIVER = "urn:x-cast:com.google.cast.receiver"
        const val NS_MEDIA = "urn:x-cast:com.google.cast.media"
        private const val LAUNCH_ID = 1
        private const val LOAD_ID = 2
        private val LOAD_FAILURES = setOf("LOAD_FAILED", "LOAD_CANCELLED", "INVALID_REQUEST")
    }
}

// Cast frames are a 4-byte big-endian length, not the remote port's varint.
class CastClient : VideoCaster {
    override suspend fun cast(host: String, video: CastVideo) = withContext(Dispatchers.IO) {
        val socket = open(host)
        try {
            val input = DataInputStream(socket.inputStream)
            val output = DataOutputStream(socket.outputStream)
            fun send(m: CastMessage) { val b = m.encode(); output.writeInt(b.size); output.write(b); output.flush() }

            val protocol = CastProtocol(video)
            protocol.start().forEach(::send)
            val deadline = System.currentTimeMillis() + TIMEOUT_MS
            while (!protocol.done) {
                protocol.failure?.let { throw IOException(it) }
                if (System.currentTimeMillis() > deadline) throw IOException("The TV didn't start the video")
                val len = input.readInt()
                if (len !in 0..MAX_FRAME) throw IOException("Bad message from the TV")
                val frame = ByteArray(len).also { input.readFully(it) }
                protocol.onMessage(CastMessage.decode(frame)).forEach(::send)
            }
        } finally {
            runCatching { socket.close() }
        }
    }

    // Cast devices present a self-signed certificate; we only ever talk to the paired TV's address.
    private fun open(host: String): SSLSocket {
        val ctx = SSLContext.getInstance("TLS")
        ctx.init(null, arrayOf(AcceptAny), null)
        val s = ctx.socketFactory.createSocket() as SSLSocket
        try {
            s.connect(InetSocketAddress(host, PORT), CONNECT_TIMEOUT_MS)
            s.soTimeout = READ_TIMEOUT_MS
            s.startHandshake()
        } catch (e: Exception) {
            runCatching { s.close() }
            throw IOException("Couldn't reach the TV's video player", e)
        }
        return s
    }

    private object AcceptAny : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    private companion object {
        const val PORT = 8009
        const val CONNECT_TIMEOUT_MS = 5_000
        const val READ_TIMEOUT_MS = 10_000
        const val TIMEOUT_MS = 25_000L     // launching the receiver on a sleepy TV takes a while
        const val MAX_FRAME = 1_000_000
    }
}
