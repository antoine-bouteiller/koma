package app.koma.history

import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.koma.data.Authorized
import app.koma.data.KomaDatabase
import app.koma.data.MangaBakaApi
import app.koma.data.Store
import app.koma.data.getText
import app.koma.data.httpClient
import app.koma.data.json
import app.koma.testing.BATCH
import app.koma.testing.EVENTS
import app.koma.testing.FakeNetwork
import app.koma.testing.RECENT_EVENTS
import app.koma.testing.batch
import app.koma.testing.event
import app.koma.testing.events
import app.koma.testing.series
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.DeserializationStrategy
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class HistorySyncTest {
    private val network = FakeNetwork()
    private val http = httpClient().newBuilder().addInterceptor(network).build()
    private val api =
        MangaBakaApi(
            http,
            object : Authorized {
                override suspend fun <T> get(url: String, body: DeserializationStrategy<T>): T =
                    json.decodeFromString(body, http.getText(url))

                override suspend fun send(method: String, url: String, body: String) =
                    error("unused")
            },
        )
    private val db =
        KomaDatabase(
            AndroidSqliteDriver(KomaDatabase.Schema, RuntimeEnvironment.getApplication(), null)
        )
    private val sync = HistorySync(Store(db, api, Dispatchers.Unconfined), api)

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
        sync.syncNewer()
        assertEquals(listOf(RECENT_EVENTS, "$RECENT_EVENTS&cursor=p2"), network.requested(EVENTS))
        assertEquals(true, db.readHistory().hasNext)
    }
}
