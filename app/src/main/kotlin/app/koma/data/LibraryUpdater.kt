package app.koma.data

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.first

private val FOLLOWED = setOf(ListStatus.Reading, ListStatus.Rereading)

/**
 * Brings the library up to date, then the chapters of its titles: a followed title's on every
 * update, any other's until some are stored. A title not bound yet is bound without a prompt.
 */
class LibraryUpdater(
    private val store: Store,
    private val library: LibraryRepository,
    private val media: MediaRepository,
    private val sources: SourceRepository,
) {
    /** Throws when the library sync fails. A title whose chapters fail to load is skipped. */
    suspend fun update() {
        val generation = store.generation
        library.sync()
        if (sources.count == 0) return
        for (item in library.library.first().items) {
            if (generation != store.generation) return
            if (item.media.kind == Kind.Novel) continue
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
