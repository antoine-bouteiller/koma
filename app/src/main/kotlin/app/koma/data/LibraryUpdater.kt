package app.koma.data

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val FOLLOWED = setOf(ListStatus.Reading, ListStatus.Rereading)

/** The library title a chapter update is on: the [position]th of [total], counted from 1. */
data class ChapterUpdate(val title: String, val position: Int, val total: Int)

/** The chapters an update found on [title], by their source's names, in its order. */
data class FoundChapters(val title: String, val chapters: List<String>)

/** What an update reports, in library order: the titles no source bound, and the chapters found. */
data class UpdateReport(val unmatched: List<String>, val found: List<FoundChapters>)

/**
 * Brings the chapters of the stored library's titles up to date: a followed title's on every
 * update, any other's until some are stored. A title not bound yet is bound without a prompt.
 */
class LibraryUpdater(
    private val store: Store,
    private val library: LibraryRepository,
    private val history: HistoryRepository,
    private val media: MediaRepository,
    private val sources: SourceRepository,
) {
    /** Syncs the library, and the newer reads beside it; answers whether the library synced. */
    suspend fun sync(): Boolean = coroutineScope {
        // Newer reads reorder the shelves; without them, they keep their order.
        launch { failed { history.syncNewer() } }
        !failed { library.sync() }
    }

    /** What a pull on the library runs: [sync], then [updateChapters] once the library synced. */
    suspend fun refresh(onTitle: suspend (ChapterUpdate) -> Unit = {}): UpdateReport =
        if (sync()) updateChapters(onTitle) else UpdateReport(emptyList(), emptyList())

    /**
     * Announces each title to [onTitle] before loading its chapters. A title whose chapters fail to
     * load is skipped. Found chapters of hidden scanlators, and outdated part chapters, go
     * unreported.
     */
    suspend fun updateChapters(onTitle: suspend (ChapterUpdate) -> Unit = {}): UpdateReport {
        val none = UpdateReport(emptyList(), emptyList())
        val generation = store.generation
        if (sources.count == 0) return none
        val items = library.library.first().items.filter { it.media.kind != Kind.Novel }
        val unmatched = mutableListOf<String>()
        val found = mutableListOf<FoundChapters>()
        for ((index, item) in items.withIndex()) {
            if (generation != store.generation) return none
            onTitle(ChapterUpdate(item.media.title, index + 1, items.size))
            try {
                val chapters = update(item.media.ref, refresh = item.status in FOLLOWED)
                when {
                    chapters == null -> unmatched += item.media.title
                    chapters.isNotEmpty() -> found += FoundChapters(item.media.title, chapters)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}
        }
        return UpdateReport(unmatched, found)
    }

    /** The names of the chapters found on the title; null when no source binds it. */
    private suspend fun update(ref: MediaRef, refresh: Boolean): List<String>? {
        val binding =
            sources.binding(ref).first() ?: sources.resolve(media.loadDetails(ref)) ?: return null
        val found = sources.newChapters(binding, refresh).orEmpty()
        if (found.isEmpty()) return emptyList()
        val excluded = sources.excludedScanlators(ref).first()
        val shown = withoutOutdatedParts(sources.chapters(binding).orEmpty()).map { it.url }.toSet()
        return found
            .filter { it.url in shown && (it.scanlator == null || it.scanlator !in excluded) }
            .map { it.name }
    }
}
