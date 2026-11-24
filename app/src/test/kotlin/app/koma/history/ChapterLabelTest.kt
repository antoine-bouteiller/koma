package app.koma.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterLabelTest {
    @Test
    fun `a read of one chapter or less names its last chapter`() {
        assertEquals("Ch. 15", chapterLabel(14.0, 15.0))
        assertEquals("Ch. 4.5", chapterLabel(4.0, 4.5))
        assertEquals("Ch. 1", chapterLabel(null, 1.0))
    }

    @Test
    fun `a read of more chapters spans them`() {
        assertEquals("Ch. 11–12", chapterLabel(10.0, 12.0))
        assertEquals("Ch. 5–7.5", chapterLabel(4.5, 7.5))
        assertEquals("Ch. 1–3", chapterLabel(null, 3.0))
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
