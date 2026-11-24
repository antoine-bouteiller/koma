package app.koma.history

import app.koma.data.KomaDatabase
import app.koma.data.MangaBakaApi
import app.koma.data.ReadEvent
import app.koma.data.Store

// Continues the walk back through older events; it keeps working across launches.
private const val BACK_CURSOR = "history_back_cursor"
private const val COMPLETE = "history_complete"
private const val PAGE = 50

/** A chapter read raises progress; a lowered or volume-only progress is not a read. */
internal fun isRead(from: Double?, to: Double?): Boolean = to != null && to > (from ?: 0.0)

private fun KomaDatabase.upsert(events: List<ReadEvent>) {
    for (e in events) readEventQueries.upsert(e.id, e.seriesId, e.at, e.from, e.to)
}

class HistorySync(private val store: Store, private val api: MangaBakaApi) {
    /** Reads the events newer than the newest stored one, or the first page when none is stored. */
    suspend fun syncNewer() {
        val generation = store.generation
        val started = store.meta(BACK_CURSOR) != null
        val newest = store.read { readEventQueries.newest().executeAsOne().newest }
        if (!started || newest == null) {
            if (started) store.write { metaQueries.remove(listOf(BACK_CURSOR, COMPLETE)) }
            readOlder(generation)
            return
        }
        var cursor: String? = null
        var more = true
        while (more) {
            val page = api.progressEvents(cursor, lower = newest, limit = 250)
            store.cacheSeries(page.items.map { it.seriesId })
            if (!store.write(generation) { upsert(page.items) }) return
            cursor = page.cursor
            more = page.more
        }
    }

    /** Reads older pages until one adds a read or the history ends. */
    suspend fun loadOlder() = readOlder(store.generation)

    private suspend fun readOlder(generation: Int) {
        while (true) {
            if (store.meta(COMPLETE) != null) return
            val page = api.progressEvents(store.meta(BACK_CURSOR), lower = null, limit = PAGE)
            store.cacheSeries(page.items.map { it.seriesId })
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

/** A stored read of a series MangaBaka still answers. */
data class StoredRead(
    val id: Long,
    val seriesId: Long,
    val at: Long,
    val from: Double?,
    val to: Double,
    val title: String,
    val cover: String,
)

/** The stored history: [synced] once its first page is in, [hasNext] while older pages remain. */
data class HistoryData(val synced: Boolean, val reads: List<StoredRead>, val hasNext: Boolean)

/**
 * Reads newest first. Reads of a series MangaBaka no longer answers (merged or deleted) are left
 * out.
 */
fun KomaDatabase.readHistory(): HistoryData {
    val synced = metaQueries.value(BACK_CURSOR).executeAsOneOrNull() != null
    val hasNext = synced && metaQueries.value(COMPLETE).executeAsOneOrNull() == null
    val reads =
        readEventQueries.titled().executeAsList().mapNotNull {
            if (!isRead(it.from_chapter, it.to_chapter)) return@mapNotNull null
            StoredRead(
                it.id,
                it.series_id,
                it.at,
                it.from_chapter,
                it.to_chapter!!,
                it.title,
                it.cover,
            )
        }
    return HistoryData(synced, reads, hasNext)
}

/** The queries [readHistory] reads. */
fun KomaDatabase.historyQueries() =
    listOf(
        metaQueries.value(BACK_CURSOR),
        metaQueries.value(COMPLETE),
        readEventQueries.titled(),
    )
