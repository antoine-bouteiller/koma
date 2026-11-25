package app.koma.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.koma.AppContainer
import app.koma.data.Session
import app.koma.data.SignInResult
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SessionViewModel(private val container: AppContainer) : ViewModel() {
    private val account = container.account
    private val busy = MutableStateFlow(false)
    private val result = MutableStateFlow<SignInResult?>(null)

    val backendName: String = account.backendName
    val session: StateFlow<Session> = account.sessions

    val signInView: StateFlow<SignInView> =
        combine(busy, result, session, ::signInView)
            .stateIn(viewModelScope, SharingStarted.Eagerly, SignInView(false, session.value.error))

    /** The signed-in person's user name, null until [loadUserName] gets it. */
    val userName: StateFlow<String?> = account.userName

    fun signIn() {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch(container.work) {
            try {
                result.value = account.signIn()
            } finally {
                busy.value = false
            }
        }
    }

    fun signOut() {
        viewModelScope.launch(container.work) {
            try {
                account.signOut()
            } catch (_: IOException) {
                // The session could not be deleted, so it stays signed in and Sign Out stays shown.
            }
            result.value = null
        }
    }

    fun loadUserName() {
        viewModelScope.launch(container.work) { account.loadUserName() }
    }
}
