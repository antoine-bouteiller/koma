package app.koma.title

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.koma.AppContainer
import app.koma.data.ListStatus
import app.koma.data.MediaDetails
import app.koma.data.MediaRef
import app.koma.data.failed
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private data class Fetch(val details: MediaDetails? = null, val failed: Boolean = false)

private data class Save(val saving: Change? = null, val failed: Change? = null)

/** The title screen of [ref]. */
class TitleViewModel(private val container: AppContainer, private val ref: MediaRef) : ViewModel() {
    private val fetch = MutableStateFlow(Fetch())
    private val save = MutableStateFlow(Save())
    private var loading: Job? = null

    val backend: String = container.account.backendName

    val state: StateFlow<TitleState> =
        combine(
                container.media.media(ref),
                container.library.entry(ref),
                fetch,
                save,
            ) { media, entry, f, s ->
                titleState(backend, media, entry, f.details, f.failed, s.saving, s.failed)
            }
            // Stops watching the store once the screen is gone, as one model is kept per title.
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(),
                titleState(
                    backend,
                    null,
                    null,
                    null,
                    loadFailed = false,
                    saving = null,
                    saveFailed = null,
                ),
            )

    init {
        load()
    }

    fun load() {
        if (loading?.isActive == true || fetch.value.details != null) return
        fetch.value = Fetch()
        loading =
            viewModelScope.launch(container.work) {
                var details: MediaDetails? = null
                val failed = failed { details = container.media.details(ref) }
                fetch.value = Fetch(details, failed)
            }
    }

    fun setStatus(status: ListStatus) {
        if (status == state.value.status?.status) return
        save(Change.Status) { container.library.setStatus(ref, status) }
    }

    /** Rates the title out of 5; 0 clears the rating. */
    fun setRating(score: Double) {
        val rating = state.value.rating ?: return
        if (score == rating.score) return
        save(Change.Rating) { container.library.setScore(ref, score) }
    }

    private fun save(change: Change, write: suspend () -> Unit) {
        if (save.value.saving != null) return
        save.value = Save(saving = change)
        viewModelScope.launch(container.work) {
            val failed = failed { write() }
            save.value = Save(failed = change.takeIf { failed })
        }
    }
}
