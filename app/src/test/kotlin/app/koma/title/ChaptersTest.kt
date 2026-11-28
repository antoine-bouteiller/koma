package app.koma.title

import androidx.compose.ui.semantics.SemanticsProperties.StateDescription
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import app.koma.source.CatalogSource
import app.koma.source.details
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
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val FIRST = "$TEST_SOURCE/search?q=Solo%20Leveling"
private const val SECOND = "$TEST_SOURCE/search?q=Na%20Honjaman%20Level%20Up"

@RunWith(RobolectricTestRunner::class)
class ChaptersTest {
    @get:Rule val app = AppHarness()

    /** Solo Leveling, also titled Na Honjaman Level Up, in the library, with [sources]. */
    private fun solo(vararg sources: Pair<String, Long>, progress: Number? = null) {
        app.signedIn()
        app.network.answer(
            CHANGES,
            200,
            changes("c1", false, upsert(1, "reading", progress = progress)),
        )
        app.network.answer(BATCH, 200, batch(series(1, "Solo Leveling")))
        app.network.answer(
            "$SERIES/1?schema=full",
            200,
            fullSeries(1, "Solo Leveling", alternateTitle = "Na Honjaman Level Up"),
        )
        app.sources = { sources.map { (name, id) -> CatalogSource(TestSource(name, id)) } }
    }

    private fun open() {
        app.tap("manga-1")
    }

    /** The source the title is bound to. */
    private fun boundSource() =
        runBlocking { app.container.sources.resolve(details("Solo Leveling")) }?.source

    private fun shows(tag: String, vararg text: String) {
        app.node("title-content").performScrollToNode(hasTestTag(tag))
        app.node(tag).assertTextEquals(*text)
    }

    @Test
    fun `a title binds to the source hit matching one of its titles and lists its chapters`() {
        solo("Test Source" to 77)
        app.network.answer(FIRST, 200, mangas("a" to "Solo Leveling: Ragnarok"))
        app.network.answer(SECOND, 200, mangas("b" to "NA HONJAMAN — LÉVEL UP!"))
        app.network.answer(
            "$TEST_SOURCE/manga/b",
            200,
            chapters(chapter("b/2", "Chapter 2", 2f, "Team Z"), chapter("b/1", "Chapter 1", 1f)),
        )
        app.launch()
        open()

        shows("chapter-b/2", "Chapter 2", "Team Z")
        shows("chapter-b/1", "Chapter 1")
        app.missing("chapters-change")
    }

    @Test
    fun `a title no hit matches lists the candidates, and picking one binds it`() {
        solo("Test Source" to 77)
        app.network.answer(FIRST, 200, mangas("a" to "Solo Leveling: Ragnarok"))
        app.network.answer(SECOND, 200, mangas())
        app.network.answer("$TEST_SOURCE/manga/a", 200, chapters(chapter("a/1", "Prologue", 1f)))
        app.launch()
        open()

        shows("pick-77-a", "Solo Leveling: Ragnarok", "Test Source")
        app.missing("chapters-keep")

        app.tap("pick-77-a")
        shows("chapter-a/1", "Prologue")
    }

    @Test
    fun `a title lists its stored chapters again without asking the source`() {
        solo("Test Source" to 77)
        app.network.answer(FIRST, 200, mangas("a" to "Solo Leveling"))
        app.network.answer("$TEST_SOURCE/manga/a", 200, chapters(chapter("a/1", "Prologue", 1f)))
        app.launch()
        open()
        shows("chapter-a/1", "Prologue")

        // The title is still open: Android restores it.
        app.reload()

        shows("chapter-a/1", "Prologue")
        assertEquals(1, app.network.requested(FIRST).size)
        assertEquals(1, app.network.requested("$TEST_SOURCE/manga/a").size)
    }

    @Test
    fun `pulling a title down lists its chapters again from the source`() {
        solo("Test Source" to 77)
        app.network.answer(FIRST, 200, mangas("a" to "Solo Leveling"))
        app.network.answer("$TEST_SOURCE/manga/a", 200, chapters(chapter("a/1", "Prologue", 1f)))
        app.launch()
        open()
        app.network.answer(
            "$TEST_SOURCE/manga/a",
            200,
            chapters(chapter("a/2", "Chapter 2", 2f), chapter("a/1", "Prologue", 1f)),
        )

        app.node("title-content").performTouchInput { swipeDown(top + height / 4, bottom) }
        app.awaitIdle()

        shows("chapter-a/2", "Chapter 2")
        assertEquals(2, app.network.requested("$TEST_SOURCE/manga/a").size)
    }

