package app.koma.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

internal object Pkce {
    private val random = SecureRandom()
    private val base64 = Base64.getUrlEncoder().withoutPadding()

    /** 32 random bytes, base64url: a code verifier, or a state. */
    fun random(): String = base64.encodeToString(ByteArray(32).also(random::nextBytes))

    /** The S256 code challenge of [verifier]. */
    fun challenge(verifier: String): String =
        base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        )
}
