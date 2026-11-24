package app.koma.library

import app.koma.data.EntryChange
import app.koma.data.HttpException
import app.koma.data.KomaDatabase
import app.koma.data.MangaBakaApi
import app.koma.data.Store

private const val CURSOR = "library_cursor"

private class ShelfRule(val id: String, val title: String, val states: Set<String>)

private val SHELVES =
    listOf(
        ShelfRule("reading", "Reading", setOf("reading", "plan_to_read", "rereading")),
        ShelfRule("archived", "Archived", setOf("completed", "paused", "dropped")),
    )

private fun KomaDatabase.apply(changes: List<EntryChange>) {
    for (change in changes) {
        when (change) {
            is EntryChange.Delete -> libraryEntryQueries.remove(change.seriesId)
            is EntryChange.Upsert ->
                libraryEntryQueries.upsert(change.seriesId, change.state, change.updatedAt)
        }
    }
}

class LibrarySync(private val store: Store, private val api: MangaBakaApi) {
    /** Brings the stored library up to date: a full read the first time, then only what changed. */
    suspend fun sync() {
        val generation = store.generation
        var cursor = store.meta(CURSOR)
        if (cursor != null) {
            try {
                // Each page is applied with its cursor, so an interrupted sync resumes where it
                // stopped.
                var more = true
                while (more) {
                    val page = api.libraryChanges(cursor)
                    store.cacheSeries(page.items.map { it.seriesId })
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
            } catch (e: HttpException) {
                if (e.status != 400) throw e
            }
        }
        // A full read replaces the stored library only once its last page is in, so entries removed
        // meanwhile go.
        val changes = mutableListOf<EntryChange>()
        var next: String? = null
        var more = true
        while (more) {
            val page = api.libraryChanges(next)
            changes += page.items
            next = page.cursor
            more = page.more
        }
        store.cacheSeries(changes.map { it.seriesId })
        store.write(generation) {
            libraryEntryQueries.clear()
            apply(changes)
            metaQueries.put(CURSOR, next ?: "")
        }
    }
}

/** Each shelf, most recently updated first, without novels. */
fun KomaDatabase.readLibrary(): LibraryData {
    val synced = metaQueries.value(CURSOR).executeAsOneOrNull() != null
    val rows = libraryEntryQueries.shelved().executeAsList()
    val shelves = SHELVES.map { rule ->
        Shelf(
            rule.id,
            rule.title,
            rows.filter { it.state in rule.states }.map { Manga(it.id, it.title, it.cover) },
        )
    }
    return LibraryData(synced, shelves)
}

/** The queries [readLibrary] reads. */
fun KomaDatabase.libraryQueries() = listOf(metaQueries.value(CURSOR), libraryEntryQueries.shelved())
