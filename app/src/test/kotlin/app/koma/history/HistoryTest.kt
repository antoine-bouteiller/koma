package app.koma.history

import app.koma.testing.AppHarness
import app.koma.testing.BATCH
import app.koma.testing.CHANGES
import app.koma.testing.EVENTS
import app.koma.testing.RECENT_EVENTS
import app.koma.testing.batch
import app.koma.testing.changes
import app.koma.testing.event
import app.koma.testing.events
import app.koma.testing.series
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HistoryTest {
    @get:Rule val app = AppHarness()

    private fun signedInWithEmptyLibrary() {
        app.epoch = Instant.parse("2026-01-01T12:00:00Z")
        app.zone = ZoneOffset.UTC
        app.signedIn()
        app.network.answer(CHANGES, 200, changes("c0", false))
        app.network.answer(BATCH, 200, batch(series(1, "Berserk"), series(2, "Vagabond")))
    }

    @Test
    fun `signed out, the history asks to sign in to MangaBaka`() {
        app.launch()
        app.tap("tab-history")
        app.has("history-sign-in")
        app.missing("history")
    }

    @Test
    fun `the history groups a series' consecutive reads of one day under that day`() {
        signedInWithEmptyLibrary()
        app.network.answer(
            RECENT_EVENTS,
            200,
            events(
                50,
                "end",
                false,
                event(6, 1, 14, 15, "2026-01-01T10:00:00.000Z"),
                event(5, 1, 13, 14, "2026-01-01T09:30:00.000Z"),
                event(4, 1, 12, 13, "2025-12-31T21:00:00.000Z"),
                event(3, 2, 4, 4.5, "2025-12-31T20:00:00.000Z"),
                event(2, 1, 10, 12, "2025-12-31T19:00:00.000Z"),
                event(1, 1, 12, 10, "2025-12-31T18:00:00.000Z"),
            ),
        )
        app.launch()
        app.tap("tab-history")
        app.text("history-day-day-2026-01-01", "Today")
        app.text("history-title-6", "Berserk")
        app.text("history-chapters-6", "Ch. 14–15 · 10:00 AM")
        app.missing("history-5")
        app.text("history-day-day-2025-12-31", "Yesterday")
        app.text("history-chapters-4", "Ch. 13 · 9:00 PM")
        app.text("history-chapters-3", "Ch. 4.5 · 8:00 PM")
        app.text("history-chapters-2", "Ch. 11–12 · 7:00 PM")
        app.missing("history-1")
        assertEquals(
            listOf(
                "history-day-day-2026-01-01",
                "history-6",
                "history-day-day-2025-12-31",
                "history-4",
                "history-3",
                "history-2",
            ),
            app.childTags("history"),
        )
    }

    @Test
    fun `the history loads older pages and extends a chain across them`() {
        signedInWithEmptyLibrary()
        app.network.answer(
            RECENT_EVENTS,
            200,
            events(50, "older", true, event(6, 1, 14, 15, "2026-01-01T10:00:00.000Z")),
        )
        app.network.answer(
            "$RECENT_EVENTS&cursor=older",
            200,
            events(
                50,
                "end",
                false,
                event(5, 1, 13, 14, "2026-01-01T09:30:00.000Z"),
                event(3, 2, 4, 5, "2026-01-01T08:00:00.000Z"),
            ),
        )
        app.launch()
        app.tap("tab-history")
        app.text("history-chapters-6", "Ch. 14–15 · 10:00 AM")
        app.text("history-chapters-3", "Ch. 5 · 8:00 AM")
        app.missing("history-more-loading")
        // No page is left to ask for.
        assertEquals(
            listOf(RECENT_EVENTS, "$RECENT_EVENTS&cursor=older"),
            app.network.requested(RECENT_EVENTS),
        )
    }

    @Test
    fun `only the first visit to the history asks for newer events, and it opens offline`() {
        signedInWithEmptyLibrary()
        app.network.answer(
            RECENT_EVENTS,
            200,
            events(50, "end", false, event(6, 1, 14, 15, "2026-01-01T10:00:00.000Z")),
        )
        app.launch()
        app.tap("tab-history")
        app.text("history-chapters-6", "Ch. 15 · 10:00 AM")
        app.network.fail(RECENT_EVENTS)
        app.network.answer(
            "$EVENTS?event_type=progress_update&created_at_lower=2026-01-01T10:00:00Z&limit=250",
            200,
            events(
                250,
                "now",
                false,
                event(8, 2, 5, 6, "2026-01-01T11:00:00.000Z"),
                event(6, 1, 14, 15, "2026-01-01T10:00:00.000Z"),
            ),
        )
        app.tap("tab-library")
        app.tap("tab-history")
        app.missing("history-8")
        app.reload()
        app.text("history-chapters-8", "Ch. 6 · 11:00 AM")
        app.text("history-chapters-6", "Ch. 15 · 10:00 AM")
        app.network.fail("$EVENTS?event_type=progress_update&created_at_lower")
        app.reload()
        app.text("history-chapters-8", "Ch. 6 · 11:00 AM")
        app.missing("history-retry")
    }

    @Test
    fun `a failed older page waits for Load More`() {
        signedInWithEmptyLibrary()
        app.network.answer(
            RECENT_EVENTS,
            200,
            events(50, "older", true, event(6, 1, 14, 15, "2026-01-01T10:00:00.000Z")),
        )
        app.network.fail("$RECENT_EVENTS&cursor=older")
        app.launch()
        app.tap("tab-history")
        app.has("history-more-retry")
        assertEquals(1, app.network.requested("$RECENT_EVENTS&cursor=older").size)
        app.network.answer(
            "$RECENT_EVENTS&cursor=older",
            200,
            events(50, "end", false, event(3, 2, 4, 5, "2026-01-01T08:00:00.000Z")),
        )
        app.tap("history-more-retry")
        app.text("history-chapters-3", "Ch. 5 · 8:00 AM")
        app.missing("history-more-retry")
    }

    @Test
    fun `a history MangaBaka never answered offers to try again`() {
        signedInWithEmptyLibrary()
        app.network.fail(EVENTS)
        app.launch()
        app.tap("tab-history")
        app.has("history-retry")
        app.network.answer(EVENTS, 200, events(50, "end", false))
        app.tap("history-retry")
        app.has("empty-history")
    }
}