    @Test
    fun `a failed search offers to try again`() {
        solo("Test Source" to 77)
        app.network.answer(FIRST, 500, "{}")
        app.launch()
        open()

        app.node("title-content").performScrollToNode(hasTestTag("chapters-retry"))
        app.network.answer(FIRST, 200, mangas("a" to "Solo Leveling"))
        app.network.answer("$TEST_SOURCE/manga/a", 200, chapters(chapter("a/1", "Prologue", 1f)))
        app.tap("chapters-retry")

        shows("chapter-a/1", "Prologue")
    }

    @Test
    fun `a failed chapter list offers to try again`() {
        solo("Test Source" to 77)
        app.network.answer(FIRST, 200, mangas("a" to "Solo Leveling"))
        app.network.answer("$TEST_SOURCE/manga/a", 500, "{}")
        app.launch()
        open()

        app.node("title-content").performScrollToNode(hasTestTag("chapters-retry"))
        app.network.answer("$TEST_SOURCE/manga/a", 200, chapters(chapter("a/1", "Prologue", 1f)))
        app.tap("chapters-retry")

        shows("chapter-a/1", "Prologue")
        assertEquals(1, app.network.requested(FIRST).size)
    }

    @Test
    fun `a bound title with no chapters says so`() {
        solo("Test Source" to 77)
        app.network.answer(FIRST, 200, mangas("a" to "Solo Leveling"))
        app.network.answer("$TEST_SOURCE/manga/a", 200, chapters())
        app.launch()
        open()

        shows("chapters-empty", "No chapters yet.")
    }

    @Test
    fun `with two sources the bound one can be changed, or kept`() {
        solo("Test Source" to 77, "Other Source" to 78)
        app.network.answer(FIRST, 200, mangas("a" to "Solo Leveling"))
        app.network.answer("$TEST_SOURCE/manga/a", 200, chapters(chapter("a/1", "Prologue", 1f)))
        app.launch()
        open()
        shows("chapter-a/1", "Prologue")
        assertEquals(77L, boundSource())

        app.tap("chapters-change")
        shows("pick-78-a", "Solo Leveling", "Other Source")
        app.tap("chapters-keep")
        shows("chapter-a/1", "Prologue")
        assertEquals(77L, boundSource())

        app.tap("chapters-change")
        app.node("title-content").performScrollToNode(hasTestTag("pick-78-a"))
        app.tap("pick-78-a")
        shows("chapter-a/1", "Prologue")
        assertEquals(78L, boundSource())
    }

    @Test
    fun `chapters the user's progress reaches are greyed out as read`() {
        solo("Test Source" to 77, progress = 2)
        app.network.answer(FIRST, 200, mangas("a" to "Solo Leveling"))
        app.network.answer(
            "$TEST_SOURCE/manga/a",
            200,
            chapters(
                chapter("a/3", "Chapter 3", 3f),
                chapter("a/2.5", "Chapter 2.5", 2.5f),
                chapter("a/2", "Chapter 2", 2f),
                chapter("a/1", "Chapter 1", 1f),
                chapter("a/extra", "Extra", -1f),
            ),
        )
        app.launch()
        open()

        for (key in listOf("a/2", "a/1")) {
            app.node("title-content").performScrollToNode(hasTestTag("chapter-$key"))
            app.node("chapter-$key").assert(hasStateDescription("Read"))
        }
        for (key in listOf("a/3", "a/2.5", "a/extra")) {
            app.node("title-content").performScrollToNode(hasTestTag("chapter-$key"))
            app.node("chapter-$key").assert(SemanticsMatcher.keyNotDefined(StateDescription))
        }
    }

    @Test
    fun `a title shows no chapters while Koma lists no source`() {
        solo()
        app.launch()
        open()
        app.text("title-name", "Solo Leveling")
        app.missing("chapters-header")
        app.missing("chapters-loading")
    }
}
