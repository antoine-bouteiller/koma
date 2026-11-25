package app.koma.data

import app.koma.data.mangabaka.MangaBakaBackend
import app.koma.testing.BATCH
import app.koma.testing.EVENTS
import app.koma.testing.FakeBrowser
import app.koma.testing.FakeNetwork
import app.koma.testing.MemorySecretStore
import app.koma.testing.RECENT_EVENTS
import app.koma.testing.batch
import app.koma.testing.event
import app.koma.testing.events
import app.koma.testing.series
import java.time.Clock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HistoryRepositoryTest {
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
    private val store = memoryStore()
    private val history = HistoryRepository(store, backend, MediaRepository(store, backend))

    @Test
    fun `the first sync stops at the first page holding a read`() = runTest {
        network.answer(BATCH, 200, batch(series(1, "Berserk")))
        network.answer(
            RECENT_EVENTS,
            200,
            events(50, "p2", true, event(1, 1, 12, 10, "2026-01-01T10:00:00Z")),
        )
        network.answer(
            "$RECENT_EVENTS&cursor=p2",
            200,
            events(50, "p3", true, event(2, 1, 10, 11, "2026-01-01T09:00:00Z")),
        )
        network.answer("$RECENT_EVENTS&cursor=p3", 200, events(50, "end", false))
        history.syncNewer()
        assertEquals(listOf(RECENT_EVENTS, "$RECENT_EVENTS&cursor=p2"), network.requested(EVENTS))
        assertEquals(true, history.history.first().hasNext)
    }

    @Test
    fun `only raised chapter progress is a read`() {
        assertTrue(isRead(4.0, 4.5))
        assertTrue(isRead(null, 1.0))
        assertFalse(isRead(12.0, 10.0))
        assertFalse(isRead(3.0, 3.0))
        assertFalse(isRead(3.0, null))
    }
}
