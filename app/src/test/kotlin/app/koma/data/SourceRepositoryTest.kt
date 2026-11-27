package app.koma.data

import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.koma.source.CatalogSource
import app.koma.source.Finder
import app.koma.source.details
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SourceRepositoryTest {
    private val driver =
        AndroidSqliteDriver(KomaDatabase.Schema, RuntimeEnvironment.getApplication(), null)
    private val store = Store(KomaDatabase(driver), Dispatchers.Unconfined)
    private val solo = details("Solo Leveling", "Na Honjaman Level Up")

    private fun repository(vararg sources: CatalogSource) =
        SourceRepository(store, sources.toList())

    @Test
    fun `a source without a finder binds the first hit matching any variant`() = runTest {
        val source =
            FakeSource(
                1,
                "Solo Leveling" to listOf(manga("a", "Solo Leveling: Ragnarok")),
                "Na Honjaman Level Up" to listOf(manga("b", "NA HONJAMAN — LÉVEL UP!")),
            )
        val sources = repository(CatalogSource(source))

        val bound = sources.resolve(solo)

        assertEquals(Binding(1, "b", "NA HONJAMAN — LÉVEL UP!"), bound)
        assertEquals(bound, sources.binding(solo.media.ref).first())
    }

    @Test
    fun `a stored binding answers without asking any source`() = runTest {
        val source = FakeSource(1, "Solo Leveling" to listOf(manga("a", "Solo Leveling")))
        val sources = repository(CatalogSource(source))
        sources.resolve(solo)
        source.searches.clear()

        assertEquals(Binding(1, "a", "Solo Leveling"), sources.resolve(solo))
        assertEquals(emptyList<String>(), source.searches)
    }

    @Test
    fun `nothing binds when no hit matches, and every hit is a candidate`() = runTest {
        val first = FakeSource(1, "Solo Leveling" to listOf(manga("a", "Solo Leveling: Ragnarok")))
        val second = FakeSource(2, "Solo Leveling" to listOf(manga("b", "Solo Levelling")))
        val sources = repository(CatalogSource(first), CatalogSource(second))

        assertNull(sources.resolve(solo))
        assertNull(sources.binding(solo.media.ref).first())
        assertEquals(
            listOf(1L to "Solo Leveling: Ragnarok", 2L to "Solo Levelling"),
            sources.candidates(solo).map { it.source to it.manga.title },
        )
    }

    @Test
    fun `a source with a finder binds only what its finder proves`() = runTest {
        val proven = FakeSource(1, "Solo Leveling" to listOf(manga("x", "Solo Leveling")))
        val matched = FakeSource(2, "Solo Leveling" to listOf(manga("y", "Solo Leveling")))
        val unproven = repository(CatalogSource(proven) { null }, CatalogSource(matched))

        assertEquals(Binding(2, "y", "Solo Leveling"), unproven.resolve(solo))
        assertEquals(emptyList<String>(), proven.searches)

        val other = details("Pluto", id = "2")
        val finder = Finder { manga("z", "I Level Up Alone") }
        assertEquals(
            Binding(1, "z", "I Level Up Alone"),
            repository(CatalogSource(proven, finder)).resolve(other),
        )
    }

    @Test
    fun `preferred sources are tried first, then the others in the order Koma lists them`() =
        runTest {
            val first = FakeSource(1, "Solo Leveling" to listOf(manga("a", "Solo Leveling")))
            val second = FakeSource(2, "Solo Leveling" to listOf(manga("b", "Solo Leveling")))
            driver.execute(null, "INSERT INTO preferred_source VALUES (2, 0)", 0)

            val sources = repository(CatalogSource(first), CatalogSource(second))

            assertEquals(2L, sources.resolve(solo)?.source)
            assertEquals(listOf(2L, 1L), sources.candidates(solo).map { it.source })
        }

    @Test
    fun `a failed search stores nothing, and a later resolve tries again`() = runTest {
        val source = FakeSource(1, "Solo Leveling" to listOf(manga("a", "Solo Leveling")))
        source.failing = true
        val sources = repository(CatalogSource(source))

        assertThrows(HttpException::class.java) { runBlocking { sources.resolve(solo) } }
        assertNull(sources.binding(solo.media.ref).first())

        source.failing = false
        assertEquals("a", sources.resolve(solo)?.mangaUrl)
    }

    @Test
    fun `a source whose search fails is left out of the candidates`() = runTest {
        val broken = FakeSource(1).apply { failing = true }
        val working = FakeSource(2, "Solo Leveling" to listOf(manga("b", "Solo Leveling")))

        val candidates = repository(CatalogSource(broken), CatalogSource(working)).candidates(solo)

        assertEquals(listOf("b"), candidates.map { it.manga.url })
    }

    @Test
    fun `binding a candidate replaces the binding`() = runTest {
        val source = FakeSource(1, "Solo Leveling" to listOf(manga("a", "Solo Leveling")))
        val sources = repository(CatalogSource(source))
        sources.resolve(solo)

        sources.bind(solo.media.ref, Candidate(1, "Fake", manga("c", "Solo Leveling (Remastered)")))

        assertEquals(Binding(1, "c", "Solo Leveling (Remastered)"), sources.resolve(solo))
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun `a second chapter load of one manga waits for the first instead of failing`() = runTest {
        val source = FakeSource(1)
        val gate = CompletableDeferred<Unit>()
        source.updating = gate
        val sources = repository(CatalogSource(source))
        val binding = Binding(1, "a", "Solo Leveling")

        val first = async { sources.chapters(binding) }
        val second = async { sources.chapters(binding) }
        runCurrent()
        gate.complete(Unit)

        assertEquals(listOf("a/1"), first.await()?.map { it.url })
        assertEquals(listOf("a/1"), second.await()?.map { it.url })
    }

    @Test
    fun `chapters of a source Koma no longer lists answer null`() = runTest {
        assertNull(repository().chapters(Binding(9, "a", "Solo Leveling")))
    }

    @Test
    fun `a source Koma no longer lists answers null`() {
        val source = FakeSource(1)
        val sources = repository(CatalogSource(source))
        assertEquals(source, sources.source(1))
        assertNull(sources.source(9))
    }
}

private fun manga(url: String, title: String): SManga =
    SManga.create().apply {
        this.url = url
        this.title = title
    }

/** A source answering searches from memory: each pair is a query and its hits. */
private class FakeSource(override val id: Long, vararg answers: Pair<String, List<SManga>>) :
    HttpSource() {
    private val answers = answers.toMap()
    val searches = mutableListOf<String>()
    var failing = false
    /** Holds each chapter load until completed. */
    var updating: CompletableDeferred<Unit>? = null
    private val loading = mutableSetOf<String>()

    override val name = "Fake $id"
    override val lang = "xx"
    override val baseUrl = "https://fake.test"
    override val supportsLatest = false

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage {
        searches += query
        if (failing) throw HttpException(500)
        return MangasPage(answers[query].orEmpty(), hasNextPage = false)
    }

    override suspend fun getPopularManga(page: Int) = throw UnsupportedOperationException()

    override suspend fun getLatestUpdates(page: Int) = throw UnsupportedOperationException()

    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        // KeiSource's rule.
        check(loading.add(manga.url)) { "getMangaUpdate must not be called concurrently" }
        try {
            updating?.await()
            val chapter =
                SChapter.create().apply {
                    url = "${manga.url}/1"
                    name = "Chapter 1"
                }
            return SMangaUpdate(manga, listOf(chapter))
        } finally {
            loading.remove(manga.url)
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> =
        throw UnsupportedOperationException()

    override fun getMangaUrl(manga: SManga) = baseUrl

    override fun getChapterUrl(chapter: SChapter) = baseUrl
}
