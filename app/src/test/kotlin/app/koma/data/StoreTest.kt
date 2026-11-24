package app.koma.data

import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.koma.library.Manga
import app.koma.library.readLibrary
import app.koma.testing.BATCH
import app.koma.testing.FakeNetwork
import app.koma.testing.batch
import app.koma.testing.series
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.DeserializationStrategy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class StoreTest {
    private val network = FakeNetwork()
    private val noAuth =
        object : Authorized {
            override suspend fun <T> get(url: String, body: DeserializationStrategy<T>): T =
                error("unused")
        }
    private val db =
        KomaDatabase(
            AndroidSqliteDriver(KomaDatabase.Schema, RuntimeEnvironment.getApplication(), null)
        )
    private val store =
        Store(
            db,
            MangaBakaApi(httpClient().newBuilder().addInterceptor(network).build(), noAuth),
            Dispatchers.Unconfined,
        )

    private fun titles() = db.seriesQueries.ids().executeAsList()

    @Test
    fun `series are looked up once, 50 at a time, and missing ones keep an empty title`() =
        runTest {
            network.answer(BATCH, 200, batch(series(1, "Berserk")))
            store.cacheSeries((1L..120L).toList())
            store.cacheSeries(listOf(1L, 2L))
            assertEquals(3, network.requests.size)
            assertEquals(50, network.requests[0].url.split("id=").size - 1)
            assertEquals(20, network.requests[2].url.split("id=").size - 1)
            assertEquals(120, titles().size)
            store.write {
                libraryEntryQueries.upsert(1, "reading", "2026-01-01")
                libraryEntryQueries.upsert(2, "reading", "2026-01-02")
            }
            // An id MangaBaka did not answer is kept with an empty title, which the screens skip.
            assertEquals(
                listOf(Manga("1", "Berserk", "https://cdn.mangabaka.dev/1.jpg")),
                db.readLibrary().shelves.first { it.id == "reading" }.items,
            )
        }

    @Test
    fun `clearing user data keeps the series cache and drops stale writes`() = runTest {
        network.answer(BATCH, 200, batch(series(1, "Berserk")))
        store.cacheSeries(listOf(1L))
        val before = store.generation
        store.write { metaQueries.put("library_cursor", "c1") }
        store.clearUserData()
        assertNull(store.meta("library_cursor"))
        assertEquals(listOf(1L), titles())
        assertFalse(store.write(before) { metaQueries.put("library_cursor", "c2") })
        assertNull(store.meta("library_cursor"))
    }

    @Test
    fun `a watch answers again after each write`() = runTest {
        val seen = mutableListOf<String?>()
        val watching =
            launch(UnconfinedTestDispatcher(testScheduler)) {
                store
                    .watch({ listOf(metaQueries.value("k")) }) {
                        metaQueries.value("k").executeAsOneOrNull()
                    }
                    .collect { seen += it }
            }
        store.write { metaQueries.put("k", "v") }
        watching.cancel()
        assertEquals(listOf(null, "v"), seen)
    }
}
