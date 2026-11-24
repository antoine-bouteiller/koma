package app.koma.history

import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatDayTest {
    private val now = Instant.parse("2026-01-01T12:00:00Z")
    private val paris = ZoneId.of("Europe/Paris")

    private fun day(at: String, zone: ZoneId = ZoneId.of("UTC")) =
        formatDay(Instant.parse(at), now, zone, Locale.US)

    @Test
    fun `days next to today are named`() {
        assertEquals("Today", day("2026-01-01T00:00:00Z"))
        assertEquals("Yesterday", day("2025-12-31T23:59:59Z"))
        assertEquals("Tomorrow", day("2026-01-02T08:00:00Z"))
    }

    @Test
    fun `other days are medium dates`() {
        assertEquals("Dec 30, 2025", day("2025-12-30T12:00:00Z"))
    }

    @Test
    fun `the day is the zone's`() {
        // 23:30 UTC on Dec 31 is already Jan 1 in Paris.
        assertEquals("Today", day("2025-12-31T23:30:00Z", paris))
    }

    @Test
    fun `the time is short, with a plain space before the day period`() {
        assertEquals(
            "9:05 PM",
            formatTime(Instant.parse("2026-01-01T21:05:00Z"), ZoneId.of("UTC"), Locale.US),
        )
        assertEquals(
            "21:05",
            formatTime(Instant.parse("2026-01-01T20:05:00Z"), paris, Locale.FRANCE),
        )
    }
}
