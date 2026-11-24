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
