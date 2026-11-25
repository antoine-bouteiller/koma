package app.koma.title

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performSemanticsAction
import app.koma.data.ListStatus
import app.koma.testing.AppHarness
import app.koma.testing.BATCH
import app.koma.testing.CHANGES
import app.koma.testing.FakeBackend
import app.koma.testing.LIBRARY
import app.koma.testing.PROFILE
import app.koma.testing.SERIES
import app.koma.testing.batch
import app.koma.testing.changes
import app.koma.testing.fullSeries
import app.koma.testing.profile
import app.koma.testing.series
import app.koma.testing.tag
import app.koma.testing.upsert
import app.koma.ui.theme.ColorStyle
import app.koma.ui.theme.ThemeMode
import app.koma.ui.theme.ThemeSettings
import app.koma.ui.theme.colorScheme
import app.koma.ui.theme.seededColorScheme
import coil3.asImage
import coil3.decode.DataSource
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
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
                tag("Action", "defining", genre = true),
            ),
        )
        app.launch()
        app.tap("manga-1")
        app.text("title-name", "Solo Leveling")
        app.text("title-authors", "Chu-Gong, Jang Sung-Lak")
        app.text("title-publication", "Completed")
        app.text("title-year", "2018")
        app.text("title-rating", "4.3")
        app.text("title-status", "Reading")
        app.text("title-summary", "Jinwoo levels up.")
        assertEquals(
            listOf("tag-Action", "tag-Dungeon"),
            app.childTags("title-tags"),
        )
        val request = app.network.requests.last { it.url.startsWith(SERIES) }
        assertNull(request.headers["Authorization"])

        app.tap("back")
        app.has("library-reading")
    }

    @Test
    fun `a title waits for its details before showing its content`() {
        val backend = FakeBackend()
        val ref = backend.add("alpha", "Alpha")
        backend.file(ref, ListStatus.Reading)
        val details = backend.hold("details")
        app.backend = backend
        app.launch()
        app.tap("manga-alpha")
        app.has("title-loading")
        app.missing("title-name")
        app.missing("title-status")

        app.compose.runOnIdle { details.complete(Unit) }
        app.awaitIdle()
        app.text("title-name", "Alpha")
        app.missing("title-loading")
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test
    fun `a title waits for its cover colors and reveals its final palette`() {
        library(upsert(1, "reading"))
        app.network.answer("$SERIES/1?schema=full", 200, fullSeries(1, "Solo Leveling"))
        app.launch()
        val colors = CompletableDeferred<Unit>()
        val argb = 0xFF43A047.toInt()
        app.image = { request ->
            colors.await()
            solidCover(request, argb)
        }
        app.tap("manga-1")
        app.has("title-loading")
        app.missing("title-name")
        app.missing("title-status")

        app.compose.mainClock.autoAdvance = false
        app.compose.runOnIdle { colors.complete(Unit) }
        app.compose.waitUntil(timeoutMillis = 5_000) {
            app.compose.mainClock.advanceTimeByFrame()
            app.compose.onAllNodesWithTag("title-status").fetchSemanticsNodes().isNotEmpty()
        }
        val expected = seededColorScheme(ThemeSettings(), dark = false, argb)!!.secondaryContainer
        assertEquals(expected, statusColor())
        app.compose.mainClock.autoAdvance = true
        app.awaitIdle()
        assertEquals(expected, statusColor())
        app.text("title-name", "Solo Leveling")
        app.missing("title-loading")
    }

    @Test
    fun `a title is named as in the library, not by an alternate title`() {
        library(upsert(1, "reading"))
        app.network.answer(
            "$SERIES/1?schema=full",
            200,
            fullSeries(1, "Solo Leveling", alternateTitle = "Only I Level Up"),
        )
        app.launch()
        app.text("manga-1", "Solo Leveling")
        app.tap("manga-1")
        app.text("title-name", "Solo Leveling")
    }

    @Test
    fun `a new status is saved on MangaBaka and files the title on its shelf`() {
        library(upsert(1, "reading"), upsert(2, "reading", "2026-01-02T00:00:00Z"))
        app.network.answer("$SERIES/1?schema=full", 200, fullSeries(1, "Solo Leveling"))
        app.network.answer("$LIBRARY/1", 200, """{"status":200,"data":true}""")
        app.launch()
        app.tap("manga-1")
        app.tap("title-status")
        app.node("status-reading").assertIsSelected()
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
    fun `a title stays open on the Library while another tab shows, and outlives a restart`() {
        library(upsert(1, "reading"))
        app.network.answer("$SERIES/1?schema=full", 200, fullSeries(1, "Solo Leveling"))
        app.launch()
        app.tap("manga-1")
        app.tap("tab-updates")
        app.tap("tab-library")
        app.text("title-name", "Solo Leveling")
        app.reload()
        app.selected("tab-library")
        app.text("title-name", "Solo Leveling")
        app.back()
        app.has("library-reading")
    }

    @Test
    fun `back from another tab opens the Library's open title`() {
        library(upsert(1, "reading"))
        app.network.answer("$SERIES/1?schema=full", 200, fullSeries(1, "Solo Leveling"))
        app.launch()
        app.tap("manga-1")
        app.tap("tab-updates")
        app.back()
        app.selected("tab-library")
        app.text("title-name", "Solo Leveling")
    }

    @Test
    fun `signing out closes the title open on the Library`() {
        library(upsert(1, "reading"))
        app.stubAuthServer()
        app.network.answer(PROFILE, 200, profile("reader"))
        app.network.answer("$SERIES/1?schema=full", 200, fullSeries(1, "Solo Leveling"))
        app.launch()
        app.tap("manga-1")
        app.tap("tab-settings")
        app.tap("settings-account")
        app.tap("sign-out")
        app.tap("tab-library")
        app.has("sign-in")
        app.missing("title-name")
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

    private fun rate(score: Float) {
        app.node("rating-gauge").performSemanticsAction(SemanticsActions.SetProgress) {
            it(score)
        }
        app.awaitIdle()
    }

    @Test
    fun `a rating set on the gauge is saved on MangaBaka`() {
        library(upsert(1, "reading", rating = 87))
        app.network.answer("$SERIES/1?schema=full", 200, fullSeries(1, "Solo Leveling"))
        app.network.answer("$LIBRARY/1", 200, """{"status":200,"data":true}""")
        app.launch()
        app.tap("manga-1")
        app.text("title-score", "4.4")
        app.tap("title-score")
        app.text("rating-score", "4.4")
        app.node("rating-save").assertIsNotEnabled()
        rate(3.4f)
        app.text("rating-score", "3.5")
        app.text("rating-word", "Liked it")
        app.tap("rating-save")
        app.text("title-score", "3.5")
        val write = app.network.requests.single { it.url == "$LIBRARY/1" }
        assertEquals("PATCH" to """{"rating":70}""", write.method to write.body)
    }

    @Test
    fun `a rating taken back to 0 is cleared`() {
        library(upsert(1, "reading", rating = 90))
        app.network.answer("$SERIES/1?schema=full", 200, fullSeries(1, "Solo Leveling"))
        app.network.answer("$LIBRARY/1", 200, """{"status":200,"data":true}""")
        app.launch()
        app.tap("manga-1")
        app.tap("title-score")
        rate(0f)
        app.text("rating-word", "Not rated")
        app.tap("rating-save")
        app.text("title-score", "Rate")
        assertEquals("""{"rating":0}""", app.network.requests.last().body)
    }

    @Test
    fun `a rating MangaBaka refuses is not kept`() {
        library(upsert(1, "reading"))
        app.network.answer("$SERIES/1?schema=full", 200, fullSeries(1, "Solo Leveling"))
        app.network.answer("$LIBRARY/1", 500, """{"status":500}""")
        app.launch()
        app.tap("manga-1")
        app.text("title-score", "Rate")
        app.tap("title-score")
        rate(5f)
        app.text("rating-word", "Loved it")
        app.tap("rating-save")
        app.text("title-status-error", "MangaBaka did not save the rating. Try again.")
        app.text("title-score", "Rate")
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

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test
    fun `cached details open offline and a failed cover sample can be retried`() {
        library(upsert(1, "reading"))
        app.network.answer(
            "$SERIES/1?schema=full",
            200,
            fullSeries(1, "Solo Leveling", authors = listOf("Chu-Gong")),
        )
        app.launch()
        app.tap("manga-1")
        app.text("title-authors", "Chu-Gong")
        app.network.fail("$SERIES/1?schema=full")
        val argb = 0xFF43A047.toInt()
        app.image = { request -> solidCover(request, argb) }
        val expected = seededColorScheme(ThemeSettings(), dark = false, argb)!!.secondaryContainer
        app.tap("back")
        app.tap("manga-1")
        app.text("title-authors", "Chu-Gong")
        assertEquals(expected, statusColor())
        app.reload()
        app.text("title-authors", "Chu-Gong")
        assertEquals(expected, statusColor())
        assertEquals(1, app.network.requested("$SERIES/1?schema=full").size)
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test
    fun `colorful and grey covers reuse their colors after reopening and restarting`() {
        val backend = FakeBackend()
        val green = backend.add("green", "Green")
        val grey = backend.add("grey", "Grey")
        backend.file(green, ListStatus.Reading)
        backend.file(grey, ListStatus.Reading)
        app.backend = backend
        app.preferences.set("theme.mode", "Light")
        app.preferences.set("theme.style", "Default")
        app.launch()
        val argb = 0xFF43A047.toInt()
        app.image = { request ->
            solidCover(
                request,
                if (request.data.toString().contains("green")) argb else 0xFF808080.toInt(),
            )
        }
        val settings = ThemeSettings(mode = ThemeMode.Light, style = ColorStyle.Default)
        val containers =
            mapOf(
                "green" to seededColorScheme(settings, dark = false, argb)!!.secondaryContainer,
                "grey" to colorScheme(settings, dark = false, wallpaper = null).secondaryContainer,
            )
        for ((id, expected) in containers) {
            app.tap("manga-$id")
            assertEquals(expected, statusColor())
            app.tap("back")
        }

        val unavailable = CompletableDeferred<Unit>()
        val missingImage = app.image
        app.image = { request ->
            unavailable.await()
            missingImage(request)
        }
        for ((id, expected) in containers) {
            app.tap("manga-$id")
            app.has("title-name")
            app.missing("title-loading")
            assertEquals(expected, statusColor())
            app.reload()
            app.has("title-name")
            app.missing("title-loading")
            assertEquals(expected, statusColor())
            app.tap("back")
        }
        app.compose.runOnIdle { unavailable.complete(Unit) }
        app.awaitIdle()
    }

    private fun solidCover(request: ImageRequest, color: Int): SuccessResult {
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        return SuccessResult(bitmap.asImage(), request, DataSource.MEMORY_CACHE)
    }

    private fun statusColor(): Color {
        val pixels = app.node("title-status").captureToImage().toPixelMap()
        return pixels[pixels.width / 2, 2]
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

    @Test
    fun `a status the backend refuses syncs the library, so filing it again updates it`() {
        val backend = FakeBackend()
        val gamma = backend.add("γ-3", "Gamma")
        backend.file(gamma, ListStatus.Reading)
        app.backend = backend
        app.launch()
        app.tap("manga-γ-3")
        // Removed, then filed again elsewhere, while the title screen stays open.
        backend.drop(gamma)
        runBlocking { app.container.library.sync() }
        app.awaitIdle()
        app.text("title-status", "Add to Library")
        app.missing("title-score")
        backend.file(gamma, ListStatus.Paused)
        backend.calls.clear()

        app.tap("title-status")
        app.tap("status-completed")
        app.has("title-status-error")
        assertEquals(listOf("addEntry(γ-3, Completed)", "changes(c2)"), backend.calls)
        app.text("title-status", "Paused")

        app.tap("title-status")
        app.tap("status-completed")
        assertEquals("updateEntry(γ-3, Completed)", backend.calls.last())
        app.text("title-status", "Completed")
        app.missing("title-status-error")
    }
}
