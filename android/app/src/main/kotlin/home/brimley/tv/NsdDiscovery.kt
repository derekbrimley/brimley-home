package home.brimley.tv

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

// Finds the Google TV by its remote service on the local network.
class NsdDiscovery(context: Context) : TvDiscovery {
    private val nsd = context.getSystemService(NsdManager::class.java)

    override suspend fun find(timeoutMs: Long): String? {
        var listener: NsdManager.DiscoveryListener? = null
        try {
            return withTimeoutOrNull(timeoutMs) {
                suspendCancellableCoroutine<String?> { cont ->
                    val l = object : NsdManager.DiscoveryListener {
                        override fun onServiceFound(info: NsdServiceInfo) {
                            nsd.resolveService(info, object : NsdManager.ResolveListener {
                                override fun onServiceResolved(resolved: NsdServiceInfo) {
                                    val host = resolved.host?.hostAddress
                                    if (host != null && cont.isActive) cont.resume(host)
                                }
                                override fun onResolveFailed(i: NsdServiceInfo, errorCode: Int) {}
                            })
                        }
                        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { if (cont.isActive) cont.resume(null) }
                        override fun onDiscoveryStarted(serviceType: String) {}
                        override fun onDiscoveryStopped(serviceType: String) {}
                        override fun onServiceLost(info: NsdServiceInfo) {}
                        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
                    }
                    listener = l
                    nsd.discoverServices(SERVICE, NsdManager.PROTOCOL_DNS_SD, l)
                }
            }
        } finally {
            listener?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        }
    }

    private companion object { const val SERVICE = "_androidtvremote2._tcp" }
}
