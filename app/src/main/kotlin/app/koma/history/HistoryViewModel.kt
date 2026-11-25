package app.koma.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.koma.AppContainer
import app.koma.data.History
import app.koma.data.failed
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private data class Status(
    val syncing: Boolean = false,
    val syncFailed: Boolean = false,
    val loadingOlder: Boolean = false,
    val olderFailed: Boolean = false,
)

private data class Tick(val now: Instant, val zone: ZoneId)

class HistoryViewModel(private val container: AppContainer) : ViewModel() {
    private val history = container.history
    private val status = MutableStateFlow(Status())
    private val tick = MutableStateFlow(Tick(container.clock.instant(), container.zone()))
    private var stored = History(synced = false, reads = emptyList(), hasNext = false)
    private var newer: Job? = null
    private var older: Job? = null

    val backend: String = container.account.backendName

    val state: StateFlow<HistoryState> =
        combine(
                history.history.onEach { stored = it },
                status,
                tick,
            ) { data, s, t ->
                historyState(
                    data,
                    s.syncing,
                    s.syncFailed,
                    s.loadingOlder,
                    s.olderFailed,
                    t.now,
                    t.zone,
                    Locale.getDefault(),
                )
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, HistoryState.Loading)

    init {
        viewModelScope.launch {
            while (true) {
                delay(60_000)
                tick.value = Tick(container.clock.instant(), container.zone())
            }
        }
    }

    /** Reads the first page when none is stored. Newer events wait for [sync]. */
    fun open() {
        if (newer?.isActive == true) return
        newer = viewModelScope.launch(container.work) { if (!history.synced()) syncNewer() }
    }

    /** Asks for the events since the newest stored one. */
    fun sync() {
        if (newer?.isActive == true) return
        newer = viewModelScope.launch(container.work) { syncNewer() }
    }

    private suspend fun syncNewer() {
        status.update { it.copy(syncing = true, syncFailed = false) }
        val failed = failed { history.syncNewer() }
        status.update { it.copy(syncing = false, syncFailed = failed) }
    }

    /** Asks for older events while some remain and nothing else is loading. */
    fun loadOlder() {
        if (!stored.hasNext || newer?.isActive == true || older?.isActive == true) return
        status.update { it.copy(loadingOlder = true, olderFailed = false) }
        older =
            viewModelScope.launch(container.work) {
                val failed = failed { history.loadOlder() }
                status.update { it.copy(loadingOlder = false, olderFailed = failed) }
            }
    }
}
