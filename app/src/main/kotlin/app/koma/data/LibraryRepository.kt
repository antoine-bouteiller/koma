package app.koma.data

import java.io.IOException
import java.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val CURSOR = "library_cursor"

/**
 * A source's chapter number; through its decimal string, so chapter 10.1f is progress 10.1. Null
 * when the source gives none.
 */
fun chapterNumber(number: Float): Double? = number.takeIf { it >= 0f }?.toString()?.toDouble()

/** Whether [progress] covers chapter [number]; a chapter without a number never is read. */
fun isChapterRead(number: Double?, progress: Double?): Boolean =
    number != null && number <= (progress ?: 0.0)

data class LibraryItem(val media: Media, val status: ListStatus, val updatedAt: Long)

/**
 * [synced] once the library has been read from the backend; [items] by their newest stored read,
 * then those without one by their last update.
 */
data class Library(val synced: Boolean, val items: List<LibraryItem>)

private fun KomaDatabase.apply(changes: List<ListChange>) {
    for (change in changes) {
        when (change) {
            is ListChange.Removed ->
                libraryEntryQueries.remove(change.ref.backend.name, change.ref.id)
            is ListChange.Upsert ->
                with(change.entry) {
                    libraryEntryQueries.upsert(
                        ref.backend.name,
                        ref.id,
                        status.name,
                        progress,
                        score,
                        updatedAt,
                    )
                }
        }
    }
}

private fun ListChange.ref(): MediaRef =
    when (this) {
        is ListChange.Removed -> ref
        is ListChange.Upsert -> entry.ref
    }

/** The signed-in person's library, mirrored from the backend. */
class LibraryRepository(
    private val store: Store,
    private val backend: Backend,
    private val media: MediaRepository,
    private val clock: Clock,
) {
    private val syncing = Mutex()
    private val name = backend.id.name

    val library: Flow<Library> =
        store.watch({ listOf(metaQueries.value(CURSOR), libraryEntryQueries.shelved(name)) }) {
            val synced = metaQueries.value(CURSOR).executeAsOneOrNull() != null
            val items =
                libraryEntryQueries.shelved(name).executeAsList().mapNotNull { row ->
                    val status =
                        ListStatus.entries.find { it.name == row.status } ?: return@mapNotNull null
                    val media =
                        mediaOf(
                            row.backend,
                            row.id,
                            row.title,
                            row.cover,
                            row.kind,
                            row.score,
                            row.adult,
                            row.long_strip,
                        )
                    LibraryItem(media, status, row.updated_at)
                }
            Library(synced, items)
        }

    /**
     * How many chapters each library title's screen shows unread. A title without a stored chapter
     * list, or with none unread, is missing.
     */
    val unread: Flow<Map<MediaRef, Int>> =
        store.watch({ listOf(chapterQueries.shownInLibrary(name)) }) {
            chapterQueries
                .shownInLibrary(name)
                .executeAsList()
                .filterNot { isChapterRead(chapterNumber(it.number.toFloat()), it.progress) }
                .groupingBy { MediaRef(backend.id, it.media_id) }
                .eachCount()
        }

    /** Whether the library has been read from the backend. */
    fun synced(): Boolean = store.meta(CURSOR) != null

    /** Null when the stored library does not hold [ref]. */
    fun entry(ref: MediaRef): Flow<ListEntry?> =
        store.watch({ listOf(libraryEntryQueries.entry(ref.backend.name, ref.id)) }) {
            val row =
                libraryEntryQueries.entry(ref.backend.name, ref.id).executeAsOneOrNull()
                    ?: return@watch null
            val status = ListStatus.entries.find { it.name == row.status } ?: return@watch null
            ListEntry(ref, status, row.progress, row.score, row.updated_at)
        }

    /** Brings the stored library up to date: a full read the first time, then only what changed. */
    suspend fun sync() {
        // Taken before waiting, so a sync queued behind a sign-out drops what it fetches.
        val generation = store.generation
        syncing.withLock { sync(generation) }
    }

    private suspend fun sync(generation: Int) {
        if (generation != store.generation) return
        var cursor = store.meta(CURSOR)
        if (cursor != null) {
            try {
                // Each page is applied with its cursor, so an interrupted sync resumes where it
                // stopped.
                var more = true
                while (more) {
                    val page = backend.changes(cursor)
                    media.cache(page.items.map { it.ref() })
                    val applied =
                        store.write(generation) {
                            apply(page.items)
                            metaQueries.put(CURSOR, page.cursor)
                        }
                    if (!applied) return
                    cursor = page.cursor
                    more = page.more
                }
                return
            } catch (_: ExpiredCursorException) {}
        }
        // A full read replaces the stored library only once its last page is in, so entries removed
        // meanwhile go.
        val changes = mutableListOf<ListChange>()
        var next: String? = null
        var more = true
        while (more) {
            val page = backend.changes(next)
            changes += page.items
            next = page.cursor
            more = page.more
        }
        media.cache(changes.map { it.ref() })
        store.write(generation) {
            libraryEntryQueries.clear()
            apply(changes)
            metaQueries.put(CURSOR, next ?: "")
        }
    }

    /**
     * Files [ref] under [status] on the backend, then in the stored library. A refused write syncs
     * the library, so a retry adds or updates as the backend expects.
     */
    suspend fun setStatus(ref: MediaRef, status: ListStatus) {
        val generation = store.generation
        val stored = store.read { storedStatus(ref) }
        media.cache(listOf(ref))
        syncIfRefused {
            if (stored == null) backend.addEntry(ref, status) else backend.updateEntry(ref, status)
        }
        val now = clock.millis()
        store.write(generation) {
            if (stored == null) {
                libraryEntryQueries.upsert(ref.backend.name, ref.id, status.name, null, null, now)
            } else {
                libraryEntryQueries.setStatus(status.name, now, ref.backend.name, ref.id)
            }
        }
    }

    /**
     * Rates [ref], which the library holds, out of 5 on the backend, then in the stored library. A
     * [score] of 0 clears the rating. A refused write syncs the library.
     */
    suspend fun setScore(ref: MediaRef, score: Double) {
        val generation = store.generation
        syncIfRefused { backend.updateEntry(ref, score = score) }
        val now = clock.millis()
        store.write(generation) {
            libraryEntryQueries.setScore(score.takeIf { it > 0 }, now, ref.backend.name, ref.id)
        }
    }

    private suspend fun syncIfRefused(write: suspend () -> Unit) {
        try {
            write()
        } catch (e: IOException) {
            try {
                sync()
            } catch (_: IOException) {}
            throw e
        }
    }
}

private fun KomaDatabase.storedStatus(ref: MediaRef): ListStatus? {
    val status = libraryEntryQueries.status(ref.backend.name, ref.id).executeAsOneOrNull()
    return ListStatus.entries.find { it.name == status }
}
