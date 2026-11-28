package app.koma.data

import app.koma.source.CatalogSource
import app.koma.source.normalize
import app.koma.source.variants
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import java.time.Clock
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The source manga behind a media: [source] is Mihon's `Source.id`. */
data class Binding(val source: Long, val mangaUrl: String, val title: String)

/** A source manga the user may bind a media to. */
data class Candidate(val source: Long, val sourceName: String, val manga: SManga)

/**
 * Finds and keeps the source manga behind each media. Source members throw any exception on a
 * failed request.
 */
class SourceRepository(
    private val store: Store,
    private val sources: List<CatalogSource>,
    private val clock: Clock,
) {
    // A source refuses a second chapter load of one manga while the first runs, even from another
    // screen: a title reopened as it closes asks again before the closing screen's load ends.
    private val loads = ConcurrentHashMap<Pair<Long, String>, Mutex>()

    /** Null until bound. */
    fun binding(ref: MediaRef): Flow<Binding?> {
        val query = { db: KomaDatabase ->
            val target = db.canonical(ref)
            db.bindingQueries.one(target.backend.name, target.id, ::Binding)
        }
        return store.watch({
            listOf(query(this), mediaReplacementQueries.survivor(ref.backend.name, ref.id))
        }) {
            query(this).executeAsOneOrNull()
        }
    }

    /**
     * The stored binding, else the first source manga a source proves or matches by title, which is
     * stored. Null when no source answers one, which marks the media unmatched until a binding is
     * stored; a failed request throws and stores nothing.
     */
    suspend fun resolve(details: MediaDetails): Binding? {
        val ref = details.media.ref
        store
            .read {
                val target = canonical(ref)
                bindingQueries.one(target.backend.name, target.id, ::Binding).executeAsOneOrNull()
            }
            ?.let {
                return it
            }
        for (catalog in ordered()) {
            val manga = find(catalog, details) ?: continue
            val binding = Binding(catalog.source.id, manga.url, manga.title)
            return store.write {
                val target = canonical(ref)
                bindingQueries.one(target.backend.name, target.id, ::Binding).executeAsOneOrNull()
                    ?: binding.also { put(target, it) }
            }
        }
        return store.write {
            val target = canonical(ref)
            bindingQueries
                .one(target.backend.name, target.id, ::Binding)
                .executeAsOneOrNull()
                .also {
                    if (it == null) bindingQueries.unmatch(target.backend.name, target.id)
                }
        }
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

    /**
     * The chapters of [binding]'s manga: the stored ones, else fetched from the source and stored.
     * [refresh] fetches them even when stored. One load of a manga at a time. Null when Koma no
     * longer lists its source.
     */
    suspend fun chapters(binding: Binding, refresh: Boolean = false): List<SChapter>? =
        load(binding, refresh)?.chapters

    /**
     * Loads [binding]'s chapters as [chapters] does, and answers those the fetch found missing from
     * the stored list, which are stored as found: none when nothing was fetched, nor on the manga's
     * first fetch.
     */
    suspend fun newChapters(binding: Binding, refresh: Boolean): List<SChapter>? =
        load(binding, refresh)?.found

    private class Loaded(val chapters: List<SChapter>, val found: List<SChapter>)

    private suspend fun load(binding: Binding, refresh: Boolean): Loaded? {
        val source = source(binding.source) ?: return null
        val load = loads.computeIfAbsent(binding.source to binding.mangaUrl) { Mutex() }
        return load.withLock {
            if (!refresh)
                stored(binding)?.let {
                    return@withLock Loaded(it, emptyList())
                }
            val manga =
                SManga.create().apply {
                    url = binding.mangaUrl
                    title = binding.title
                }
            val chapters =
                source
                    .getMangaUpdate(manga, emptyList(), fetchDetails = false, fetchChapters = true)
                    .chapters
            val found = store.write { put(binding, chapters) }
            Loaded(chapters, found)
        }
    }

    private fun stored(binding: Binding): List<SChapter>? = store.read {
        if (chapterQueries.listed(binding.source, binding.mangaUrl).executeAsOne() == 0L) {
            return@read null
        }
        chapterQueries
            .stored(binding.source, binding.mangaUrl) { url, name, scanlator, number, uploaded ->
                SChapter.create().apply {
                    this.url = url
                    this.name = name
                    this.scanlator = scanlator
                    chapter_number = number.toFloat()
                    date_upload = uploaded
                }
            }
            .executeAsList()
    }

    /** The scanlators whose chapters [ref]'s title hides. */
    fun excludedScanlators(ref: MediaRef): Flow<Set<String>> {
        val query = { db: KomaDatabase ->
            val target = db.canonical(ref)
            db.excludedScanlatorQueries.excluded(target.backend.name, target.id)
        }
        return store.watch({
            listOf(query(this), mediaReplacementQueries.survivor(ref.backend.name, ref.id))
        }) {
            query(this).executeAsList().toSet()
        }
    }

    /** Shows or hides the chapters of [scanlator] on [ref]'s title. */
    suspend fun showScanlator(ref: MediaRef, scanlator: String, shown: Boolean) = store.write {
        val target = canonical(ref)
        if (shown) excludedScanlatorQueries.show(target.backend.name, target.id, scanlator)
        else excludedScanlatorQueries.hide(target.backend.name, target.id, scanlator)
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

    /**
     * Answers the chapters a stored list lacked, stored as found; none on the manga's first list.
     */
    private fun KomaDatabase.put(binding: Binding, chapters: List<SChapter>): List<SChapter> {
        val known =
            if (chapterQueries.listed(binding.source, binding.mangaUrl).executeAsOne() == 0L) null
            else
                chapterQueries
                    .stored(binding.source, binding.mangaUrl) { url, _, _, _, _ -> url }
                    .executeAsList()
                    .toSet()
        val found = known?.let { chapters.filter { it.url !in known } }.orEmpty()
        val now = clock.millis()
        for (chapter in found) {
            chapterQueries.found(binding.source, binding.mangaUrl, chapter.url, now)
        }
        chapterQueries.clear(binding.source, binding.mangaUrl)
        chapters.forEachIndexed { position, chapter ->
            chapterQueries.put(
                binding.source,
                binding.mangaUrl,
                position.toLong(),
                chapter.url,
                chapter.name,
                chapter.scanlator,
                chapter.chapter_number.toDouble(),
                chapter.date_upload,
            )
        }
        chapterQueries.list(binding.source, binding.mangaUrl)
        return found
    }

    private fun KomaDatabase.put(ref: MediaRef, binding: Binding) {
        val target = canonical(ref)
        bindingQueries.put(
            target.backend.name,
            target.id,
            binding.source,
            binding.mangaUrl,
            binding.title,
        )
        bindingQueries.match(target.backend.name, target.id)
    }
}
