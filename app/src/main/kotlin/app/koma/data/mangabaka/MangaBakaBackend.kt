package app.koma.data.mangabaka

import app.koma.AuthBrowser
import app.koma.SecretStore
import app.koma.data.Backend
import app.koma.data.BackendId
import app.koma.data.CursorPage
import app.koma.data.ExpiredCursorException
import app.koma.data.HttpException
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
import app.koma.data.getText
import app.koma.data.json
import java.io.IOException
import java.net.URLEncoder
import java.time.Clock
import java.time.DateTimeException
import java.time.Instant
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient

private const val API = "https://api.mangabaka.org"
private const val BATCH_SIZE = 50

@Serializable
private class Title(
    val language: String,
    val title: String,
    @SerialName("is_primary") val primary: Boolean = true,
)

@Serializable
private class Cover(val x350: String? = null, val x250: String? = null, val raw: String? = null)

@Serializable
private class SeriesReply(
    val id: Long,
    val type: String = "",
    val titles: List<Title>? = null,
    val cover: Cover = Cover(),
    val rating: Double? = null,
    @SerialName("content_rating") val contentRating: String? = null,
)

@Serializable private class Batch(val data: List<SeriesReply>)

@Serializable private class Published(@SerialName("start_date") val startDate: String? = null)

@Serializable
private class RawTag(
    val name: String,
    @SerialName("is_genre") val genre: Boolean = false,
    val weight: String = "unweighted",
    @SerialName("is_spoiler") val spoiler: Boolean? = null,
)

@Serializable
private class FullSeries(
    val id: Long,
    val type: String = "",
    val titles: List<Title>? = null,
    val cover: Cover = Cover(),
    val authors: List<String>? = null,
    val status: String = "unknown",
    val published: Published = Published(),
    val rating: Double? = null,
    @SerialName("content_rating") val contentRating: String? = null,
    val description: String? = null,
    val tags: List<RawTag>? = null,
)

@Serializable private class OneSeries(val data: FullSeries)

@Serializable private class Pagination(val cursor: String = "", val more: Boolean = false)

@Serializable
private class RawChange(val type: String, val action: String, val current: JsonElement)

@Serializable
private class Entry(
    @SerialName("series_id") val seriesId: Long,
    val state: String = "",
    @SerialName("progress_chapter") val progress: Double? = null,
    @SerialName("updated_at") val updatedAt: String = "",
)

@Serializable private class Changes(val data: List<RawChange>, val pagination: Pagination)

@Serializable private class Progress(@SerialName("progress_chapter") val chapter: Double? = null)

@Serializable
private class RawEvent(
    val id: Long,
    @SerialName("series_id") val seriesId: Long,
    @SerialName("created_at") val createdAt: String? = null,
    val old: Progress = Progress(),
    val new: Progress = Progress(),
)

@Serializable private class Events(val data: List<RawEvent>, val pagination: Pagination)

@Serializable
private class Profile(
    @SerialName("preferred_username") val username: String? = null,
    val nickname: String? = null,
)

@Serializable private class ProfileReply(val data: Profile)

private val STATES =
    ListStatus.entries.associateBy {
        if (it == ListStatus.Planning) "plan_to_read" else it.name.lowercase()
    }

/** MangaBaka rates out of 100. Halving first keeps whole ratings exact before rounding. */
private fun outOfFive(rating: Double): Double = (rating / 2).roundToInt() / 10.0

private val RANKS = mapOf("core" to 100, "defining" to 75, "recurrent" to 50, "incidental" to 25)

private fun stateOf(status: ListStatus): String = STATES.entries.first { it.value == status }.key

private fun kindOf(type: String): Kind =
    when (type) {
        "manga" -> Kind.Manga
        "manhwa" -> Kind.Manhwa
        "manhua" -> Kind.Manhua
        "oel" -> Kind.Oel
        "novel" -> Kind.Novel
        else -> Kind.Other
    }

private fun publicationOf(status: String): Publication =
    Publication.entries.find { it.name.lowercase() == status } ?: Publication.Unknown

private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

// The full schema also lists alternate titles, which the batch leaves out.
private fun titleOf(titles: List<Title>?): String {
    val primary = titles.orEmpty().filter { it.primary }
    return (primary.find { it.language == "en" } ?: primary.firstOrNull())?.title ?: ""
}

private fun coverOf(cover: Cover): String = cover.x350 ?: cover.x250 ?: cover.raw ?: ""

private fun adult(contentRating: String?): Boolean =
    contentRating == "erotica" || contentRating == "pornographic"

/** Reports a reply that cannot be read as the [IOException] every [Backend] member throws. */
private inline fun <T> readable(read: () -> T): T =
    try {
        read()
    } catch (e: IllegalArgumentException) {
        throw IOException("Unreadable reply.", e)
    } catch (e: DateTimeException) {
        throw IOException("Unreadable reply.", e)
    }

