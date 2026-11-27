package app.koma.data

import app.koma.source.CatalogSource
import app.koma.source.normalize
import app.koma.source.variants
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow

/** The source manga behind a media: [source] is Mihon's `Source.id`. */
data class Binding(val source: Long, val mangaUrl: String, val title: String)

/** A source manga the user may bind a media to. */
data class Candidate(val source: Long, val sourceName: String, val manga: SManga)

/**
 * Finds and keeps the source manga behind each media. Source members throw any exception on a
 * failed request.
 */
class SourceRepository(private val store: Store, private val sources: List<CatalogSource>) {
    /** Null until bound. */
    fun binding(ref: MediaRef): Flow<Binding?> {
        val query = { db: KomaDatabase ->
            db.bindingQueries.one(ref.backend.name, ref.id, ::Binding)
        }
        return store.watch({ listOf(query(this)) }) { query(this).executeAsOneOrNull() }
    }

    /**
     * The stored binding, else the first source manga a source proves or matches by title, which is
     * stored. Null when no source answers one; a failed request throws and stores nothing.
     */
    suspend fun resolve(details: MediaDetails): Binding? {
        val ref = details.media.ref
        store
            .read { bindingQueries.one(ref.backend.name, ref.id, ::Binding).executeAsOneOrNull() }
            ?.let {
                return it
            }
        for (catalog in ordered()) {
            val manga = find(catalog, details) ?: continue
            val binding = Binding(catalog.source.id, manga.url, manga.title)
            store.write { put(ref, binding) }
            return binding
        }
        return null
    }

    /** Each source's search hits for the media's title; a source whose search fails is left out. */
    suspend fun candidates(details: MediaDetails): List<Candidate> =
        ordered().flatMap { catalog ->
            val source = catalog.source
            val hits =
                try {
                    source.getSearchManga(1, details.media.title, FilterList()).mangas
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    emptyList()
                }
            hits.map { Candidate(source.id, source.name, it) }
        }

    /** Replaces any binding of [ref]. */
    suspend fun bind(ref: MediaRef, candidate: Candidate) {
        val binding = Binding(candidate.source, candidate.manga.url, candidate.manga.title)
        store.write { put(ref, binding) }
    }

    /** How many sources Koma lists. */
    val count: Int
        get() = sources.size

    /** Null when Koma no longer lists the source. */
    fun source(id: Long): HttpSource? = sources.firstOrNull { it.source.id == id }?.source

    /** The user's preferred sources first, then the others in the order Koma lists them. */
    private fun ordered(): List<CatalogSource> {
        val preferred = store.read { bindingQueries.preferred().executeAsList() }
        return sources.sortedBy { catalog ->
            preferred.indexOf(catalog.source.id).takeIf { it >= 0 } ?: preferred.size
        }
    }

    private suspend fun find(catalog: CatalogSource, details: MediaDetails): SManga? {
        catalog.finder?.let {
            return it.find(details)
        }
        val titles = variants(details)
        val wanted = titles.map(::normalize).toSet()
        for (variant in titles) {
            val hits = catalog.source.getSearchManga(1, variant, FilterList()).mangas
            hits
                .firstOrNull { normalize(it.title) in wanted }
                ?.let {
                    return it
                }
        }
        return null
    }

    private fun KomaDatabase.put(ref: MediaRef, binding: Binding) =
        bindingQueries.put(
            ref.backend.name,
            ref.id,
            binding.source,
            binding.mangaUrl,
            binding.title,
        )
}
