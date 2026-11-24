package app.koma.update

import app.koma.testing.AppHarness
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val LATEST = "https://api.github.com/repos/antoine-bouteiller/koma/releases/latest"
private const val APK =
    "https://github.com/antoine-bouteiller/koma/releases/download/v2.0.0/koma.apk"

private fun release(version: String) =
    """{"tag_name":"v$version","assets":[{"name":"koma.apk","browser_download_url":"https://github.com/antoine-bouteiller/koma/releases/download/v$version/koma.apk"}]}"""

@RunWith(RobolectricTestRunner::class)
class UpdateTest {
    @get:Rule val app = AppHarness()

    @Test
    fun `a newer release is downloaded on request and handed to the installer`() {
        app.network.answer(LATEST, 200, release("2.0.0"))
        app.network.answer(APK, 200, "the APK")
        app.launch()
        app.text("update-pill", "Update")
        app.missing("update-dialog")

        app.tap("update-pill")
        app.text("update-title", "Update available")
        app.text("update-subtitle", "Koma 2.0.0")
        app.text("update-action", "Download")
        app.tap("update-action")
        assertEquals(listOf("the APK"), app.installed.map { it.readText() })

        // A dismissed installer opens again without a second download.
        app.text("update-pill", "Install")
        app.text("update-title", "Ready to install")
        app.tap("update-action")
        assertEquals(2, app.installed.size)
        assertEquals(1, app.network.requested(APK).size)
    }

    @Test
    fun `a failed download offers a retry`() {
        app.network.answer(LATEST, 200, release("2.0.0"))
        app.network.answer(APK, 500, "")
        app.launch()
        app.tap("update-pill")
        app.tap("update-action")
        app.text("update-error", "The download failed. Check the connection and try again.")
        assertEquals(emptyList<Any>(), app.installed)

        app.network.answer(APK, 200, "the APK")
        app.text("update-action", "Retry")
        app.tap("update-action")
        assertEquals(listOf("the APK"), app.installed.map { it.readText() })
    }

    @Test
    fun `later closes the dialog and keeps the pill`() {
        app.network.answer(LATEST, 200, release("2.0.0"))
        app.launch()
        app.tap("update-pill")
        app.text("update-dismiss", "Later")
        app.tap("update-dismiss")
        app.missing("update-dialog")
        app.has("update-pill")
    }

    @Test
    fun `the installed release shows no pill`() {
        app.network.answer(LATEST, 200, release("0.1.0"))
        app.launch()
        app.has("tabs")
        app.missing("update-pill")
    }
}
