package app.koma.testing

import app.koma.SecretStore
import java.io.IOException

class MemorySecretStore : SecretStore {
    private val values = mutableMapOf<String, String>()

    override fun get(key: String): String? = values[key]

    /** Every write fails, as a full disk would. */
    var failWrites = false

    override fun set(key: String, value: String) {
        if (failWrites) throw IOException("$key could not be saved.")
        values[key] = value
    }

    override fun delete(key: String) {
        values.remove(key)
    }
}
