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

    val backend: String = container.account.backendName

    val state: StateFlow<LibraryState> =
        combine(
                container.library.library,
                syncing,
                lastFailed,
                ::libraryState,
            )
            .stateIn(viewModelScope, SharingStarted.Eagerly, LibraryState.Loading)

    /**
     * Updates the library and its chapters when it first shows after a sign-in in this process, or
     * while it was never synced. Otherwise it waits for [refresh].
     */
    fun open() {
        val session = container.account.signIns
        if (openedFor == session) return
        openedFor = session
        update { session > 0 || !container.library.synced() }
    }

    fun refresh() = update { true }

    private fun update(wanted: () -> Boolean) {
        if (job?.isActive == true) return
        job =
            viewModelScope.launch(container.work) {
                if (!wanted()) return@launch
                syncing.value = true
                lastFailed.value = false
                // Newer reads reorder the shelves; without them, they keep their order.
                launch { failed { container.history.syncNewer() } }
                lastFailed.value = failed { container.updater.update() }
                syncing.value = false
            }
    }
}
