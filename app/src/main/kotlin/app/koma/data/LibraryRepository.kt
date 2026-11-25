package app.koma.data

import java.io.IOException
import java.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val CURSOR = "library_cursor"

data class LibraryItem(val media: Media, val status: ListStatus, val updatedAt: Long)

/**
 * [synced] once the library has been read from the backend; [items] most recently updated first.
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

    /** Null when the stored library does not hold [ref]. */
    fun status(ref: MediaRef): Flow<ListStatus?> =
        store.watch({ listOf(libraryEntryQueries.status(ref.backend.name, ref.id)) }) {
            storedStatus(ref)
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
        try {
            if (stored == null) backend.addEntry(ref, status) else backend.updateEntry(ref, status)
        } catch (e: IOException) {
            try {
                sync()
            } catch (_: IOException) {}
            throw e
        }
        val now = clock.millis()
        store.write(generation) {
            if (stored == null) {
                libraryEntryQueries.upsert(ref.backend.name, ref.id, status.name, null, now)
            } else {
                libraryEntryQueries.setStatus(status.name, now, ref.backend.name, ref.id)
            }
        }
    }
}

private fun KomaDatabase.storedStatus(ref: MediaRef): ListStatus? {
    val status = libraryEntryQueries.status(ref.backend.name, ref.id).executeAsOneOrNull()
    return ListStatus.entries.find { it.name == status }
}
