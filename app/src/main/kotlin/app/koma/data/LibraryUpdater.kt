package app.koma.data

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.first

private val FOLLOWED = setOf(ListStatus.Reading, ListStatus.Rereading)

/** The library title a chapter update is on: the [position]th of [total], counted from 1. */
data class ChapterUpdate(val title: String, val position: Int, val total: Int)

/**
 * Brings the chapters of the stored library's titles up to date: a followed title's on every
 * update, any other's until some are stored. A title not bound yet is bound without a prompt.
 */
class LibraryUpdater(
    private val store: Store,
    private val library: LibraryRepository,
    private val media: MediaRepository,
    private val sources: SourceRepository,
) {
    /**
     * Announces each title to [onTitle] before loading its chapters. A title whose chapters fail to
     * load is skipped. Answers the titles no source bound, in library order.
     */
    suspend fun updateChapters(onTitle: suspend (ChapterUpdate) -> Unit = {}): List<String> {
        val generation = store.generation
        if (sources.count == 0) return emptyList()
        val items = library.library.first().items.filter { it.media.kind != Kind.Novel }
        val unmatched = mutableListOf<String>()
        for ((index, item) in items.withIndex()) {
            if (generation != store.generation) return emptyList()
            onTitle(ChapterUpdate(item.media.title, index + 1, items.size))
            try {
                if (!update(item.media.ref, refresh = item.status in FOLLOWED)) {
                    unmatched += item.media.title
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}
        }
        return unmatched
    }

    /** False when no source binds the title. */
    private suspend fun update(ref: MediaRef, refresh: Boolean): Boolean {
        val binding =
            sources.binding(ref).first() ?: sources.resolve(media.loadDetails(ref)) ?: return false
        sources.chapters(binding, refresh)
        return true
    }
}
