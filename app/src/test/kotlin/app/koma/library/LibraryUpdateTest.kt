package app.koma.library

import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import app.koma.data.ChapterUpdate
import app.koma.source.CatalogSource
import app.koma.testing.AppHarness
import app.koma.testing.BATCH
import app.koma.testing.CHANGES
import app.koma.testing.SERIES
import app.koma.testing.TEST_SOURCE
import app.koma.testing.TestSource
import app.koma.testing.batch
import app.koma.testing.changes
import app.koma.testing.chapter
import app.koma.testing.chapters
import app.koma.testing.fullSeries
import app.koma.testing.mangas
import app.koma.testing.series
import app.koma.testing.upsert
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val READING = "$TEST_SOURCE/manga/solo"
private const val COMPLETED = "$TEST_SOURCE/manga/pluto"

@RunWith(RobolectricTestRunner::class)
class LibraryUpdateTest {
    @get:Rule val app = AppHarness()

    /** Solo Leveling, being read, and Pluto, completed, each found on the test source. */
    private fun library() {
        app.signedIn()
        app.network.answer(
            CHANGES,
            200,
            changes("c1", false, upsert(1, "reading"), upsert(2, "completed")),
        )
        app.network.answer("$CHANGES?limit=1000&cursor=c1", 200, changes("c1", false))
        app.network.answer(BATCH, 200, batch(series(1, "Solo Leveling"), series(2, "Pluto")))
        app.network.answer("$SERIES/1?schema=full", 200, fullSeries(1, "Solo Leveling"))
        app.network.answer("$SERIES/2?schema=full", 200, fullSeries(2, "Pluto"))
        app.network.answer(
            "$TEST_SOURCE/search?q=Solo%20Leveling",
            200,
            mangas("solo" to "Solo Leveling"),
        )
        app.network.answer("$TEST_SOURCE/search?q=Pluto", 200, mangas("pluto" to "Pluto"))
        app.network.answer(READING, 200, chapters(chapter("solo/1", "Chapter 1", 1f)))
        app.network.answer(COMPLETED, 200, chapters(chapter("pluto/1", "Act 1", 1f)))
        app.sources = { listOf(CatalogSource(TestSource())) }
    }

    private fun pull() {
        app.node("library-reading").performTouchInput { swipeDown(top + height / 4, bottom) }
        app.awaitIdle()
    }

    private fun fetched() =
        app.network.requested(READING).size to app.network.requested(COMPLETED).size

    @Test
    fun `the first library update stores every title's chapters, and a later launch asks nothing`() {
        library()
        app.launch()
        assertEquals(1 to 1, fetched())

        app.reload()
        app.tap("manga-1")
        app.has("chapter-solo/1")

        assertEquals(1, app.network.requested(CHANGES).size)
        assertEquals(1 to 1, fetched())
    }

    @Test
    fun `pulling the library asks again for the chapters of titles being read, and of others until stored`() {
        library()
        app.network.answer(COMPLETED, 500, "{}")
        app.launch()
        assertEquals(1 to 1, fetched())

        app.network.answer(COMPLETED, 200, chapters(chapter("pluto/1", "Act 1", 1f)))
        pull()
        assertEquals(2 to 2, fetched())

        pull()
        assertEquals(3 to 2, fetched())
    }

    @Test
    fun `the background update names each title while it loads its chapters`() {
        library()
        val named = mutableMapOf<String, ChapterUpdate?>()
        app.network.whileInFlight = { request ->
            if (request.url == READING || request.url == COMPLETED) {
                named[request.url] = app.libraryUpdates.lastOrNull()
            }
        }
        app.launch()
        assertEquals(
            mapOf(
                READING to ChapterUpdate("Solo Leveling", 1, 2),
                COMPLETED to ChapterUpdate("Pluto", 2, 2),
            ),
            named,
        )
    }
}
