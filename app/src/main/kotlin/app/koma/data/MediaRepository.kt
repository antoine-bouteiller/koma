package app.koma.data

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString

internal fun KomaDatabase.upsert(media: Media) {
    mediaQueries.upsert(
        media.ref.backend.name,
        media.ref.id,
        media.title,
        media.cover,
        media.kind.name,
        media.score,
        if (media.adult) 1 else 0,
        if (media.longStrip) 1 else 0,
    )
}

internal fun mediaOf(
    backend: String,
    id: String,
    title: String,
    cover: String,
    kind: String,
    score: Double?,
    adult: Long,
    longStrip: Long,
) =
    Media(
        MediaRef(BackendId.valueOf(backend), id),
        title,
        cover,
        Kind.entries.find { it.name == kind } ?: Kind.Other,
        score,
        adult != 0L,
        longStrip != 0L,
    )

/** Public media data: stored across sign-outs, looked up once. */
class MediaRepository(private val store: Store, private val backend: Backend) {
    /** Null until stored; an empty title marks media the backend no longer answers. */
    fun media(ref: MediaRef): Flow<Media?> {
        val query = { db: KomaDatabase -> db.mediaQueries.one(ref.backend.name, ref.id, ::mediaOf) }
        return store.watch({ listOf(query(this)) }) { query(this).executeAsOneOrNull() }
    }

    /** The backend's details of [ref], null until fetched. Never refreshed once stored. */
    fun details(ref: MediaRef): Flow<MediaDetails?> {
        val query = { db: KomaDatabase -> db.mediaDetailsQueries.one(ref.backend.name, ref.id) }
        return store.watch({ listOf(query(this)) }) {
            query(this).executeAsOneOrNull()?.let(::decodeDetails)
        }
    }

    /** Fetches and stores the details of [ref], unless they are stored. */
    suspend fun loadDetails(ref: MediaRef) {
        val stored = store.read {
            mediaDetailsQueries.one(ref.backend.name, ref.id).executeAsOneOrNull()
        }
        if (stored?.let(::decodeDetails) != null) return
        val details = backend.details(ref)
        store.write {
            mediaDetailsQueries.upsert(ref.backend.name, ref.id, json.encodeToString(details))
        }
    }

    /** Looks up the refs not stored yet. Those the backend does not answer get an empty title. */
    suspend fun cache(refs: Collection<MediaRef>) {
        val known = store.read { mediaQueries.ids(backend.id.name).executeAsList() }.toSet()
        val missing = refs.filter { it.backend == backend.id && it.id !in known }.distinct()
        if (missing.isEmpty()) return
        val found = backend.media(missing).associateBy { it.ref }
        store.write {
            for (ref in missing) {
                upsert(found[ref] ?: Media(ref, "", "", Kind.Other, null, false, false))
            }
        }
    }
}

// Details an older app stored in another shape do not decode, and are fetched again.
private fun decodeDetails(text: String): MediaDetails? =
    try {
        json.decodeFromString<MediaDetails>(text)
    } catch (_: IllegalArgumentException) {
        null
    }
