package app.koma.title

import androidx.compose.ui.test.assertContentDescriptionEquals
import app.koma.testing.AppHarness
import app.koma.testing.BATCH
import app.koma.testing.CHANGES
import app.koma.testing.LIBRARY
import app.koma.testing.SERIES
import app.koma.testing.batch
import app.koma.testing.changes
import app.koma.testing.fullSeries
import app.koma.testing.series
import app.koma.testing.tag
import app.koma.testing.upsert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
class TitleTest {
    @get:Rule val app = AppHarness()

    private fun library(vararg entries: String) {
        app.signedIn()
        app.network.answer(CHANGES, 200, changes("c1", false, *entries))
        app.network.answer(BATCH, 200, batch(series(1, "Solo Leveling"), series(2, "Pluto")))
    }

    @Test
    fun `a library title shows MangaBaka's facts, its summary and its tags, genres first`() {
        library(upsert(1, "reading"))
        app.network.answer(
            "$SERIES/1?schema=full",
            200,
            fullSeries(
                1,
                "Solo Leveling",
                authors = listOf("Chu-Gong", "Jang Sung-Lak"),
                status = "completed",
                startDate = "2018-03-04",
                rating = 86.15,
                description = "Jinwoo levels up.",
                tag("Dungeon", "core"),
                tag("War", "core", spoiler = true),
                tag("Monsters", "incidental"),
                tag("Action", "defining", genre = true),
            ),
        )
        app.launch()
        app.tap("manga-1")
        app.text("title-name", "Solo Leveling")
        app.text("title-authors", "Chu-Gong, Jang Sung-Lak")
        app.text("title-release", "Completed · 2018")
        app.text("title-rating", "86")
        app.text("title-status", "Reading")
        app.text("title-summary", "Jinwoo levels up.")
        assertEquals(
            listOf("tag-Action", "tag-Dungeon", "tag-Monsters"),
            app.childTags("title-tags"),
        )
        val request = app.network.requests.last { it.url.startsWith(SERIES) }
        assertNull(request.headers["Authorization"])

        app.tap("back")
        app.has("library-reading")
    }

    @Test
    fun `a new status is saved on MangaBaka and files the title on its shelf`() {
        library(upsert(1, "reading"), upsert(2, "reading", "2026-01-02T00:00:00Z"))
        app.network.answer("$SERIES/1?schema=full", 200, fullSeries(1, "Solo Leveling"))
        app.network.answer("$LIBRARY/1", 200, """{"status":200,"data":true}""")
        app.launch()
        app.tap("manga-1")
        app.tap("title-status")
        app.tap("status-completed")
        app.text("title-status", "Completed")
        val write = app.network.requests.single { it.url == "$LIBRARY/1" }
        assertEquals("PATCH", write.method)
        assertEquals("""{"state":"completed"}""", write.body)
        assertEquals("Bearer access", write.headers["Authorization"])

        app.tap("back")
        assertEquals(listOf("manga-2"), app.childTags("library-reading"))
        app.tap("shelf-archived")
        assertEquals(listOf("manga-1"), app.childTags("library-archived"))
    }

    @Test
    fun `a status MangaBaka refuses is not kept`() {
        library(upsert(1, "reading"))
        app.network.answer("$SERIES/1?schema=full", 200, fullSeries(1, "Solo Leveling"))
        app.network.answer("$LIBRARY/1", 500, """{"status":500}""")
        app.launch()
        app.tap("manga-1")
        app.tap("title-status")
        app.tap("status-dropped")
        app.has("title-status-error")
        app.text("title-status", "Reading")
    }

    // Legacy graphics measure no text, so no summary would overflow.
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test
    fun `a long summary folds until it is tapped`() {
        library(upsert(1, "reading"))
        app.network.answer(
            "$SERIES/1?schema=full",
            200,
            fullSeries(1, "Solo Leveling", description = "Jinwoo levels up. ".repeat(40)),
        )
        app.launch()
        app.tap("manga-1")
        app.node("title-summary").assertContentDescriptionEquals("Show more")
        app.tap("title-summary")
        app.node("title-summary").assertContentDescriptionEquals("Show less")
    }

    @Test
    fun `details MangaBaka did not send can be asked for again`() {
        library(upsert(1, "reading"))
        app.network.fail("$SERIES/1?schema=full")
        app.launch()
        app.tap("manga-1")
        app.text("title-name", "Solo Leveling")
        app.missing("title-authors")
        app.network.answer(
            "$SERIES/1?schema=full",
            200,
            fullSeries(1, "Solo Leveling", authors = listOf("Chu-Gong")),
        )
        app.tap("title-retry")
        app.text("title-authors", "Chu-Gong")
    }
}
