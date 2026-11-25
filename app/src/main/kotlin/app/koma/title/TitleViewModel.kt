package app.koma.title

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.koma.AppContainer
import app.koma.data.CoverColors
import app.koma.data.ListStatus
import app.koma.data.MediaRef
import app.koma.data.failed
import app.koma.ui.theme.seedColor
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private data class Save(val saving: Change? = null, val failed: Change? = null)

private data class Load(
    val ready: Boolean = false,
    val failed: Boolean = false,
    val seed: Int? = null,
)

/** The title screen of [ref]. */
class TitleViewModel(private val container: AppContainer, private val ref: MediaRef) : ViewModel() {
    private val loaded = MutableStateFlow(Load())
    private val save = MutableStateFlow(Save())
    private var loading: Job? = null

    val backend: String = container.account.backendName

    val state: StateFlow<TitleState> =
        combine(
                container.media.media(ref),
                container.library.entry(ref),
                container.media.details(ref),
                loaded,
                save,
            ) { media, entry, details, load, s ->
                titleState(
                    backend,
                    media,
                    entry,
                    details,
                    load.failed,
                    s.saving,
                    s.failed,
                    load.ready,
                    load.seed,
                )
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
                    colorsReady = false,
                    seed = null,
                ),
            )

    init {
        load()
    }

    private suspend fun coverColors(cover: String): CoverColors? {
        val request =
            ImageRequest.Builder(container.context)
                .data(cover)
                .size(SEED_SAMPLE_SIZE)
                .allowHardware(false)
                .build()
        val bitmap = container.images.execute(request).image?.toBitmap() ?: return null
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return CoverColors(cover, seedColor(pixels))
    }

    /** Fetches the title's details, unless they are stored. */
    fun load() {
        if (loading?.isActive == true) return
        loaded.value = Load()
        loading =
            viewModelScope.launch(container.work) {
                val failed = failed {
                    val details = container.media.loadDetails(ref)
                    val cover =
                        details.media.cover.ifEmpty {
                            container.media.media(ref).first()?.cover.orEmpty()
                        }
                    val cached = container.media.coverColors(ref)?.takeIf { it.cover == cover }
                    val colors =
                        cached
                            ?: if (cover.isEmpty()) CoverColors(cover, null) else coverColors(cover)
                    if (cached == null && colors != null)
                        container.media.cacheCoverColors(ref, colors)
                    loaded.value = Load(ready = true, seed = colors?.seed)
                }
                if (failed) loaded.value = Load(ready = true, failed = true)
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
