package app.koma.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.koma.AppContainer
import app.koma.data.failed
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(private val container: AppContainer) : ViewModel() {
    private val syncing = MutableStateFlow(false)
    private val lastFailed = MutableStateFlow(false)
    private var job: Job? = null
    private var openedFor: Int? = null

    val state: StateFlow<LibraryState> =
        combine(
                container.library.library,
                syncing,
                lastFailed,
                ::libraryState,
            )
            .stateIn(viewModelScope, SharingStarted.Eagerly, LibraryState.Loading)

    /** Syncs once per sign-in, when the library first shows. */
    fun open() {
        val session = container.account.signIns
        if (openedFor == session) return
        openedFor = session
        sync()
    }

    fun sync() {
        if (job?.isActive == true) return
        syncing.value = true
        lastFailed.value = false
        job =
            viewModelScope.launch(container.work) {
                lastFailed.value = failed { container.library.sync() }
                syncing.value = false
            }
    }
}
