package app.koma.updates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.koma.AppContainer
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private data class Tick(val now: Instant, val zone: ZoneId)

class UpdatesViewModel(private val container: AppContainer) : ViewModel() {
    private val tick = MutableStateFlow(Tick(container.clock.instant(), container.zone()))

    val state: StateFlow<UpdatesState> =
        combine(container.library.newChapters, tick) { found, t ->
                updatesState(found, t.now, t.zone, Locale.getDefault())
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, UpdatesState.Loading)

    init {
        viewModelScope.launch {
            while (true) {
                delay(60_000)
                tick.value = Tick(container.clock.instant(), container.zone())
            }
        }
    }
}
