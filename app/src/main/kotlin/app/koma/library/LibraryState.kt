package app.koma.library

data class Manga(val id: Long, val title: String, val cover: String)

/** A tab of the library: most recently updated first. */
data class Shelf(val id: String, val title: String, val items: List<Manga>)

/** The stored library; [synced] once it has been read from MangaBaka at least once. */
data class LibraryData(val synced: Boolean, val shelves: List<Shelf>)

/** The library screen once signed in. */
sealed interface LibraryState {
    /** Never synced, and the last sync failed. */
    data object Failed : LibraryState

    /** Never synced. */
    data object Loading : LibraryState

    data class Shelves(val shelves: List<Shelf>, val refreshing: Boolean) : LibraryState
}

fun libraryState(data: LibraryData, syncing: Boolean, failed: Boolean): LibraryState =
    when {
        !data.synced && failed -> LibraryState.Failed
        !data.synced -> LibraryState.Loading
        else -> LibraryState.Shelves(data.shelves, refreshing = syncing)
    }

/** An empty shelf's title and what will show up there. */
fun emptyShelf(id: String): Pair<String, String> =
    if (id == "archived") {
        "Nothing archived" to "Your completed, paused and dropped titles show up here."
    } else {
        "Your library is empty" to "The titles you track on MangaBaka show up here."
    }
