package app.koma.data

import kotlinx.coroutines.flow.Flow

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

    suspend fun details(ref: MediaRef): MediaDetails = backend.details(ref)

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
