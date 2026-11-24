package app.koma.title

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.koma.AppContainer
import app.koma.data.SeriesDetails
import app.koma.data.failed
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private data class Fetch(val details: SeriesDetails? = null, val failed: Boolean = false)

private data class Save(val saving: Boolean = false, val failed: Boolean = false)

/** The title screen of series [id]. */
class TitleViewModel(private val container: AppContainer, private val id: Long) : ViewModel() {
    private val fetch = MutableStateFlow(Fetch())
    private val save = MutableStateFlow(Save())
    private var loading: Job? = null

    val state: StateFlow<TitleState> =
        combine(
                container.store.watch({ listOf(seriesQueries.withEntry(id)) }) {
                    seriesQueries.withEntry(id).executeAsOneOrNull()?.let {
                        StoredTitle(it.title, it.cover, it.state)
                    }
                },
                fetch,
                save,
            ) { stored, f, s ->
                titleState(stored, f.details, f.failed, s.saving, s.failed)
            }
            // Stops watching the store once the screen is gone, as one model is kept per title.
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(),
                titleState(null, null, loadFailed = false, saving = false, saveFailed = false),
            )

    init {
        load()
    }

    fun load() {
        if (loading?.isActive == true || fetch.value.details != null) return
        fetch.value = Fetch()
        loading =
            viewModelScope.launch(container.work) {
                var details: SeriesDetails? = null
                val failed = failed { details = container.api.series(id) }
                fetch.value = Fetch(details, failed)
            }
    }

    /** Files the series under [state] on MangaBaka, then in the stored library. */
    fun setStatus(state: String) {
        val current = this.state.value.status?.state
        if (state == current || save.value.saving) return
        save.value = Save(saving = true)
        val generation = container.store.generation
        viewModelScope.launch(container.work) {
            val failed = failed {
                container.api.saveEntry(id, state, exists = current != null)
                container.store.write(generation) {
                    libraryEntryQueries.upsert(id, state, container.clock.instant().toString())
                }
            }
            save.value = Save(failed = failed)
        }
    }
}
