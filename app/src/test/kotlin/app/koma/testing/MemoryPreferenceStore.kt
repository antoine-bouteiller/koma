package app.koma.testing

import app.koma.PreferenceStore

class MemoryPreferenceStore : PreferenceStore {
    private val values = mutableMapOf<String, String>()

    override fun get(key: String): String? = values[key]

    override fun set(key: String, value: String) {
        values[key] = value
    }
}
