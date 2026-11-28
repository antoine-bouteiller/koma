package app.koma.testing

import app.koma.data.Backend
import app.koma.data.BackendId
import app.koma.data.CursorPage
import app.koma.data.ExpiredCursorException
import app.koma.data.Kind
import app.koma.data.ListChange
import app.koma.data.ListEntry
import app.koma.data.ListStatus
import app.koma.data.Media
import app.koma.data.MediaDetails
import app.koma.data.MediaRef
import app.koma.data.Publication
import app.koma.data.ReadEvent
import app.koma.data.Session
import app.koma.data.SignInResult
import app.koma.data.Tag
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A [Backend] held in memory, whose ids and titles MangaBaka could not produce. It records each
 * call to the library and the history in [calls]. Its changes feed answers in one page.
 */
class FakeBackend(override val name: String = "Shelfie", signedIn: Boolean = true) : Backend {
    private val session = MutableStateFlow(Session(signedIn, ""))
    private val known = mutableMapOf<MediaRef, MediaDetails>()
    private val entries = linkedMapOf<MediaRef, ListEntry>()
    private val pending = mutableListOf<ListChange>()
    private var cursors = 0
    private val held = mutableMapOf<String, CompletableDeferred<Unit>>()

    override val id = BackendId.MangaBaka
    override val sessions: StateFlow<Session> = session.asStateFlow()

    val calls = CopyOnWriteArrayList<String>()
    var user = "shelf-reader"
    /** Newest first. */
    val reads = mutableListOf<ReadEvent>()
    /** Every cursor is refused, as from a backend without a changes feed. */
    var refuseCursors = false
    /** [media] fails, as when the backend is unreachable. */
    var failMedia = false

    /** Makes [member] wait until the answer completes. */
    fun hold(member: String): CompletableDeferred<Unit> =
        CompletableDeferred<Unit>().also { held[member] = it }

    private suspend fun waitFor(member: String) {
        held[member]?.await()
    }

    /** Media the backend answers. */
    fun add(
        id: String,
        title: String,
        score: Double? = null,
        kind: Kind = Kind.Manhwa,
        tags: List<Tag> = emptyList(),
    ): MediaRef {
        val ref = MediaRef(this.id, id)
        known[ref] =
            MediaDetails(
                Media(ref, title, "https://shelfie.test/$id.png", kind, score, false, false),
                titles = listOf(title),
                authors = emptyList(),
                publication = Publication.Unknown,
                startYear = null,
                chapters = null,
                description = "",
                tags = tags,
            )
        return ref
    }

    /** Files [ref] in the library, as an edit made elsewhere would. */
    fun file(ref: MediaRef, status: ListStatus, updatedAt: Long = 0, score: Double? = null) {
        val entry = ListEntry(ref, status, null, score, updatedAt)
        entries[ref] = entry
        pending += ListChange.Upsert(entry)
    }

    /** Removes [ref] from the library, as an edit made elsewhere would. */
    fun drop(ref: MediaRef) {
        entries.remove(ref)
        pending += ListChange.Removed(ref)
    }

    override suspend fun signIn(): SignInResult {
        session.value = Session(true, "")
        return SignInResult(true, "")
    }

    override suspend fun signOut() {
        waitFor("signOut")
        session.value = Session(false, "")
    }

    override suspend fun userName(): String {
        calls += "userName()"
        waitFor("userName")
        return user
    }

    override suspend fun changes(cursor: String?): CursorPage<ListChange> {
        calls += "changes($cursor)"
        waitFor("changes")
        if (cursor != null && refuseCursors) throw ExpiredCursorException()
        val items = if (cursor == null) entries.values.map { ListChange.Upsert(it) } else pending
        val page = CursorPage(items.toList(), "c${++cursors}", more = false)
        pending.clear()
        return page
    }

    override suspend fun addEntry(
        ref: MediaRef,
        status: ListStatus,
        progress: Double?,
        score: Double?,
    ) {
        calls += "addEntry(${ref.id}, $status)"
        if (ref in entries) throw IOException("${ref.id} is in the library.")
        file(ref, status)
    }

    override suspend fun updateEntry(
        ref: MediaRef,
        status: ListStatus?,
        progress: Double?,
        score: Double?,
    ) {
        calls += "updateEntry(${ref.id}, $status${score?.let { ", $it" }.orEmpty()})"
        val entry = entries[ref] ?: throw IOException("${ref.id} is not in the library.")
        val rated = if (score == null) entry.score else score.takeIf { it > 0 }
        file(ref, status ?: entry.status, score = rated)
    }

    override suspend fun readEvents(
        cursor: String?,
        since: Long?,
        limit: Int,
    ): CursorPage<ReadEvent> {
        calls += "readEvents($cursor, $since)"
        waitFor("readEvents")
        return CursorPage(reads.filter { since == null || it.at >= since }, "end", more = false)
    }

    override suspend fun media(refs: List<MediaRef>): List<Media> {
        if (failMedia) throw IOException("Unreachable.")
        return refs.mapNotNull { known[it]?.media }
    }

    val redirects = mutableMapOf<MediaRef, MediaRef>()

    override suspend fun replacements(refs: List<MediaRef>): Map<MediaRef, MediaRef> =
        redirects.filterKeys {
            it in refs
        }

    override suspend fun details(ref: MediaRef): MediaDetails {
        calls += "details(${ref.id})"
        waitFor("details")
        return known[ref] ?: throw IOException("${ref.id} is unknown.")
    }
}
