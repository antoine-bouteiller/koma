package app.koma

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import app.koma.testing.AppHarness
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppTest {
    @get:Rule val app = AppHarness()

    @Test
    fun `the app opens on the Library tab`() {
        app.launch()
        app.selected("tab-library")
        app.text("title-library", "Library")
    }

    @Test
    fun `each tab opens its own screen`() {
        app.launch()
        app.tap("tab-updates")
        app.selected("tab-updates")
        app.text("title-updates", "Updates")
        app.tap("tab-history")
        app.selected("tab-history")
        app.text("title-history", "History")
        app.tap("tab-discover")
        app.selected("tab-discover")
        app.text("title-discover", "Discover")
        app.tap("tab-settings")
        app.selected("tab-settings")
        app.text("title-settings", "Settings")
        app.tap("tab-library")
        app.selected("tab-library")
    }

    @Test
    fun `back from another tab opens the Library`() {
        app.launch()
        app.tap("tab-history")
        app.back()
        app.selected("tab-library")
        app.text("title-library", "Library")
    }

    @Test
    fun `back from a Settings page opens Settings, then the Library`() {
        app.launch()
        app.tap("tab-settings")
        app.tap("settings-theme")
        app.back()
        app.text("title-settings", "Settings")
        app.back()
        app.selected("tab-library")
    }

    @Test
    fun `back from the Library closes the app`() {
        app.launch()
        assertTrue(app.backClosesApp)
    }

    @Test
    fun `an empty tab says what will show up there`() {
        app.launch()
        app.tap("tab-discover")
        app.has("empty-discover")
    }

    @Test
    fun `a screen's content starts below the header floating over it`() {
        app.launch()
        app.tap("tab-settings")
        val header = app.node("header").getUnclippedBoundsInRoot()
        val first = app.node("settings-theme").getUnclippedBoundsInRoot()
        assertTrue("${first.top} is under ${header.bottom}", first.top >= header.bottom)
    }
}
