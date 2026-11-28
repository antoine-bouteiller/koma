package app.koma.title

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.koma.AppContainer
import app.koma.data.Binding
import app.koma.data.Candidate
import app.koma.data.CoverColors
import app.koma.data.ListStatus
import app.koma.data.MediaDetails
import app.koma.data.MediaRef
import app.koma.data.failed
import app.koma.ui.theme.seedColor
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private data class Save(val saving: Change? = null, val failed: Change? = null)

private data class Screen(
    val load: Load,
    val save: Save,
    val chapters: Chapters?,
    val refreshing: Boolean,
)

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
    private val hasSources = container.sources.count > 0
    private val reading = MutableStateFlow<Chapters?>(if (hasSources) Chapters.Loading else null)
    private val refreshing = MutableStateFlow(false)
    private var readingJob: Job? = null
    private var details: MediaDetails? = null
    /** The candidates [Chapters.Picking] shows, by [pickKey]. */
    private var candidates = emptyMap<String, Candidate>()
    /** What [Chapters.Picking] returns to when the user keeps the current binding. */
    private var bound: Chapters.Bound? = null

    val backend: String = container.account.backendName

    val state: StateFlow<TitleState> =
        combine(
                container.media.media(ref),
                container.library.entry(ref),
                container.media.details(ref),
                combine(loaded, save, reading, refreshing, ::Screen),
            ) { media, entry, details, (load, s, chapters, pulled) ->
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
                    .copy(
                        chapters = readChapters(chapters, entry?.progress),
                        refreshing = pulled,
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
                    )
                    .copy(chapters = reading.value),
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
        if (hasSources) reading.value = Chapters.Loading
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
                    this@TitleViewModel.details = details
                }
                if (failed) {
                    loaded.value = Load(ready = true, failed = true)
                    reading.value = null
                } else listChapters(fetch = false, keep = false)
            }
    }

    /** Asks the source again for the chapters. */
    fun loadChapters() = listChapters(fetch = true, keep = false)

    /** Asks again for the chapters, or for the details when they failed, keeping what shows. */
    fun refresh() {
        if (details == null) load() else listChapters(fetch = true, keep = true)
    }

    /**
     * Binds the title to a source manga, unless one is stored, then lists its chapters: the stored
     * ones unless [fetch]. [keep] shows the current chapters until the new ones are in.
     */
    private fun listChapters(fetch: Boolean, keep: Boolean) {
        val details = details ?: return
        if (!hasSources) return
        read(keep) {
            val binding = container.sources.resolve(details)
            if (binding == null) picking(details, cancel = false) else bound(binding, fetch)
        }
    }

    /** Lists the source manga that could replace the bound one. */
    fun change() {
        val details = details ?: return
        read { picking(details, cancel = true) }
    }

    /** Keeps the bound source manga. */
    fun keep() {
        bound?.let { reading.value = it }
    }

    /** Binds the title to the candidate [key] names and lists its chapters. */
    fun pick(key: String) {
        val candidate = candidates[key] ?: return
        read {
            container.sources.bind(ref, candidate)
            bound(Binding(candidate.source, candidate.manga.url, candidate.manga.title))
        }
    }

    // One source request at a time per screen.
    private fun read(keep: Boolean = false, work: suspend () -> Chapters) {
        if (readingJob?.isActive == true) return
        if (keep) refreshing.value = true else reading.value = Chapters.Loading
        readingJob =
            viewModelScope.launch(container.work) {
                reading.value =
                    try {
                        work()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        Chapters.Failed
                    } finally {
                        refreshing.value = false
                    }
            }
    }

    private suspend fun picking(details: MediaDetails, cancel: Boolean): Chapters {
        val found = container.sources.candidates(details)
        candidates = found.associateBy(::pickKey)
        return pickingChapters(found, cancel)
    }

    private suspend fun bound(binding: Binding, fetch: Boolean = false): Chapters {
        val details = checkNotNull(details)
        val chapters =
            container.sources.chapters(binding, refresh = fetch)
                ?: return picking(details, cancel = false)
        return boundChapters(chapters, changeable = container.sources.count > 1).also { bound = it }
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
