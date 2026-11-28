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
     * load is skipped.
     */
    suspend fun updateChapters(onTitle: suspend (ChapterUpdate) -> Unit = {}) {
        val generation = store.generation
        if (sources.count == 0) return
        val items = library.library.first().items.filter { it.media.kind != Kind.Novel }
        for ((index, item) in items.withIndex()) {
            if (generation != store.generation) return
            onTitle(ChapterUpdate(item.media.title, index + 1, items.size))
            try {
                update(item.media.ref, refresh = item.status in FOLLOWED)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}
        }
    }

    private suspend fun update(ref: MediaRef, refresh: Boolean) {
        val binding =
            sources.binding(ref).first() ?: sources.resolve(media.loadDetails(ref)) ?: return
        sources.chapters(binding, refresh)
    }
}
