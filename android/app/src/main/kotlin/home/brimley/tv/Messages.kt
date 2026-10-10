package home.brimley.tv

import java.math.BigInteger
import java.security.MessageDigest

// Messages for the Android TV remote protocol v2, as laid out in
// androidtvremote2's polo.proto (pairing, port 6467) and
// remotemessage.proto (remote, port 6466). Field numbers are the protocol's.

object PairingMessages {
    private const val HEXADECIMAL = 3
    private const val ROLE_INPUT = 1

    private fun outer(build: ProtoWriter.() -> Unit) =
        ProtoWriter().varint(1, 2).varint(2, 200).apply(build).toByteArray()   // protocol_version, status OK

    fun request(clientName: String) = outer { message(10) { string(1, "atvremote"); string(2, clientName) } }
    fun options() = outer { message(20) { message(1) { varint(1, HEXADECIMAL); varint(2, 6) }; varint(3, ROLE_INPUT) } }
    fun configuration() = outer { message(30) { message(1) { varint(1, HEXADECIMAL); varint(2, 6) }; varint(2, ROLE_INPUT) } }
    fun secret(secret: ByteArray) = outer { message(40) { bytes(1, secret) } }

    // The TV's status in a reply: 200 is OK, 400-402 are refusals.
    fun status(frame: ByteArray): Int = ProtoMessage.parse(frame).int(2) ?: 0
}

object PairingSecret {
    // The TV shows six hex characters: a check byte, then a two-byte nonce.
    fun normalize(code: String): String? =
        code.trim().uppercase().takeIf { it.length == 6 && it.all { c -> c in '0'..'9' || c in 'A'..'F' } }

    // The secret to send, or null if the code can't be right (the first byte
    // of the hash must equal the code's check byte).
    fun compute(code: String, clientModulus: BigInteger, clientExponent: BigInteger, serverModulus: BigInteger, serverExponent: BigInteger): ByteArray? {
        val c = normalize(code) ?: return null
        val md = MessageDigest.getInstance("SHA-256")
        listOf(clientModulus, clientExponent, serverModulus, serverExponent).forEach { md.update(unsigned(it)) }
        md.update(ByteArray(2) { c.substring(2 + it * 2, 4 + it * 2).toInt(16).toByte() })
        val hash = md.digest()
        return if ((hash[0].toInt() and 0xff) == c.substring(0, 2).toInt(16)) hash else null
    }

    private fun unsigned(n: BigInteger): ByteArray {
        val b = n.toByteArray()
        return if (b.size > 1 && b[0] == 0.toByte()) b.copyOfRange(1, b.size) else b
    }
}

object KeyCode {
    const val POWER = 26
    const val VOLUME_UP = 24
    const val VOLUME_DOWN = 25
    const val MEDIA_PLAY_PAUSE = 85
}

object RemoteMessages {
    private const val FEATURES = 622   // the feature mask androidtvremote2 sends: ping, key, power, volume, app link
    private const val SHORT = 3

    fun configure(model: String, vendor: String) = ProtoWriter().message(1) {
        varint(1, FEATURES)
        message(2) { string(1, model); string(2, vendor); varint(3, 1); string(4, "1"); string(5, "atvremote"); string(6, "1.0.0") }
    }.toByteArray()
    fun setActive() = ProtoWriter().message(2) { varint(1, FEATURES) }.toByteArray()
    fun pingResponse(val1: Int) = ProtoWriter().message(9) { varint(1, val1) }.toByteArray()
    fun key(code: Int) = ProtoWriter().message(10) { varint(1, code); varint(2, SHORT) }.toByteArray()
    fun appLink(link: String) = ProtoWriter().message(90) { string(1, link) }.toByteArray()
}

data class RemoteStatus(
    val poweredOn: Boolean? = null,   // null until the TV says
    val appPackage: String? = null,
    val volume: Int? = null,
    val volumeMax: Int? = null,
)

// Reacts to one message from the TV: returns the replies to send and keeps
// the latest power, app and volume.
class RemoteProtocol(private val model: String, private val vendor: String) {
    var status = RemoteStatus()
        private set

    fun onMessage(frame: ByteArray): List<ByteArray> {
        val m = ProtoMessage.parse(frame)
        val replies = mutableListOf<ByteArray>()
        if (m.has(1)) replies += RemoteMessages.configure(model, vendor)
        if (m.has(2)) replies += RemoteMessages.setActive()
        m.message(8)?.let { replies += RemoteMessages.pingResponse(it.int(1) ?: 0) }
        m.message(40)?.let { status = status.copy(poweredOn = it.bool(1)) }
        m.message(20)?.message(1)?.string(12)?.let { status = status.copy(appPackage = it) }
        m.message(50)?.let { status = status.copy(volume = it.int(7), volumeMax = it.int(6)) }
        return replies
    }
}
