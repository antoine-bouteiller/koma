package app.koma.data

import app.koma.data.mangabaka.MangaBakaBackend
import app.koma.testing.BATCH
import app.koma.testing.CHANGES
import app.koma.testing.FakeBackend
import app.koma.testing.FakeBrowser
import app.koma.testing.FakeNetwork
import app.koma.testing.MemorySecretStore
import app.koma.testing.SERIES
import app.koma.testing.batch
import app.koma.testing.changes
import app.koma.testing.fullSeries
import app.koma.testing.series
import app.koma.testing.upsert
import eu.kanade.tachiyomi.source.model.SManga
import java.io.IOException
import java.time.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MergedSeriesTest {
    private val old = MediaRef(BackendId.MangaBaka, "245")
    private val survivor = MediaRef(BackendId.MangaBaka, "234")

    private fun backend(network: FakeNetwork) =
        MangaBakaBackend(
            httpClient().newBuilder().addInterceptor(network).build(),
            MemorySecretStore().apply {
                set("mangabaka.session", """{"accessToken":"access","refreshToken":"refresh"}""")
            },
            FakeBrowser(),
            Clock.systemUTC(),
        )

    @Test
    fun `watches for an old title follow its survivor during a merge and later writes`() = runTest {
        val backend = FakeBackend()
        val old = backend.add("old-α", "Duplicate")
        val survivor = backend.add("survivor-β", "Survivor")
        val store = memoryStore()
        val media = MediaRepository(store, backend)
        val library = LibraryRepository(store, backend, media, Clock.systemUTC())
        val sources = SourceRepository(store, emptyList(), Clock.systemUTC())
        backend.file(old, ListStatus.Reading)
        library.sync()
        media.loadDetails(old)
        media.loadDetails(survivor)
        sources.bind(
            survivor,
            Candidate(
                7,
                "Test",
                SManga.create().apply {
                    url = "/survivor"
                    title = "Chosen"
                },
            ),
        )
        sources.showScanlator(survivor, "Hidden", shown = false)
        val seen = mutableListOf<List<Any?>>()
        val watching =
            launch(UnconfinedTestDispatcher(testScheduler)) {
                combine(
                        media.media(old),
                        library.entry(old),
                        media.details(old),
                        sources.binding(old),
                        sources.excludedScanlators(old),
                    ) { card, entry, details, binding, excluded ->
                        listOf(card?.ref, entry?.ref, details?.media?.ref, binding, excluded)
                    }
                    .collect { seen += it }
            }
        backend.redirects[old] = survivor
        backend.file(survivor, ListStatus.Paused)
        library.sync()
        runCurrent()
        assertEquals(
            listOf(
                survivor,
                survivor,
                survivor,
                Binding(7, "/survivor", "Chosen"),
                setOf("Hidden"),
            ),
            seen.last(),
        )
        library.setStatus(old, ListStatus.Completed)
        sources.showScanlator(survivor, "Later", shown = false)
        runCurrent()
        assertEquals(setOf("Hidden", "Later"), seen.last().last())
        assertEquals(ListStatus.Completed, library.entry(old).first()?.status)
        watching.cancel()
    }

    @Test
    fun `a failed lookup or a sign-out cannot commit a merge with the page cursor`() = runTest {
        for (signOut in listOf(false, true)) {
            val network = FakeNetwork()
            val backend = backend(network)
            val store = memoryStore()
            val media = MediaRepository(store, backend)
            val library = LibraryRepository(store, backend, media, Clock.systemUTC())
            network.answer(BATCH, 200, batch(series(245, "Duplicate")))
            network.answer(CHANGES, 200, changes("before", false, upsert(245, "reading")))
            library.sync()
            network.answer(BATCH, 200, batch(series(234, "Survivor")))
            network.answer(CHANGES, 200, changes("after", false))
            if (signOut) {
                network.answer(
                    "$SERIES/245?schema=full",
                    200,
                    """{"data":{"id":245,"state":"merged","merged_with":234}}""",
                )
                network.whileInFlight = { request ->
                    if (request.url == "$SERIES/245?schema=full")
                        runBlocking { store.clearUserData() }
                }
                library.sync()
                assertEquals(Library(false, emptyList()), library.library.first())
                assertNull(store.meta("library_cursor"))
            } else {
                network.fail("$SERIES/245?schema=full")
                assertTrue(runCatching { library.sync() }.exceptionOrNull() is IOException)
                assertEquals(listOf(old), library.library.first().items.map { it.media.ref })
                assertEquals("before", store.meta("library_cursor"))
            }
            assertEquals(old, store.read { canonical(old) })
            if (!signOut) {
                network.answer(
                    "$SERIES/245?schema=full",
                    200,
                    """{"data":{"id":245,"state":"merged","merged_with":234}}""",
                )
                library.sync()
                assertEquals(listOf(survivor), library.library.first().items.map { it.media.ref })
                assertEquals("after", store.meta("library_cursor"))
            }
            assertEquals(emptyList<String>(), network.unstubbed)
        }
    }

    @Test
    fun `repository writes using an old ref use its stored survivor`() = runTest {
        val backend = FakeBackend()
        val old = backend.add("old-α", "Duplicate")
        val survivor = backend.add("survivor-β", "Survivor")
        val store = memoryStore()
        val media = MediaRepository(store, backend)
        val library = LibraryRepository(store, backend, media, Clock.systemUTC())
        val sources = SourceRepository(store, emptyList(), Clock.systemUTC())
        backend.file(old, ListStatus.Reading)
        library.sync()
        backend.redirects[old] = survivor
        backend.drop(old)
        backend.file(survivor, ListStatus.Paused)
        library.sync()
        library.setStatus(old, ListStatus.Completed)
        library.setScore(old, 4.5)
        val manga =
            SManga.create().apply {
                url = "/chosen"
                title = "Chosen"
            }
        sources.bind(old, Candidate(7, "Test", manga))
        sources.showScanlator(old, "Hidden", shown = false)
        val details = media.loadDetails(old)

        assertEquals(ListStatus.Completed, library.entry(survivor).first()?.status)
        assertEquals(4.5, library.entry(survivor).first()?.score)
        assertEquals(survivor, details.media.ref)
        assertEquals(Binding(7, "/chosen", "Chosen"), sources.binding(survivor).first())
        assertEquals(setOf("Hidden"), sources.excludedScanlators(survivor).first())
        store.read {
            assertNull(libraryEntryQueries.entry(old.backend.name, old.id).executeAsOneOrNull())
            assertNull(mediaDetailsQueries.one(old.backend.name, old.id).executeAsOneOrNull())
            assertNull(bindingQueries.one(old.backend.name, old.id).executeAsOneOrNull())
            assertEquals(
                emptyList<String>(),
                excludedScanlatorQueries.excluded(old.backend.name, old.id).executeAsList(),
            )
        }
        assertEquals(
            listOf("updateEntry(survivor-β, Completed)", "updateEntry(survivor-β, null, 4.5)"),
            backend.calls.filter { it.startsWith("updateEntry") },
        )
    }

    @Test
    fun `a history reply arriving after a backend neutral merge cannot recreate the old id`() =
        runTest {
            val backend = FakeBackend()
            val old = backend.add("old-α", "Duplicate")
            val survivor = backend.add("survivor-β", "Survivor")
            val store = memoryStore()
            val media = MediaRepository(store, backend)
            val library = LibraryRepository(store, backend, media, Clock.systemUTC())
            val history = HistoryRepository(store, backend, media)
            backend.file(old, ListStatus.Reading)
            library.sync()
            backend.reads += ReadEvent("event", old, 100, 1.0, 2.0)
            val answer = backend.hold("readEvents")
            val syncing = launch { history.syncNewer() }
            runCurrent()
            backend.redirects[old] = survivor
            library.sync()
            answer.complete(Unit)
            syncing.join()

            assertEquals(listOf(survivor), library.library.first().items.map { it.media.ref })
            assertEquals(
                listOf("event" to survivor),
                history.history.first().reads.map { it.id to it.media.ref },
            )
            assertNull(
                store.read { mediaQueries.one(old.backend.name, old.id).executeAsOneOrNull() }
            )
        }

    @Test
    fun `a library sync repairs merged ids even with no feed changes and keeps survivor conflicts`() =
        runTest {
            for ((conflict, changed) in
                listOf(false to false, true to false, false to true, true to true)) {
                val network = FakeNetwork()
                val backend = backend(network)
                val store = memoryStore()
                val media = MediaRepository(store, backend)
                val library = LibraryRepository(store, backend, media, Clock.systemUTC())
                val history = HistoryRepository(store, backend, media)
                network.answer(BATCH, 200, batch(series(245, "Duplicate"), series(234, "Survivor")))
                network.answer(
                    CHANGES,
                    200,
                    changes(
                        "before",
                        false,
                        upsert(245, "reading", progress = 12, rating = 80),
                        *if (conflict) arrayOf(upsert(234, "paused", progress = 7, rating = 60))
                        else emptyArray(),
                    ),
                )
                network.answer("$SERIES/245?schema=full", 200, fullSeries(245, "Duplicate"))
                library.sync()
                media.loadDetails(old)
                store.write {
                    readEventQueries.upsert("MangaBaka", "event", old.id, 100, 1.0, 2.0)
                    bindingQueries.put("MangaBaka", old.id, 1, "/old", "Old binding")
                    bindingQueries.unmatch("MangaBaka", old.id)
                    excludedScanlatorQueries.hide("MangaBaka", old.id, "Old group")
                    if (conflict) {
                        bindingQueries.put(
                            "MangaBaka",
                            survivor.id,
                            2,
                            "/survivor",
                            "Survivor binding",
                        )
                        excludedScanlatorQueries.hide("MangaBaka", survivor.id, "Survivor group")
                    }
                }
                network.answer(
                    CHANGES,
                    200,
                    changes(
                        "after",
                        false,
                        *if (changed) arrayOf(upsert(245, "reading", progress = 19, rating = 90))
                        else emptyArray(),
                    ),
                )
                network.answer(BATCH, 200, batch(series(234, "Survivor")))
                network.answer(
                    "$SERIES/245?schema=full",
                    200,
                    fullSeries(245, "Duplicate")
                        .replace(
                            "\"state\":\"active\",\"merged_with\":null",
                            "\"state\":\"merged\",\"merged_with\":234",
                        ),
                )
                library.sync()

                assertEquals(listOf(survivor), library.library.first().items.map { it.media.ref })
                val entry = library.entry(survivor).first()!!
                assertEquals(if (conflict) ListStatus.Paused else ListStatus.Reading, entry.status)
                assertEquals(if (conflict) 7.0 else if (changed) 19.0 else 12.0, entry.progress)
                assertEquals(if (conflict) 3.0 else if (changed) 4.5 else 4.0, entry.score)
                store.read {
                    assertNull(
                        libraryEntryQueries.entry(old.backend.name, old.id).executeAsOneOrNull()
                    )
                    assertNull(mediaQueries.one(old.backend.name, old.id).executeAsOneOrNull())
                    assertNull(
                        mediaDetailsQueries.one(old.backend.name, old.id).executeAsOneOrNull()
                    )
                }
                assertEquals(listOf(survivor), history.history.first().reads.map { it.media.ref })
                store.read {
                    assertNull(bindingQueries.one("MangaBaka", old.id).executeAsOneOrNull())
                    val binding = bindingQueries.one("MangaBaka", survivor.id).executeAsOne()
                    assertEquals(if (conflict) "/survivor" else "/old", binding.manga_url)
                    assertEquals(
                        emptyList<String>(),
                        bindingQueries.unmatched("MangaBaka").executeAsList(),
                    )
                    assertEquals(
                        emptyList<String>(),
                        excludedScanlatorQueries.excluded("MangaBaka", old.id).executeAsList(),
                    )
                    assertEquals(
                        if (conflict) setOf("Old group", "Survivor group") else setOf("Old group"),
                        excludedScanlatorQueries
                            .excluded("MangaBaka", survivor.id)
                            .executeAsList()
                            .toSet(),
                    )
                }
                assertEquals("after", store.meta("library_cursor"))
                assertEquals(emptyList<String>(), network.unstubbed)
            }
        }
}
