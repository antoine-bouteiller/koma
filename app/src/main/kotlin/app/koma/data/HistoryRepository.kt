package app.koma.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// Continues the walk back through older events; it keeps working across launches.
private const val BACK_CURSOR = "history_back_cursor"
private const val COMPLETE = "history_complete"
private const val PAGE = 50

/**
 * A chapter read raises progress; a lowered or volume-only progress is not a read. Keep in sync
 * with `shelved` in LibraryEntry.sq.
 */
internal fun isRead(from: Double?, to: Double?): Boolean = to != null && to > (from ?: 0.0)

/** A read of [media], from chapter [from] to [to], at [at] (epoch ms). */
data class Read(val id: String, val media: Media, val at: Long, val from: Double?, val to: Double)

/** The stored history: [synced] once its first page is in, [hasNext] while older pages remain. */
data class History(val synced: Boolean, val reads: List<Read>, val hasNext: Boolean)

private fun KomaDatabase.upsert(events: List<ReadEvent>) {
    for (e in events) {
        readEventQueries.upsert(e.ref.backend.name, e.id, e.ref.id, e.at, e.from, e.to)
    }
}

/** The signed-in person's reading history, read from the backend newest first. */
class HistoryRepository(
    private val store: Store,
    private val backend: Backend,
    private val media: MediaRepository,
) {
    private val syncing = Mutex()
    private val name = backend.id.name

    /** Reads newest first, without media the backend no longer answers. */
    val history: Flow<History> =
        store.watch({
            listOf(
                metaQueries.value(BACK_CURSOR),
                metaQueries.value(COMPLETE),
                readEventQueries.titled(name),
            )
        }) {
            val synced = metaQueries.value(BACK_CURSOR).executeAsOneOrNull() != null
            val hasNext = synced && metaQueries.value(COMPLETE).executeAsOneOrNull() == null
            val reads =
                readEventQueries.titled(name).executeAsList().mapNotNull {
                    if (!isRead(it.from_chapter, it.to_chapter)) return@mapNotNull null
                    Read(
                        it.event_id,
                        mediaOf(
                            it.backend,
                            it.id,
                            it.title,
                            it.cover,
                            it.kind,
                            it.score,
                            it.adult,
                            it.long_strip,
                        ),
                        it.at,
                        it.from_chapter,
                        it.to_chapter!!,
                    )
                }
            History(synced, reads, hasNext)
        }

    /** Whether the first page is stored. */
    fun synced(): Boolean = store.meta(BACK_CURSOR) != null

    /** Reads the events newer than the newest stored one, or the first page when none is stored. */
    suspend fun syncNewer() {
        val generation = store.generation
        syncing.withLock { syncNewer(generation) }
    }

    private suspend fun syncNewer(generation: Int) {
        if (generation != store.generation) return
        val started = store.meta(BACK_CURSOR) != null
        val newest = store.read { readEventQueries.newest(name).executeAsOne().newest }
        if (!started || newest == null) {
            if (started) store.write { metaQueries.remove(listOf(BACK_CURSOR, COMPLETE)) }
            readOlder(generation)
            return
        }
        var cursor: String? = null
        var more = true
        while (more) {
            val page = backend.readEvents(cursor, since = newest, limit = 250)
            media.cache(page.items.map { it.ref })
            if (!store.write(generation) { upsert(page.items) }) return
            cursor = page.cursor
            more = page.more
        }
    }

    /** Reads older pages until one adds a read or the history ends. */
    suspend fun loadOlder() {
        val generation = store.generation
        syncing.withLock { readOlder(generation) }
    }

    private suspend fun readOlder(generation: Int) {
        while (true) {
            if (store.meta(COMPLETE) != null) return
            val page = backend.readEvents(store.meta(BACK_CURSOR), since = null, limit = PAGE)
            media.cache(page.items.map { it.ref })
            val applied =
                store.write(generation) {
                    upsert(page.items)
                    metaQueries.put(BACK_CURSOR, page.cursor)
                    if (!page.more) metaQueries.put(COMPLETE, "1")
                }
            if (!applied || !page.more || page.items.any { isRead(it.from, it.to) }) return
        }
    }
}
