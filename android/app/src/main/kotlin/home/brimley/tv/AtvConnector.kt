package home.brimley.tv

import android.os.Build
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.security.interfaces.RSAPublicKey
import javax.net.ssl.SSLSocket

// Real sockets to the TV: pairing on 6467, the remote on 6466.
class AtvConnector(private val identity: TvIdentity, private val scope: CoroutineScope) : TvConnector {
    override suspend fun connect(host: String, pinnedCertSha256: String): TvSession = withContext(Dispatchers.IO) {
        val socket = identity.openSocket(host, REMOTE_PORT)
        if (identity.fingerprint(socket) != pinnedCertSha256) {
            runCatching { socket.close() }
            throw TvCertificateChanged()
        }
        AtvSession(socket).also { it.start(scope) }
    }

    override suspend fun startPairing(host: String): PairingSession = withContext(Dispatchers.IO) {
        val socket = identity.openSocket(host, PAIRING_PORT)
        try {
            for (frame in listOf(PairingMessages.request("Brimley Home"), PairingMessages.options(), PairingMessages.configuration())) {
                writeFrame(socket.outputStream, frame)
                val status = PairingMessages.status(readFrame(socket.inputStream))
                if (status != 200) throw IOException("The TV said no to pairing ($status)")
            }
        } catch (e: Exception) {
            runCatching { socket.close() }
            throw e
        }
        AtvPairing(socket, identity)
    }

    private companion object {
        const val REMOTE_PORT = 6466
        const val PAIRING_PORT = 6467
    }
}

private class AtvPairing(private val socket: SSLSocket, private val identity: TvIdentity) : PairingSession {
    override val serverCertSha256: String = identity.fingerprint(socket)

    override suspend fun submit(code: String): PairResult = withContext(Dispatchers.IO) {
        val server = socket.session.peerCertificates[0].publicKey as RSAPublicKey
        val client = identity.publicKey()
        val secret = PairingSecret.compute(code, client.modulus, client.publicExponent, server.modulus, server.publicExponent)
            ?: return@withContext PairResult.WrongCode
        runCatching {
            writeFrame(socket.outputStream, PairingMessages.secret(secret))
            PairingMessages.status(readFrame(socket.inputStream))
        }.fold({ if (it == 200) PairResult.Paired else PairResult.Failed }, { PairResult.Failed })
    }

    override fun close() { runCatching { socket.close() } }
}

private class AtvSession(private val socket: SSLSocket) : TvSession {
    private val protocol = RemoteProtocol(model = Build.MODEL, vendor = Build.MANUFACTURER)
    private val _status = MutableStateFlow(RemoteStatus())
    override val status: StateFlow<RemoteStatus> = _status
    private val writeLock = Mutex()
    private val closed = CompletableDeferred<Unit>()

    fun start(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            try {
                socket.soTimeout = READ_TIMEOUT_MS   // the TV pings every few seconds; silence means the link is dead
                while (true) {
                    val replies = protocol.onMessage(readFrame(socket.inputStream))
                    _status.value = protocol.status
                    replies.forEach { send(it) }
                }
            } catch (_: Exception) {
                // closed, timed out or reset: the controller reconnects
            } finally {
                close()
            }
        }
    }

    override suspend fun send(frame: ByteArray) = withContext(Dispatchers.IO) {
        writeLock.withLock { writeFrame(socket.outputStream, frame) }
    }

    override suspend fun awaitClosed() = closed.await()

    override fun close() {
        runCatching { socket.close() }
        closed.complete(Unit)
    }

    private companion object { const val READ_TIMEOUT_MS = 15_000 }
}
