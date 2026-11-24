package app.koma.auth

import android.annotation.SuppressLint
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import app.koma.SecretStore
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val PROVIDER = "AndroidKeyStore"
private const val ALIAS = "koma.secrets"
private const val TRANSFORMATION = "AES/GCM/NoPadding"
private const val IV_SIZE = 12

/** Values encrypted by an AES-256-GCM key that never leaves the Android Keystore. */
// KTX's `edit {}` drops the result of `commit()`, which says whether the write reached disk.
@SuppressLint("UseKtx")
class KeystoreSecretStore(context: Context) : SecretStore {
    private val prefs = context.getSharedPreferences("koma.secrets", Context.MODE_PRIVATE)
    private val secretKey: SecretKey by lazy {
        val keyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (keyStore.getKey(ALIAS, null) as SecretKey?)
            ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
                .apply {
                    init(
                        KeyGenParameterSpec.Builder(
                                ALIAS,
                                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                            )
                            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                            .setKeySize(256)
                            .build()
                    )
                }
                .generateKey()
    }

    /** The value, or null when absent or no longer decryptable. */
    override fun get(key: String): String? {
        val stored = prefs.getString(key, null) ?: return null
        return try {
            val bytes = Base64.getDecoder().decode(stored)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, bytes, 0, IV_SIZE))
            String(cipher.doFinal(bytes, IV_SIZE, bytes.size - IV_SIZE), Charsets.UTF_8)
        } catch (_: GeneralSecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    override fun set(key: String, value: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val sealed = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        // Written before returning: a rotated refresh token must be on disk before it is relied on.
        val saved = prefs.edit().putString(key, Base64.getEncoder().encodeToString(sealed)).commit()
        if (!saved) throw IOException("$key could not be saved.")
    }

    override fun delete(key: String) {
        if (!prefs.edit().remove(key).commit()) throw IOException("$key could not be deleted.")
    }
}
