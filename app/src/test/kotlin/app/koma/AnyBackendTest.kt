package app.koma

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithText
import app.koma.data.ListStatus
import app.koma.data.ReadEvent
import app.koma.data.Tag
import app.koma.testing.AppHarness
import app.koma.testing.FakeBackend
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AnyBackendTest {
    @get:Rule val app = AppHarness()

    private val backend = FakeBackend("Shelfie")
    private val alpha =
        backend.add(
            "α-1",
            "Alpha",
            score = 91,
            tags =
                listOf(
                    Tag("Low", genre = false, spoiler = false, rank = 10),
                    Tag("High", genre = false, spoiler = false, rank = 90),
                    Tag("Unranked", genre = false, spoiler = false, rank = null),
                    Tag("Drama", genre = true, spoiler = false, rank = null),
                ),
        )
    private val beta = backend.add("β-2", "Beta")

    private fun launch() {
        app.backend = backend
        app.launch()
    }

    @Test
    fun `the library shelves the backend's statuses`() {
        backend.file(alpha, ListStatus.Planning)
        backend.file(beta, ListStatus.Dropped)
        launch()
        assertEquals(listOf("manga-α-1"), app.childTags("library-reading"))
        app.tap("shelf-archived")
        assertEquals(listOf("manga-β-2"), app.childTags("library-archived"))
    }

    @Test
    fun `an empty library names the backend`() {
        launch()
        app.compose.onNodeWithText("The titles you track on Shelfie show up here.").assertExists()
    }

    @Test
    fun `signed out, the library asks to sign in to the backend`() {
        app.backend = FakeBackend("Shelfie", signedIn = false)
        app.launch()
        app.compose.onNodeWithText("Sign in to Shelfie").assertExists()
    }

    @Test
    fun `the history shows the backend's reads under its titles`() {
        backend.reads +=
            ReadEvent("r-1", alpha, Instant.parse("2026-01-01T10:00:00Z").toEpochMilli(), 3.0, 4.0)
        launch()
        app.tap("tab-history")
        app.text("history-title-r-1", "Alpha")
        app.text("history-chapters-r-1", "Ch. 4 · 10:00 AM")
    }

    @Test
    fun `the title screen shows the backend's facts and files the title there`() {
        backend.file(alpha, ListStatus.Planning)
        launch()
        app.tap("manga-α-1")
        app.text("title-rating", "91")
        assertEquals(
            listOf("tag-Drama", "tag-High", "tag-Low", "tag-Unranked"),
            app.childTags("title-tags"),
        )
        app.tap("title-status")
        app.tap("status-completed")
        assertEquals("updateEntry(α-1, Completed)", backend.calls.last())

        // Removed elsewhere while the title screen stays open.
        backend.drop(alpha)
        runBlocking { app.container.library.sync() }
        app.awaitIdle()
        app.tap("title-status")
        app.tap("status-reading")
        assertEquals("addEntry(α-1, Reading)", backend.calls.last())
        app.text("title-status", "Reading")
    }

    @Test
    fun `settings name the backend and its user`() {
        launch()
        app.tap("tab-settings")
        app.node("settings-account").assertTextEquals("Shelfie", "Signed in as shelf-reader")
        app.tap("settings-account")
        app.text("title-account", "Shelfie")
        app.text("account-name-title", "shelf-reader")
        app.compose.onNodeWithText("Signed in to Shelfie").assertExists()
    }
}
