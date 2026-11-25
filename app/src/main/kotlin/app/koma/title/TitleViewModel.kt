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

private data class Save(val saving: Boolean = false, val failed: Boolean = false)

/** The title screen of [ref]. */
class TitleViewModel(private val container: AppContainer, private val ref: MediaRef) : ViewModel() {
    private val fetch = MutableStateFlow(Fetch())
    private val save = MutableStateFlow(Save())
    private var loading: Job? = null

    val state: StateFlow<TitleState> =
        combine(
                container.media.media(ref),
                container.library.status(ref),
                fetch,
                save,
            ) { media, status, f, s ->
                titleState(media, status, f.details, f.failed, s.saving, s.failed)
            }
            // Stops watching the store once the screen is gone, as one model is kept per title.
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(),
                titleState(
                    null,
                    null,
                    null,
                    loadFailed = false,
                    saving = false,
                    saveFailed = false,
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
        if (status == state.value.status?.status || save.value.saving) return
        save.value = Save(saving = true)
        viewModelScope.launch(container.work) {
            val failed = failed { container.library.setStatus(ref, status) }
            save.value = Save(failed = failed)
        }
    }
}
