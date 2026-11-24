package app.koma.testing

const val CHANGES = "https://api.mangabaka.org/v2/my/library/changes"
const val BATCH = "https://api.mangabaka.org/v2/series/batch"
const val EVENTS = "https://api.mangabaka.org/v2/my/library/events"
const val RECENT_EVENTS = "$EVENTS?event_type=progress_update&limit=50"
const val PROFILE = "https://api.mangabaka.org/v1/my/profile"

fun profile(username: String?) =
    """{"status":200,"data":{"id":"u","nickname":null,"preferred_username":${username?.let { "\"$it\"" } ?: "null"},"picture":null,"updated_at":null,"role":"user","auth_type":"oauth","scopes":["openid"]}}"""

fun upsert(
    seriesId: Int,
    state: String,
    updatedAt: String = "2026-01-01T00:00:00Z",
    revision: Int = seriesId,
) =
    """{"type":"entry","action":"upsert","revision":$revision,"current":{"id":$seriesId,"series_id":$seriesId,"user_id":"u","state":"$state","progress_chapter":null,"created_at":null,"updated_at":"$updatedAt","list_ids":[]}}"""

fun delete(seriesId: Int) =
    """{"type":"entry","action":"delete","revision":99,"current":{"series_id":$seriesId}}"""

fun changes(cursor: String, more: Boolean, vararg data: String) =
    """{"status":200,"pagination":{"limit":1000,"cursor":"$cursor","more":$more},"data":[${data.joinToString(",")}]}"""

fun series(id: Int, title: String, type: String = "manhwa") =
    """{"id":$id,"type":"$type","titles":[{"language":"en","traits":[],"title":"$title"}],"cover":{"raw":null,"x150":null,"x250":null,"x350":"https://cdn.mangabaka.dev/$id.jpg","blurhash":null,"width":null,"height":null}}"""

fun batch(vararg series: String) = """{"status":200,"data":[${series.joinToString(",")}]}"""

fun event(id: Int, seriesId: Int, from: Number, to: Number, at: String) =
    """{"id":$id,"series_id":$seriesId,"event_type":"progress_update","old":{"progress_chapter":$from,"progress_volume":null},"new":{"progress_chapter":$to,"progress_volume":null},"created_at":"$at"}"""

fun events(limit: Int, cursor: String, more: Boolean, vararg data: String) =
    """{"status":200,"pagination":{"limit":$limit,"cursor":"$cursor","more":$more},"data":[${data.joinToString(",")}]}"""

const val SERIES = "https://api.mangabaka.org/v2/series"
const val LIBRARY = "https://api.mangabaka.org/v1/my/library"

fun tag(name: String, weight: String, genre: Boolean = false, spoiler: Boolean = false) =
    """{"id":${name.hashCode()},"is_spoiler":$spoiler,"is_genre":$genre,"is_explicit":false,"implied_by_tag_ids":[],"parent_id":null,"name":"$name","level":2,"name_path":"$name","series_count":1,"description":null,"content_rating":"safe","weight":"$weight"}"""

fun fullSeries(
    id: Int,
    title: String,
    authors: List<String> = emptyList(),
    status: String = "releasing",
    startDate: String? = null,
    rating: Number? = null,
    description: String? = null,
    vararg tags: String,
    alternateTitle: String? = null,
) =
    """{"status":200,"data":{"id":$id,"state":"active","merged_with":null,"authors":[${authors.joinToString(",") { "\"$it\"" }}],"artists":[],"description":${description?.let { "\"$it\"" } ?: "null"},"published":{"start_date":${startDate?.let { "\"$it\"" } ?: "null"},"end_date":null},"status":"$status","type":"manhwa","rating":$rating,"titles":[${alternateTitle?.let { """{"language":"en","traits":[],"title":"$it","is_primary":false},""" } ?: ""}{"language":"en","traits":[],"title":"$title","is_primary":true}],"cover":{"raw":null,"x150":null,"x250":null,"x350":"https://cdn.mangabaka.dev/$id.jpg"},"tags":[${tags.joinToString(",")}]}}"""
