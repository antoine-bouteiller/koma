package app.koma.data

import java.net.URLEncoder
import java.time.Instant
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import okhttp3.OkHttpClient

private const val API = "https://api.mangabaka.org"

/** Makes requests with the signed-in person's access token. */
interface Authorized {
    suspend fun <T> get(url: String, body: DeserializationStrategy<T>): T
}

data class SeriesInfo(val id: Long, val title: String, val cover: String, val type: String)

sealed interface EntryChange {
    val seriesId: Long

    data class Upsert(override val seriesId: Long, val state: String, val updatedAt: String) :
        EntryChange

    data class Delete(override val seriesId: Long) : EntryChange
}

data class ReadEvent(
    val id: Long,
    val seriesId: Long,
    val at: Long,
    val from: Double?,
    val to: Double?,
)

data class Page<T>(val items: List<T>, val cursor: String, val more: Boolean)

@Serializable private class Title(val language: String, val title: String)

@Serializable
private class Cover(val x350: String? = null, val x250: String? = null, val raw: String? = null)

@Serializable
private class SeriesReply(
    val id: Long,
    val type: String = "",
    val titles: List<Title>? = null,
    val cover: Cover = Cover(),
)

@Serializable private class Batch(val data: List<SeriesReply>)

@Serializable private class Pagination(val cursor: String = "", val more: Boolean = false)

@Serializable
private class RawChange(val type: String, val action: String, val current: JsonElement)

@Serializable
private class Entry(
    @SerialName("series_id") val seriesId: Long,
    val state: String = "",
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

private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

private fun titleOf(titles: List<Title>?): String =
    (titles?.find { it.language == "en" } ?: titles?.firstOrNull())?.title ?: ""

class MangaBakaApi(private val http: OkHttpClient, private val auth: Authorized) {
    /**
     * Up to 50 series by id, unauthenticated so MangaBaka's CDN can answer. Merged or deleted ids
     * are missing.
     */
    fun seriesBatch(ids: List<Long>): List<SeriesInfo> {
        val body = http.getText("$API/v2/series/batch?" + ids.joinToString("&") { "id=$it" })
        return json.decodeFromString(Batch.serializer(), body).data.map {
            SeriesInfo(
                id = it.id,
                title = titleOf(it.titles),
                cover = it.cover.x350 ?: it.cover.x250 ?: it.cover.raw ?: "",
                type = it.type,
            )
        }
    }

    /**
     * One page of the library changes feed: a full read without [cursor], else what changed since.
     * A refused cursor throws an [HttpException] with status 400.
     */
    suspend fun libraryChanges(cursor: String?): Page<EntryChange> {
        val query = if (cursor.isNullOrEmpty()) "" else "&cursor=${encode(cursor)}"
        val body = auth.get("$API/v2/my/library/changes?limit=1000$query", Changes.serializer())
        // Lists and unknown item kinds are skipped, as the feed asks.
        val changes =
            body.data.mapNotNull { change ->
                if (change.type != "entry" || change.action !in setOf("delete", "upsert")) {
                    return@mapNotNull null
                }
                val entry = json.decodeFromJsonElement(Entry.serializer(), change.current)
                if (change.action == "delete") EntryChange.Delete(entry.seriesId)
                else EntryChange.Upsert(entry.seriesId, entry.state, entry.updatedAt)
            }
        return Page(changes, body.pagination.cursor, body.pagination.more)
    }

    /**
     * One page of progress events, newest first. [lower] keeps events at or after that instant
     * (epoch ms, rounded down to the second). A cursor must be sent with the filters that made it.
     * Undated events are dropped: they cannot be placed on a day.
     */
    suspend fun progressEvents(cursor: String?, lower: Long?, limit: Int): Page<ReadEvent> {
        var query = "event_type=progress_update"
        if (lower != null) query += "&created_at_lower=${Instant.ofEpochSecond(lower / 1000)}"
        query += "&limit=$limit"
        if (!cursor.isNullOrEmpty()) query += "&cursor=${encode(cursor)}"
        val body = auth.get("$API/v2/my/library/events?$query", Events.serializer())
        val events =
            body.data.mapNotNull { e ->
                val at = e.createdAt ?: return@mapNotNull null
                ReadEvent(
                    e.id,
                    e.seriesId,
                    Instant.parse(at).toEpochMilli(),
                    e.old.chapter,
                    e.new.chapter,
                )
            }
        return Page(events, body.pagination.cursor, body.pagination.more)
    }

    /** The signed-in person's MangaBaka user name, empty when they have none. */
    suspend fun userName(): String {
        val profile = auth.get("$API/v1/my/profile", ProfileReply.serializer()).data
        return profile.username ?: profile.nickname ?: ""
    }
}
