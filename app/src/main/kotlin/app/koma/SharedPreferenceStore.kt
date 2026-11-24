package app.koma

import android.content.Context
import androidx.core.content.edit

class SharedPreferenceStore(context: Context) : PreferenceStore {
    private val prefs = context.getSharedPreferences("koma.preferences", Context.MODE_PRIVATE)

    override fun get(key: String): String? = prefs.getString(key, null)

    override fun set(key: String, value: String) {
        prefs.edit { putString(key, value) }
    }
}
