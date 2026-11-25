package app.koma.data

import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The signed-in person's session and account. */
class AccountRepository(private val store: Store, private val backend: Backend) {
    private val name = MutableStateFlow<String?>(null)
    @Volatile private var loadingName = false
    // Bumped at each sign-in and sign-out, so a name asked for an earlier session is dropped.
    @Volatile private var session = 0

    val backendName: String = backend.name
    val sessions: StateFlow<Session> = backend.sessions

    /** Successful sign-ins in this process, so a screen can tell one session from the next. */
    @Volatile
    var signIns = 0
        private set

    /** Null until loaded; empty when the account has none. */
    val userName: StateFlow<String?> = name.asStateFlow()

    suspend fun signIn(): SignInResult =
        backend.signIn().also {
            if (it.ok) {
                signIns++
                session++
                name.value = null
            }
        }

    /** Forgets the person's library and history, then ends the session. */
    suspend fun signOut() {
        store.clearUserData()
        session++
        name.value = null
        backend.signOut()
    }

    /** Asks once per session; a failure leaves the name unknown. */
    suspend fun loadUserName() {
        if (loadingName || name.value != null || !sessions.value.signedIn) return
        loadingName = true
        val asked = session
        try {
            val found = backend.userName()
            if (asked == session && sessions.value.signedIn) name.value = found
        } catch (_: IOException) {} finally {
            loadingName = false
        }
    }
}
