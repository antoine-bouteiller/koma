package app.koma.data.mangabaka

import app.koma.data.BackendId
import app.koma.data.ExpiredCursorException
import app.koma.data.ListChange
import app.koma.data.ListStatus
import app.koma.data.MediaRef
import app.koma.data.httpClient
import app.koma.testing.BATCH
import app.koma.testing.CHANGES
import app.koma.testing.FakeBrowser
import app.koma.testing.FakeNetwork
import app.koma.testing.LIBRARY
import app.koma.testing.MemorySecretStore
import app.koma.testing.SERIES
import app.koma.testing.batch
import app.koma.testing.changes
import app.koma.testing.fullSeries
import app.koma.testing.tag
import app.koma.testing.upsert
import java.io.IOException
import java.time.Clock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MangaBakaBackendTest {
    private val network = FakeNetwork()
    private val backend =
        MangaBakaBackend(
            httpClient().newBuilder().addInterceptor(network).build(),
            MemorySecretStore().apply {
                set("mangabaka.session", """{"accessToken":"access","refreshToken":"refresh"}""")
            },
            FakeBrowser(),
            Clock.systemUTC(),
        )

    private fun ref(id: Int) = MediaRef(BackendId.MangaBaka, id.toString())

    private suspend fun failure(work: suspend () -> Unit): Throwable? =
        try {
            work()
            null
        } catch (e: IOException) {
            e
        }

    @Test
    fun `library writes send MangaBaka's state names`() = runTest {
        network.answer("$LIBRARY/1", 200, """{"status":200,"data":true}""")
        backend.addEntry(ref(1), ListStatus.Planning)
        backend.updateEntry(ref(1), ListStatus.Completed)
        val (add, update) = network.requests.toList()
        assertEquals("POST" to """{"state":"plan_to_read"}""", add.method to add.body)
        assertEquals("PATCH" to """{"state":"completed"}""", update.method to update.body)
    }

    @Test
    fun `a rating is sent out of 100, and 0 clears it`() = runTest {
        network.answer("$LIBRARY/1", 200, """{"status":200,"data":true}""")
        backend.updateEntry(ref(1), score = 4.3)
        backend.updateEntry(ref(1), score = 0.0)
        assertEquals(
            listOf("""{"rating":86}""", """{"rating":0}"""),
            network.requests.map { it.body },
        )
    }

    @Test
    fun `a library change reads MangaBaka's state names and the rating out of five`() = runTest {
        network.answer(
            CHANGES,
            200,
            changes("c1", false, upsert(1, "plan_to_read", rating = 87)),
        )
        val change = backend.changes(null).items.single() as ListChange.Upsert
        assertEquals(ListStatus.Planning, change.entry.status)
        assertEquals(4.4, change.entry.score)
    }

    @Test
    fun `a refused cursor is an expired cursor`() = runTest {
        network.answer(
            "$CHANGES?limit=1000&cursor=c1",
            400,
            """{"status":400,"message":"Invalid cursor: bootstrap again without one"}""",
        )
        assertTrue(failure { backend.changes("c1") } is ExpiredCursorException)
    }

    @Test
    fun `a reply that cannot be read is an IOException`() = runTest {
        network.answer(CHANGES, 200, """{"status":200,"data":"nope"}""")
        assertTrue(failure { backend.changes(null) } is IOException)
    }

    @Test
    fun `media is looked up 50 ids at a time`() = runTest {
        network.answer(BATCH, 200, batch())
        backend.media((1..120).map(::ref))
        val ids = network.requested(BATCH).map { it.split("id=").size - 1 }
        assertEquals(listOf(50, 50, 20), ids)
    }

    @Test
    fun `ratings are out of five to one decimal and only genre and core tags stay`() = runTest {
        network.answer(
            "$SERIES/1?schema=full",
            200,
            fullSeries(
                1,
                "Solo Leveling",
                emptyList(),
                "releasing",
                null,
                87.6,
                null,
                tag("Monsters", "defining"),
                tag("Dungeon", "core"),
                tag("Action", "unweighted", genre = true),
            ),
        )
        val details = backend.details(ref(1))
        assertEquals(4.4, details.media.score)
        assertEquals(
            listOf("Dungeon" to 100, "Action" to null),
            details.tags.map { it.name to it.rank },
        )
    }
}
