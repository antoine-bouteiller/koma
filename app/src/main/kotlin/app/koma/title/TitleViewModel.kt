package app.koma.title

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.koma.AppContainer
import app.koma.data.ListStatus
import app.koma.data.MediaDetails
import app.koma.data.MediaRef
import app.koma.data.failed
import app.koma.ui.theme.seedColor
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    /** The ARGB the screen's colors are drawn from: the cover's, null until it loads or if grey. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val seed: StateFlow<Int?> =
        state
            .map { it.cover }
            .filter { it.isNotEmpty() }
            .distinctUntilChanged()
            .mapLatest(::coverSeed)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    init {
        load()
    }

    private suspend fun coverSeed(cover: String): Int? {
        val request =
            ImageRequest.Builder(container.context)
                .data(cover)
                .size(SEED_SAMPLE_SIZE)
                .allowHardware(false)
                .build()
        val bitmap = container.images.execute(request).image?.toBitmap() ?: return null
        return withContext(container.work) {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            seedColor(pixels)
        }
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

// Enough pixels to find a cover's colors, few enough to count them at once.
private const val SEED_SAMPLE_SIZE = 96
