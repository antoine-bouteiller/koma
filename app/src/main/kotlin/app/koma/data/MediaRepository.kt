package app.koma.data

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString

internal fun KomaDatabase.upsert(media: Media) {
    if (canonical(media.ref) != media.ref) return
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

/** A sampled cover's ARGB seed; null when the cover has no suitable color. */
data class CoverColors(val cover: String, val seed: Int?)

/** Public media data: stored across sign-outs, looked up once. */
class MediaRepository(private val store: Store, private val backend: Backend) {
    /** Null until stored; an empty title marks media the backend no longer answers. */
    fun media(ref: MediaRef): Flow<Media?> {
        val query = { db: KomaDatabase ->
            val target = db.canonical(ref)
            db.mediaQueries.one(target.backend.name, target.id, ::mediaOf)
        }
        return store.watch({
            listOf(query(this), mediaReplacementQueries.survivor(ref.backend.name, ref.id))
        }) {
            query(this).executeAsOneOrNull()
        }
    }

    /** The backend's details of [ref], null until fetched. Never refreshed once stored. */
    fun details(ref: MediaRef): Flow<MediaDetails?> {
        val query = { db: KomaDatabase ->
            val target = db.canonical(ref)
            db.mediaDetailsQueries.one(target.backend.name, target.id)
        }
        return store.watch({
            listOf(query(this), mediaReplacementQueries.survivor(ref.backend.name, ref.id))
        }) {
            query(this).executeAsOneOrNull()?.let(::decodeDetails)
        }
    }

    /** Fetches and stores the details of [ref], unless they are stored. */
    suspend fun loadDetails(ref: MediaRef): MediaDetails {
        val target = store.read { canonical(ref) }
        val stored = store.read {
            mediaDetailsQueries.one(target.backend.name, target.id).executeAsOneOrNull()
        }
        stored?.let(::decodeDetails)?.let {
            return it
        }
        val details = backend.details(target)
        val current = store.write {
            val current = canonical(target)
            if (current == target) {
                mediaDetailsQueries.upsert(
                    target.backend.name,
                    target.id,
                    json.encodeToString(details),
                )
            }
            current
        }
        return if (current == target) details else loadDetails(current)
    }

    /** Null until this title's cover has been sampled successfully. */
    fun coverColors(ref: MediaRef): CoverColors? = store.read {
        val target = canonical(ref)
        mediaDetailsQueries.colors(target.backend.name, target.id).executeAsOneOrNull()?.let { row
            ->
            row.color_cover?.let { CoverColors(it, row.color_seed?.toInt()) }
        }
    }

    suspend fun cacheCoverColors(ref: MediaRef, colors: CoverColors) {
        store.write {
            val target = canonical(ref)
            if (
                target != ref &&
                    mediaDetailsQueries
                        .one(target.backend.name, target.id)
                        .executeAsOneOrNull()
                        ?.let(::decodeDetails)
                        ?.media
                        ?.cover != colors.cover
            )
                return@write
            mediaDetailsQueries.cacheColors(
                colors.cover,
                colors.seed?.toLong(),
                target.backend.name,
                target.id,
            )
        }
    }

    /** Looks up the refs not stored yet. Those the backend does not answer get an empty title. */
    suspend fun cache(refs: Collection<MediaRef>) {
        val missing = store.read {
            val known = mediaQueries.ids(backend.id.name).executeAsList().toSet()
            refs
                .map { canonical(it) }
                .filter { it.backend == backend.id && it.id !in known }
                .distinct()
        }
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
