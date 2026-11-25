package app.koma.data

import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The signed-in person's session and account. */
class AccountRepository(private val store: Store, private val backend: Backend) {
    private val name = MutableStateFlow<String?>(null)
    @Volatile private var loadingName = false

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
                name.value = null
            }
        }

    /** Forgets the person's library and history, then ends the session. */
    suspend fun signOut() {
        store.clearUserData()
        name.value = null
        backend.signOut()
    }

    /** Asks once per session; a failure leaves the name unknown. */
    suspend fun loadUserName() {
        if (loadingName || name.value != null || !sessions.value.signedIn) return
        loadingName = true
        val session = signIns
        try {
            val found = backend.userName()
            // A reply for a session that ended meanwhile is dropped.
            if (session == signIns && sessions.value.signedIn) name.value = found
        } catch (_: IOException) {} finally {
            loadingName = false
        }
    }
}
