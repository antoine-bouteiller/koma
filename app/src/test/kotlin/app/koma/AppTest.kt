package app.koma

import app.koma.testing.AppHarness
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
        app.tap("tab-library")
        app.selected("tab-library")
    }

    @Test
    fun `an empty tab says what will show up there`() {
        app.launch()
        app.tap("tab-discover")
        app.has("empty-discover")
    }
}
