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
