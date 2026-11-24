package app.koma.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.koma.AppContainer
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SessionViewModel(private val container: AppContainer) : ViewModel() {
    private val busy = MutableStateFlow(false)
    private val result = MutableStateFlow<SignInResult?>(null)

    val session: StateFlow<Session> = container.auth.sessions

    val signInView: StateFlow<SignInView> =
        combine(busy, result, session, ::signInView)
            .stateIn(viewModelScope, SharingStarted.Eagerly, SignInView(false, session.value.error))

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
}
