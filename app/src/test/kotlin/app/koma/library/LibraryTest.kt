package app.koma.library

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Dp
import app.koma.testing.AppHarness
import app.koma.testing.BATCH
import app.koma.testing.CHANGES
import app.koma.testing.batch
import app.koma.testing.changes
import app.koma.testing.delete
import app.koma.testing.series
import app.koma.testing.upsert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LibraryTest {
    @get:Rule val app = AppHarness()

    private fun longShelf() {
        app.signedIn()
        val ids = 1..30
        app.network.answer(
            "$CHANGES?limit=1000",
            200,
            changes(
                "c1",
                false,
                *ids.map { upsert(it, "reading", "2026-01-01T00:00:00Z") }.toTypedArray(),
            ),
        )
        app.network.answer(BATCH, 200, batch(*ids.map { series(it, "Title $it") }.toTypedArray()))
        app.launch()
    }

    private fun barTop(): Dp = app.node("tabs").getUnclippedBoundsInRoot().top

    private fun rootBottom(): Dp = app.node("root").getUnclippedBoundsInRoot().bottom

    @Test
    fun `the shelves' tabs sit in the header, above the grid`() {
        longShelf()
        val header = app.node("header").getUnclippedBoundsInRoot()
        val shelves = app.node("shelves").getUnclippedBoundsInRoot()
        val first = app.node("manga-1").getUnclippedBoundsInRoot()
        assertTrue("${shelves.bottom} is in ${header.bottom}", shelves.bottom <= header.bottom)
        assertTrue("${first.top} is under ${header.bottom}", first.top >= header.bottom)
    }

    @Test
    fun `scrolling down hides the tab bar and the shelves' tabs, and scrolling up brings them back`() {
        longShelf()
        assertTrue(barTop() < rootBottom())
        app.node("library-reading").performTouchInput {
            swipeUp(centerY + height / 4, centerY - height / 4)
        }
        app.awaitIdle()
        assertEquals(rootBottom(), barTop())
        app.node("shelves").assertIsNotDisplayed()
        app.node("library-reading").performTouchInput {
            swipeDown(centerY - height / 4, centerY + height / 4)
        }
        app.awaitIdle()
        assertTrue(barTop() < rootBottom())
        app.node("shelves").assertIsDisplayed()
    }

    @Test
    fun `the library reads every page of the changes feed, sorts each shelf and leaves novels out`() {
        app.signedIn()
        app.network.answer(
            "$CHANGES?limit=1000",
            200,
            changes(
                "p2",
                true,
                upsert(1, "reading", "2026-01-01T00:00:00Z"),
                upsert(2, "plan_to_read", "2026-01-03T00:00:00Z"),
                upsert(3, "completed", "2026-01-02T00:00:00Z"),
            ),
        )
        app.network.answer(
            "$CHANGES?limit=1000&cursor=p2",
            200,
            changes(
                "c1",
                false,
                upsert(4, "reading", "2026-01-04T00:00:00Z"),
                upsert(5, "rereading", "2026-01-05T00:00:00Z"),
                delete(1),
            ),
        )
        app.network.answer(
            BATCH,
            200,
            batch(
                series(2, "Berserk", "manga"),
                series(3, "Vagabond", "manga"),
                series(4, "Monster", "manga"),
                series(5, "Overlord", "novel"),
            ),
        )
        app.launch()
        assertEquals(listOf("manga-4", "manga-2"), app.childTags("library-reading"))
        assertEquals(listOf("manga-3"), app.childTags("library-archived"))
        app.text("manga-title-4", "Monster")
        app.missing("manga-1")
        app.missing("manga-5")
    }

    @Test
    fun `a later sync asks only for what changed and looks up only new series`() {
        app.signedIn()
        app.network.answer(
            "$CHANGES?limit=1000",
            200,
            changes(
                "c1",
                false,
                upsert(1, "reading", "2026-01-01T00:00:00Z"),
                upsert(2, "reading", "2026-01-02T00:00:00Z"),
            ),
        )
        app.network.answer(BATCH, 200, batch(series(1, "Berserk"), series(2, "Vagabond")))
        app.launch()
        app.text("manga-title-2", "Vagabond")
        app.network.fail("$CHANGES?limit=1000")
        app.network.fail(BATCH)
        app.network.answer(
            "$CHANGES?limit=1000&cursor=c1",
            200,
            changes(
                "c2",
                false,
                upsert(7, "reading", "2026-01-03T00:00:00Z"),
                delete(1),
            ),
        )
        app.network.answer("$BATCH?id=7", 200, batch(series(7, "Pluto")))
        app.reload()
        app.text("manga-title-7", "Pluto")
        app.text("manga-title-2", "Vagabond")
        app.missing("manga-1")
    }

    @Test
    fun `a refused library cursor reads the whole library again`() {
        app.signedIn()
        app.network.answer(
            "$CHANGES?limit=1000",
            200,
            changes("c1", false, upsert(1, "reading", "2026-01-01T00:00:00Z")),
        )
        app.network.answer(BATCH, 200, batch(series(1, "Berserk"), series(2, "Vagabond")))
        app.launch()
        app.text("manga-title-1", "Berserk")
        app.network.answer(
            "$CHANGES?limit=1000&cursor=c1",
            400,
            """{"status":400,"message":"Invalid cursor: bootstrap again without one"}""",
        )
        app.network.answer(
            "$CHANGES?limit=1000",
            200,
            changes("c2", false, upsert(2, "reading", "2026-01-02T00:00:00Z")),
        )
        app.reload()
        app.text("manga-title-2", "Vagabond")
        app.missing("manga-1")
    }

    @Test
    fun `the library opens offline with what was stored`() {
        app.signedIn()
        app.network.answer(CHANGES, 200, changes("c1", false, upsert(1, "reading")))
        app.network.answer(BATCH, 200, batch(series(1, "Berserk")))
        app.launch()
        app.text("manga-title-1", "Berserk")
        app.network.fail(CHANGES)
        app.network.fail(BATCH)
        app.reload()
        app.text("manga-title-1", "Berserk")
        app.missing("library-retry")
    }

    @Test
    fun `a library MangaBaka never answered offers to try again`() {
        app.signedIn()
        app.network.fail(CHANGES)
        app.launch()
        app.has("library-retry")
        app.network.answer(CHANGES, 200, changes("c0", false))
        app.tap("library-retry")
        app.has("empty-library")
    }

    @Test
    fun `an empty MangaBaka library says what will show up there`() {
        app.signedIn()
        app.network.answer(CHANGES, 200, changes("c0", false))
        app.launch()
        app.has("empty-library")
        app.missing("signed-out")
    }

    @Test
    fun `a change of an unknown kind is skipped`() {
        app.signedIn()
        app.network.answer(
            CHANGES,
            200,
            changes(
                "c1",
                false,
                """{"type":"entry","action":"archive","revision":2,"current":{}}""",
                upsert(1, "reading"),
            ),
        )
        app.network.answer(BATCH, 200, batch(series(1, "Berserk")))
        app.launch()
        app.text("manga-title-1", "Berserk")
    }

    @Test
    fun `the selected shelf survives a visit to another tab`() {
        app.signedIn()
        app.network.answer(
            CHANGES,
            200,
            changes("c1", false, upsert(1, "reading"), upsert(5, "completed", revision = 5)),
        )
        app.network.answer(BATCH, 200, batch(series(1, "Berserk"), series(5, "Pluto")))
        app.launch()
        app.tap("shelf-archived")
        app.tap("tab-updates")
        app.tap("tab-library")
        app.selected("shelf-archived")
    }

    @Test
    fun `the archived shelf lists completed, paused and dropped titles`() {
        app.signedIn()
        app.network.answer(
            CHANGES,
            200,
            changes(
                "c1",
                false,
                upsert(1, "reading", "2026-01-01T00:00:00Z"),
                upsert(5, "completed", "2026-01-01T00:00:00Z", revision = 5),
            ),
        )
        app.network.answer(BATCH, 200, batch(series(1, "Berserk"), series(5, "Pluto")))
        app.launch()
        app.selected("shelf-reading")
        app.text("manga-title-5", "Pluto")
        app.tap("shelf-archived")
        app.selected("shelf-archived")
        app.tap("shelf-reading")
        app.selected("shelf-reading")
    }
}