/** MangaBaka, signed in through [MangaBakaAuth]. */
class MangaBakaBackend(
    private val http: OkHttpClient,
    secrets: SecretStore,
    browser: AuthBrowser,
    clock: Clock,
) : Backend {
    private val auth = MangaBakaAuth(http, secrets, browser, clock)

    override val id = BackendId.MangaBaka
    override val name = "MangaBaka"
    override val sessions: StateFlow<Session> = auth.sessions

    override suspend fun signIn(): SignInResult = auth.signIn()

    override suspend fun signOut() = auth.signOut()

    override suspend fun userName(): String = readable {
        val profile = auth.get("$API/v1/my/profile", ProfileReply.serializer()).data
        profile.username ?: profile.nickname ?: ""
    }

    override suspend fun changes(cursor: String?): CursorPage<ListChange> = readable {
        val query = if (cursor.isNullOrEmpty()) "" else "&cursor=${encode(cursor)}"
        val body =
            try {
                auth.get("$API/v2/my/library/changes?limit=1000$query", Changes.serializer())
            } catch (e: HttpException) {
                if (e.status == 400 && query.isNotEmpty()) throw ExpiredCursorException()
                throw e
            }
        // Lists, unknown item kinds and unknown states are skipped, as the feed asks.
        val changes =
            body.data.mapNotNull { change ->
                if (change.type != "entry" || change.action !in setOf("delete", "upsert")) {
                    return@mapNotNull null
                }
                val entry = json.decodeFromJsonElement(Entry.serializer(), change.current)
                val ref = ref(entry.seriesId)
                if (change.action == "delete") return@mapNotNull ListChange.Removed(ref)
                val status = STATES[entry.state] ?: return@mapNotNull null
                val updatedAt =
                    entry.updatedAt.takeIf { it.isNotEmpty() }?.let { Instant.parse(it) }
                ListChange.Upsert(
                    ListEntry(ref, status, entry.progress, null, updatedAt?.toEpochMilli() ?: 0)
                )
            }
        CursorPage(changes, body.pagination.cursor, body.pagination.more)
    }

    override suspend fun addEntry(
        ref: MediaRef,
        status: ListStatus,
        progress: Double?,
        score: Double?,
    ) = write("POST", ref, status, progress)

    override suspend fun updateEntry(
        ref: MediaRef,
        status: ListStatus?,
        progress: Double?,
        score: Double?,
    ) = write("PATCH", ref, status, progress)

    private suspend fun write(
        method: String,
        ref: MediaRef,
        status: ListStatus?,
        progress: Double?,
    ) = readable {
        val body = buildJsonObject {
            if (status != null) put("state", stateOf(status))
            if (progress != null) put("progress_chapter", progress)
        }
        auth.send(method, "$API/v1/my/library/${seriesId(ref)}", body.toString())
    }

    override suspend fun readEvents(
        cursor: String?,
        since: Long?,
        limit: Int,
    ): CursorPage<ReadEvent> = readable {
        var query = "event_type=progress_update"
        if (since != null) query += "&created_at_lower=${Instant.ofEpochSecond(since / 1000)}"
        query += "&limit=$limit"
        if (!cursor.isNullOrEmpty()) query += "&cursor=${encode(cursor)}"
        val body = auth.get("$API/v2/my/library/events?$query", Events.serializer())
        // Undated events are dropped: they cannot be placed on a day.
        val events =
            body.data.mapNotNull { e ->
                val at = e.createdAt ?: return@mapNotNull null
                ReadEvent(
                    e.id.toString(),
                    ref(e.seriesId),
                    Instant.parse(at).toEpochMilli(),
                    e.old.chapter,
                    e.new.chapter,
                )
            }
        CursorPage(events, body.pagination.cursor, body.pagination.more)
    }

    /** Unauthenticated, so MangaBaka's CDN can answer. Merged or deleted ids are missing. */
    override suspend fun media(refs: List<MediaRef>): List<Media> = readable {
        val ids = refs.filter { it.backend == id }.mapNotNull { it.id.toLongOrNull() }.distinct()
        ids.chunked(BATCH_SIZE).flatMap { chunk ->
            val body = http.getText("$API/v2/series/batch?" + chunk.joinToString("&") { "id=$it" })
            json.decodeFromString(Batch.serializer(), body).data.map {
                Media(
                    ref = ref(it.id),
                    title = titleOf(it.titles),
                    cover = coverOf(it.cover),
                    kind = kindOf(it.type),
                    score = it.rating?.let(::outOfFive),
                    adult = adult(it.contentRating),
                    longStrip = false,
                )
            }
        }
    }

    /** Unauthenticated, so MangaBaka's CDN can answer. */
    override suspend fun details(ref: MediaRef): MediaDetails = readable {
        val s =
            json
                .decodeFromString(
                    OneSeries.serializer(),
                    http.getText("$API/v2/series/${seriesId(ref)}?schema=full"),
                )
                .data
        MediaDetails(
            media =
                Media(
                    ref = ref(s.id),
                    title = titleOf(s.titles),
                    cover = coverOf(s.cover),
                    kind = kindOf(s.type),
                    score = s.rating?.let(::outOfFive),
                    adult = adult(s.contentRating),
                    longStrip = false,
                ),
            titles = s.titles.orEmpty().map { it.title }.distinct(),
            authors = s.authors.orEmpty(),
            publication = publicationOf(s.status),
            startYear = s.published.startDate?.take(4)?.toIntOrNull(),
            chapters = null,
            description = s.description.orEmpty().trim(),
            // A series carries dozens of tags; only genres and core ones describe it.
            tags =
                s.tags
                    .orEmpty()
                    .filter { it.genre || it.weight == "core" }
                    .map { Tag(it.name, it.genre, it.spoiler == true, RANKS[it.weight]) },
        )
    }

    private fun ref(seriesId: Long) = MediaRef(id, seriesId.toString())

    private fun seriesId(ref: MediaRef): Long =
        ref.id.toLongOrNull()?.takeIf { ref.backend == id }
            ?: throw IOException("Not a MangaBaka series: $ref")
}
