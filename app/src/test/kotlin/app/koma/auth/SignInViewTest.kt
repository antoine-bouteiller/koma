package app.koma.auth

import org.junit.Assert.assertEquals
import org.junit.Test

class SignInViewTest {
    private val signedOut = Session(signedIn = false, error = "")

    @Test
    fun `a sign-in under way shows no error`() {
        assertEquals(
            SignInView(busy = true, error = ""),
            signInView(true, SignInResult(false, "cancelled"), Session(false, "ended")),
        )
    }

    @Test
    fun `a failed sign-in says why`() {
        assertEquals(
            "Sign-in was cancelled.",
            signInView(false, SignInResult(false, "cancelled"), signedOut).error,
        )
        assertEquals(
            "Sign-in was declined.",
            signInView(false, SignInResult(false, "access_denied"), signedOut).error,
        )
        assertEquals(
            "Sign-in failed (invalid_client).",
            signInView(false, SignInResult(false, "invalid_client"), signedOut).error,
        )
    }

    @Test
    fun `otherwise the session's error shows`() {
        assertEquals("ended", signInView(false, null, Session(false, "ended")).error)
        assertEquals(
            "ended",
            signInView(false, SignInResult(true, ""), Session(false, "ended")).error,
        )
    }
}
