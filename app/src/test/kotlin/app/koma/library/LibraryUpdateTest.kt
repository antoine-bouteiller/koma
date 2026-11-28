package app.koma.library

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onChild
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import app.koma.data.ChapterUpdate
import app.koma.data.FoundChapters
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

    private fun unread(id: Int) = app.compose.onNodeWithTag("unread-$id", useUnmergedTree = true)

    private fun unmatched(id: Int) =
        app.compose.onNodeWithTag("unmatched-$id", useUnmergedTree = true)

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
    fun `a library cover counts the shown chapters past the title's progress`() {
        library()
        app.network.answer(
            CHANGES,
            200,
            changes(
                "c1",
                false,
                upsert(1, "reading", progress = 1.1),
                upsert(2, "completed", progress = 1),
            ),
        )
        app.network.answer(
            READING,
            200,
            chapters(
                chapter("solo/1", "Chapter 1", 1f),
                chapter("solo/1.1", "Chapter 1.1", 1.1f),
                chapter("solo/2z", "Chapter 2", 2f, "Team Z"),
                chapter("solo/2y", "Chapter 2", 2f, "Team Y"),
                chapter("solo/extra", "Extra", -1f),
            ),
        )
        app.launch()
        unread(1).onChild().assertTextEquals("3")
        unread(2).assertDoesNotExist()

        app.tap("manga-1")
        app.tap("title-scanlators")
        app.tap("scanlator-Team Z")
        app.back()
        unread(1).onChild().assertTextEquals("2")

        app.network.answer(
            "$CHANGES?limit=1000&cursor=c1",
            200,
            changes("c2", false, upsert(1, "reading", revision = 3, progress = 2)),
        )
        pull()
        unread(1).onChild().assertTextEquals("1")
    }

    @Test
    fun `a title no source binds is reported and badged until a later update binds it`() {
        library()
        app.network.answer("$TEST_SOURCE/search?q=Pluto", 500, "{}")
        app.launch()
        unmatched(2).assertDoesNotExist()
        assertEquals(listOf(emptyList<String>()), app.unmatched)

        app.network.answer("$TEST_SOURCE/search?q=Pluto", 200, mangas("pluto" to "Pluto 2"))
        pull()
        unmatched(2).assertExists()
        unmatched(1).assertDoesNotExist()
        assertEquals(listOf("Pluto"), app.unmatched.last())

        app.network.answer("$TEST_SOURCE/search?q=Pluto", 200, mangas("pluto" to "Pluto"))
        pull()
        unmatched(2).assertDoesNotExist()
        assertEquals(emptyList<String>(), app.unmatched.last())
    }

    @Test
    fun `an update reports and lists the chapters a refetch adds, but none on the first fetch`() {
        library()
        app.launch()
        assertEquals(listOf(emptyList<FoundChapters>()), app.found)

        app.network.answer(
            READING,
            200,
            chapters(
                chapter("solo/3", "Chapter 3", 3f),
                chapter("solo/2", "Chapter 2", 2f),
                chapter("solo/1", "Chapter 1", 1f),
            ),
        )
        pull()
        assertEquals(
            listOf(FoundChapters("Solo Leveling", listOf("Chapter 3", "Chapter 2"))),
            app.found.last(),
        )

        pull()
        assertEquals(emptyList<FoundChapters>(), app.found.last())

        app.tap("tab-updates")
        assertEquals(
            listOf("updates-day-2026-01-01", "update-1:solo/3", "update-1:solo/2"),
            app.childTags("updates"),
        )
        app.node("update-1:solo/3").assertTextEquals("Solo Leveling", "Chapter 3")
        app.tap("update-1:solo/3")
        app.text("title-name", "Solo Leveling")
    }

    @Test
    fun `chapters of a hidden scanlator are neither reported nor listed`() {
        library()
        app.network.answer(READING, 200, chapters(chapter("solo/1", "Chapter 1", 1f, "Team Z")))
        app.launch()
        app.tap("manga-1")
        app.tap("title-scanlators")
        app.tap("scanlator-Team Z")
        app.back()

        app.network.answer(
            READING,
            200,
            chapters(
                chapter("solo/2z", "Chapter 2", 2f, "Team Z"),
                chapter("solo/1", "Chapter 1", 1f, "Team Z"),
            ),
        )
        pull()
        assertEquals(emptyList<FoundChapters>(), app.found.last())
        app.tap("tab-updates")
        app.has("empty-updates")
    }

    @Test
    fun `a part chapter a later whole chapter outdates is neither counted, reported nor listed`() {
        library()
        app.network.answer(
            READING,
            200,
            chapters(
                chapter("solo/1.1", "Hiatus", 1.1f),
                chapter("solo/1", "Chapter 1", 1f),
            ),
        )
        app.launch()
        unread(1).onChild().assertTextEquals("2")

        app.network.answer(
            READING,
            200,
            chapters(
                chapter("solo/3", "Chapter 3", 3f),
                chapter("solo/2.5", "Hiatus", 2.5f),
                chapter("solo/2", "Chapter 2", 2f),
                chapter("solo/1.1", "Hiatus", 1.1f),
                chapter("solo/1", "Chapter 1", 1f),
            ),
        )
        pull()
        unread(1).onChild().assertTextEquals("3")
        assertEquals(
            listOf(FoundChapters("Solo Leveling", listOf("Chapter 3", "Chapter 2"))),
            app.found.last(),
        )
        app.tap("tab-updates")
        assertEquals(
            listOf("updates-day-2026-01-01", "update-1:solo/3", "update-1:solo/2"),
            app.childTags("updates"),
        )
    }

    @Test
    fun `a chapter a title's pull adds is listed without a report`() {
        library()
        app.launch()
        app.tap("manga-1")
        app.network.answer(
            READING,
            200,
            chapters(chapter("solo/2", "Chapter 2", 2f), chapter("solo/1", "Chapter 1", 1f)),
        )
        app.node("title-content").performTouchInput { swipeDown(top + height / 4, bottom) }
        app.awaitIdle()

        assertEquals(listOf(emptyList<FoundChapters>()), app.found)
        app.tap("tab-updates")
        app.has("update-1:solo/2")
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
