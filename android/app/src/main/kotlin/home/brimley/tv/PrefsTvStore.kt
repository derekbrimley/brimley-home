package home.brimley.tv

import android.content.Context

// Where the paired TV lives and the fingerprint of its certificate.
class PrefsTvStore(context: Context) : TvStore {
    private val prefs = context.getSharedPreferences("tv", Context.MODE_PRIVATE)
    override var host: String?
        get() = prefs.getString("host", null)
        set(v) { prefs.edit().putString("host", v).apply() }
    override var serverCertSha256: String?
        get() = prefs.getString("pin", null)
        set(v) { prefs.edit().putString("pin", v).apply() }
    override fun clear() { prefs.edit().clear().apply() }
}
