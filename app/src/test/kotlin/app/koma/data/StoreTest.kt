package app.koma.data

import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** A store over a fresh in-memory database. */
fun memoryStore(): Store =
    Store(
        KomaDatabase(
            AndroidSqliteDriver(KomaDatabase.Schema, RuntimeEnvironment.getApplication(), null)
        ),
        Dispatchers.Unconfined,
    )

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class StoreTest {
    private val store = memoryStore()

    @Test
    fun `clearing user data keeps media and drops stale writes`() = runTest {
        val ref = MediaRef(BackendId.MangaBaka, "1")
        store.write { upsert(Media(ref, "Berserk", "", Kind.Manga, null, false, false)) }
        val before = store.generation
        store.write { metaQueries.put("library_cursor", "c1") }
        store.clearUserData()
        assertNull(store.meta("library_cursor"))
        assertEquals(listOf("1"), store.read { mediaQueries.ids(ref.backend.name).executeAsList() })
        assertFalse(store.write(before) { metaQueries.put("library_cursor", "c2") })
        assertNull(store.meta("library_cursor"))
    }

    @Test
    fun `migrating to version 3 rescales stored scores out of five to one decimal`() {
        val driver =
            AndroidSqliteDriver(KomaDatabase.Schema, RuntimeEnvironment.getApplication(), null)
        driver.execute(null, "DROP TABLE media", 0)
        driver.execute(
            null,
            "CREATE TABLE media (backend TEXT NOT NULL, id TEXT NOT NULL, title TEXT NOT NULL, cover TEXT NOT NULL, kind TEXT NOT NULL, score INTEGER, adult INTEGER NOT NULL, long_strip INTEGER NOT NULL, PRIMARY KEY (backend, id))",
            0,
        )
        driver.execute(
            null,
            "INSERT INTO media VALUES ('MangaBaka', '1', 'Berserk', '', 'Manga', 87, 0, 0)",
            0,
        )
        KomaDatabase.Schema.migrate(driver, 2, 3)
        val media =
            KomaDatabase(driver).mediaQueries.one("MangaBaka", "1", ::mediaOf).executeAsOne()
        assertEquals(4.4, media.score)
    }

    @Test
    fun `migrating to version 4 makes room for ratings and reads the library whole again`() {
        val driver =
            AndroidSqliteDriver(KomaDatabase.Schema, RuntimeEnvironment.getApplication(), null)
        driver.execute(null, "DROP TABLE library_entry", 0)
        driver.execute(
            null,
            "CREATE TABLE library_entry (backend TEXT NOT NULL, media_id TEXT NOT NULL, status TEXT NOT NULL, progress REAL, updated_at INTEGER NOT NULL, PRIMARY KEY (backend, media_id))",
            0,
        )
        driver.execute(
            null,
            "INSERT INTO library_entry VALUES ('MangaBaka', '1', 'Reading', NULL, 0)",
            0,
        )
        driver.execute(null, "INSERT INTO meta VALUES ('library_cursor', 'c1')", 0)
        KomaDatabase.Schema.migrate(driver, 3, 4)
        val db = KomaDatabase(driver)
        val entry = db.libraryEntryQueries.entry("MangaBaka", "1").executeAsOne()
        assertEquals("Reading" to null, entry.status to entry.score)
        assertNull(db.metaQueries.value("library_cursor").executeAsOneOrNull())
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
