package app.koma.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.koma.AppContainer
import app.koma.data.failed
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SessionViewModel(private val container: AppContainer) : ViewModel() {
    private val busy = MutableStateFlow(false)
    private val result = MutableStateFlow<SignInResult?>(null)
    private val name = MutableStateFlow<String?>(null)
    @Volatile private var loadingName = false

    val session: StateFlow<Session> = container.auth.sessions

    val signInView: StateFlow<SignInView> =
        combine(busy, result, session, ::signInView)
            .stateIn(viewModelScope, SharingStarted.Eagerly, SignInView(false, session.value.error))

    /** The signed-in person's MangaBaka user name, null until [loadUserName] gets it. */
    val userName: StateFlow<String?> = name.asStateFlow()

    init {
        viewModelScope.launch { session.collect { if (!it.signedIn) name.value = null } }
    }

    fun signIn() {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch(container.work) {
            try {
                result.value = container.auth.signIn()
            } finally {
                busy.value = false
            }
        }
    }

    fun signOut() {
        viewModelScope.launch(container.work) {
            container.store.clearUserData()
            try {
                container.auth.signOut()
            } catch (_: IOException) {
                // The session could not be deleted, so it stays signed in and Sign Out stays shown.
            }
            result.value = null
        }
    }

    /** Asks MangaBaka for the user name once per session; a failure leaves it unknown. */
    fun loadUserName() {
        if (loadingName || name.value != null || !session.value.signedIn) return
        loadingName = true
        val signIns = container.auth.signIns
        viewModelScope.launch(container.work) {
            var found = ""
            val failed = failed { found = container.api.userName() }
            // A reply for a session that ended meanwhile is dropped.
            if (!failed && signIns == container.auth.signIns && session.value.signedIn) {
                name.value = found
            }
            loadingName = false
        }
    }
}
