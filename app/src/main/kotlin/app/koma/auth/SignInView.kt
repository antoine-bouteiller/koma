package app.koma.auth

/** What the sign-in prompt shows: whether a sign-in is under way, and the error to show. */
data class SignInView(val busy: Boolean, val error: String)

fun signInView(busy: Boolean, result: SignInResult?, session: Session): SignInView {
    if (busy) return SignInView(busy = true, error = "")
    val failure = result?.takeIf { !it.ok }?.error?.let(::signInMessage)
    return SignInView(busy = false, error = failure ?: session.error)
}

private fun signInMessage(code: String): String =
    when (code) {
        "cancelled" -> "Sign-in was cancelled."
        "access_denied" -> "Sign-in was declined."
        "network" -> "Sign-in could not finish. Try again."
        "storage" -> "Sign-in could not be saved. Try again."
        "state_mismatch" -> "Sign-in failed: the reply was for another sign-in."
        "issuer_mismatch" -> "Sign-in failed: the reply came from another server."
        else -> "Sign-in failed ($code)."
    }
