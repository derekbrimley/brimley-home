package home.brimley.data

import android.content.Context

// The crate someone picked for the Music card. Kept until someone picks another;
// unset means the server's default (HOME_CRATE).
interface CrateChoiceStore {
    var crateId: String?
}

class PrefsCrateChoice(context: Context) : CrateChoiceStore {
    private val prefs = context.getSharedPreferences("music", Context.MODE_PRIVATE)
    override var crateId: String?
        get() = prefs.getString("crate", null)
        set(v) { prefs.edit().putString("crate", v).apply() }
}
