package home.brimley.tv

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.Socket
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import java.security.spec.RSAKeyGenParameterSpec
import java.util.Date
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509TrustManager
import javax.security.auth.x500.X500Principal

// The tablet's identity to the TV: one RSA key in AndroidKeyStore, whose
// self-signed certificate the keystore issues. The TV remembers it at pairing.
class TvIdentity {
    private val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun ensureKey() {
        if (ks.containsAlias(ALIAS)) return
        val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore")
        kpg.initialize(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_DECRYPT)
                .setAlgorithmParameterSpec(RSAKeyGenParameterSpec(2048, RSAKeyGenParameterSpec.F4))
                .setDigests(KeyProperties.DIGEST_NONE, KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA384, KeyProperties.DIGEST_SHA512)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1, KeyProperties.SIGNATURE_PADDING_RSA_PSS)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE, KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
                .setCertificateSubject(X500Principal("CN=Brimley Home"))
                .setCertificateSerialNumber(BigInteger.ONE)
                .setCertificateNotBefore(Date(0))
                .setCertificateNotAfter(Date(4_102_444_800_000))   // 2100
                .build()
        )
        kpg.generateKeyPair()
    }

    private fun certificate(): X509Certificate { ensureKey(); return ks.getCertificate(ALIAS) as X509Certificate }
    private fun privateKey(): PrivateKey { ensureKey(); return ks.getKey(ALIAS, null) as PrivateKey }
    fun publicKey(): RSAPublicKey = certificate().publicKey as RSAPublicKey

    // A connected, handshaken TLS socket. The TV's self-signed certificate is
    // accepted here; callers pin it by fingerprint (see AtvConnector).
    fun openSocket(host: String, port: Int): SSLSocket {
        val ctx = SSLContext.getInstance("TLS")
        ctx.init(arrayOf(ClientKeys()), arrayOf(AcceptTvCertificate), null)
        val s = ctx.socketFactory.createSocket() as SSLSocket
        try {
            s.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
            s.soTimeout = HANDSHAKE_TIMEOUT_MS   // a TV that goes quiet mid-handshake must not hang the loop
            s.startHandshake()
        } catch (e: Exception) {
            runCatching { s.close() }
            throw e
        }
        return s
    }

    fun fingerprint(socket: SSLSocket): String =
        MessageDigest.getInstance("SHA-256").digest(socket.session.peerCertificates[0].encoded).joinToString("") { "%02x".format(it) }

    private inner class ClientKeys : X509ExtendedKeyManager() {
        override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?) = arrayOf(ALIAS)
        override fun chooseClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, socket: Socket?) = ALIAS
        override fun chooseEngineClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, engine: SSLEngine?) = ALIAS
        override fun getServerAliases(keyType: String?, issuers: Array<out Principal>?): Array<String>? = null
        override fun chooseServerAlias(keyType: String?, issuers: Array<out Principal>?, socket: Socket?): String? = null
        override fun getCertificateChain(alias: String?) = arrayOf(certificate())
        override fun getPrivateKey(alias: String?) = privateKey()
    }

    // The TV's certificate is self-signed, so there is no chain to check;
    // trust comes from the fingerprint pinned at pairing.
    private object AcceptTvCertificate : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    private companion object {
        const val ALIAS = "brimley-tv"
        const val CONNECT_TIMEOUT_MS = 5_000
        const val HANDSHAKE_TIMEOUT_MS = 10_000
    }
}
