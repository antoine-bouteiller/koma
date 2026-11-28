package app.koma.library

import app.koma.data.Kind
import app.koma.data.Library
import app.koma.data.ListStatus
import app.koma.data.MediaRef

/** [unread] counts the chapters its title screen shows unread. */
data class Manga(val ref: MediaRef, val title: String, val cover: String, val unread: Int = 0)

/** A tab of the library, in [Library.items]' order. */
data class Shelf(val id: String, val title: String, val items: List<Manga>)

private class ShelfRule(val id: String, val title: String, val statuses: Set<ListStatus>)

private val SHELVES =
    listOf(
        ShelfRule(
            "reading",
            "Reading",
            setOf(ListStatus.Reading, ListStatus.Planning, ListStatus.Rereading),
        ),
        ShelfRule(
            "archived",
            "Archived",
            setOf(ListStatus.Completed, ListStatus.Paused, ListStatus.Dropped),
        ),
    )

/** Each shelf of [library], in its order, without novels. */
fun shelves(library: Library, unread: Map<MediaRef, Int>): List<Shelf> {
    val shown = library.items.filter { it.media.kind != Kind.Novel }
    return SHELVES.map { rule ->
        Shelf(
            rule.id,
            rule.title,
            shown
                .filter { it.status in rule.statuses }
                .map {
                    Manga(
                        it.media.ref,
                        it.media.title,
                        it.media.cover,
                        unread[it.media.ref] ?: 0,
                    )
                },
        )
    }
}

/** The library screen once signed in. */
sealed interface LibraryState {
    /** Never synced, and the last sync failed. */
    data object Failed : LibraryState

    /** Never synced. */
    data object Loading : LibraryState

    data class Shelves(val shelves: List<Shelf>, val refreshing: Boolean) : LibraryState
}

fun libraryState(
    library: Library,
    unread: Map<MediaRef, Int>,
    syncing: Boolean,
    failed: Boolean,
): LibraryState =
    when {
        !library.synced && failed -> LibraryState.Failed
        !library.synced -> LibraryState.Loading
        else -> LibraryState.Shelves(shelves(library, unread), refreshing = syncing)
    }

/** An empty shelf's title and what will show up there. */
fun emptyShelf(id: String, backend: String): Pair<String, String> =
    if (id == "archived") {
        "Nothing archived" to "Your completed, paused and dropped titles show up here."
    } else {
        "Your library is empty" to "The titles you track on $backend show up here."
    }
