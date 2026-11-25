package app.koma.data

import java.io.IOException
import kotlinx.coroutines.flow.StateFlow

enum class BackendId {
    MangaBaka
}

data class MediaRef(val backend: BackendId, val id: String)

enum class Kind {
    Manga,
    Manhwa,
    Manhua,
    Oel,
    Novel,
    Other,
}

enum class ListStatus {
    Reading,
    Planning,
    Considering,
    Completed,
    Paused,
    Dropped,
    Rereading,
}

enum class Publication {
    Releasing,
    Completed,
    Hiatus,
    Cancelled,
    Upcoming,
    Unknown,
}

/** What a card, a shelf or a history row shows. */
data class Media(
    val ref: MediaRef,
    val title: String,
    val cover: String,
    val kind: Kind,
    /** The backend's rating, from 0 to 100. */
    val score: Int?,
    val adult: Boolean,
    /** False when the backend does not say. */
    val longStrip: Boolean,
)

/** What the title screen shows beyond [media]. */
data class MediaDetails(
    val media: Media,
    /** Every variant: native, romanized and secondary titles. */
    val titles: List<String>,
    val authors: List<String>,
    val publication: Publication,
    val startYear: Int?,
    /** The total, when the backend knows it. */
    val chapters: Double?,
    val description: String,
    val tags: List<Tag>,
)

/** [rank] runs from 0 to 100. */
data class Tag(val name: String, val genre: Boolean, val spoiler: Boolean, val rank: Int?)

data class ListEntry(
    val ref: MediaRef,
    val status: ListStatus,
    /** Chapters read; may be fractional. */
    val progress: Double?,
    /** The user's rating, from 0 to 100. */
    val score: Int?,
    /** Epoch ms. */
    val updatedAt: Long,
)

sealed interface ListChange {
    data class Upsert(val entry: ListEntry) : ListChange

    data class Removed(val ref: MediaRef) : ListChange
}

/** A progress change at [at], epoch ms. */
data class ReadEvent(
    val id: String,
    val ref: MediaRef,
    val at: Long,
    val from: Double?,
    val to: Double?,
)

data class CursorPage<T>(val items: List<T>, val cursor: String, val more: Boolean)

data class Session(val signedIn: Boolean, val error: String)

/** [error] is an OAuth error code, or "cancelled", "network", "state_mismatch"… */
data class SignInResult(val ok: Boolean, val error: String)

/** The person is not signed in, or their session ended. */
class SignedOutException : IOException("Not signed in.")

class ExpiredCursorException : IOException("Cursor refused.")

/**
 * The service that holds the account, the library and the reading history. Every member throws an
 * [IOException] for any failure, an unreadable reply included.
 */
interface Backend {
    val id: BackendId
    /** Shown in copy. */
    val name: String
    val sessions: StateFlow<Session>

    suspend fun signIn(): SignInResult

    suspend fun signOut()

    /** Empty when the account has none. */
    suspend fun userName(): String

    /**
     * One page of the library: all of it from a null [cursor], else what changed since. A refused
     * cursor throws [ExpiredCursorException].
     */
    suspend fun changes(cursor: String?): CursorPage<ListChange>

    /** Throws when the library already holds [ref]. */
    suspend fun addEntry(
        ref: MediaRef,
        status: ListStatus,
        progress: Double? = null,
        score: Int? = null,
    )

    /** Changes only the fields given; throws when the library does not hold [ref]. */
    suspend fun updateEntry(
        ref: MediaRef,
        status: ListStatus? = null,
        progress: Double? = null,
        score: Int? = null,
    )

    /**
     * Newest first. [since] keeps events at or after that instant (epoch ms); a cursor is valid
     * only with the filters that made it.
     */
    suspend fun readEvents(cursor: String?, since: Long?, limit: Int): CursorPage<ReadEvent>

    /** Any number of refs; a ref the backend no longer answers is missing from the result. */
    suspend fun media(refs: List<MediaRef>): List<Media>

    suspend fun details(ref: MediaRef): MediaDetails
}
