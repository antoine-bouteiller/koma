package app.koma.update

import app.koma.testing.AppHarness
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val LATEST = "https://api.github.com/repos/antoine-bouteiller/koma/releases/latest"

private fun release(version: String) =
    """{"tag_name":"v$version","assets":[{"name":"koma.apk","browser_download_url":"https://github.com/antoine-bouteiller/koma/releases/download/v$version/koma.apk"}]}"""

@RunWith(RobolectricTestRunner::class)
class UpdateTest {
    @get:Rule val app = AppHarness()

    @Test
    fun `a newer release offers its APK`() {
        app.network.answer(LATEST, 200, release("2.0.0"))
        app.launch()
        app.text("update-text", "Koma 2.0.0 is available.")
        app.tap("update-now")
        assertEquals(
            listOf("https://github.com/antoine-bouteiller/koma/releases/download/v2.0.0/koma.apk"),
            app.opened,
        )
    }

    @Test
    fun `the installed release shows no banner`() {
        app.network.answer(LATEST, 200, release("0.1.0"))
        app.launch()
        app.has("tabs")
        app.missing("update-banner")
    }
}
