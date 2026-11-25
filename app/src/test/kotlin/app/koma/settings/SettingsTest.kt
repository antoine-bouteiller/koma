package app.koma.settings

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import app.koma.testing.AppHarness
import app.koma.testing.CHANGES
import app.koma.testing.PROFILE
import app.koma.testing.changes
import app.koma.testing.profile
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsTest {
    @get:Rule val app = AppHarness()

    @Test
    fun `the Theme page opens from Settings and leads back`() {
        app.launch()
        app.tap("tab-settings")
        app.tap("settings-theme")
        app.text("title-theme", "Theme")
        app.selected("theme-mode-system")
        app.selected("color-style-material-you")
        app.tap("back")
        app.text("title-settings", "Settings")
        app.has("settings-theme")
    }

    @Test
    fun `theme choices outlive a restart`() {
        app.launch()
        app.tap("tab-settings")
        app.tap("settings-theme")
        app.tap("theme-mode-dark")
        app.tap("color-style-default")
        app.tap("accent-FF43A047")
        app.tap("true-black")
        app.tap("cover-colors")
        app.reload()
        app.selected("theme-mode-dark")
        app.selected("color-style-default")
        app.selected("accent-FF43A047")
        app.node("true-black").assertIsOn()
        app.node("cover-colors").assertIsOff()
    }

    @Test
    fun `true black is offered out of light mode, and an accent off Material You`() {
        app.launch()
        app.tap("tab-settings")
        app.tap("settings-theme")
        app.has("true-black")
        app.missing("accent-none")
        app.tap("theme-mode-light")
        app.missing("true-black")
        app.tap("color-style-monochrome")
        app.selected("accent-none")
    }

    @Test
    fun `signed in, the account page shows the user name`() {
        app.signedIn()
        app.network.answer(CHANGES, 200, changes("c0", false))
        app.network.answer(PROFILE, 200, profile("reader"))
        app.launch()
        app.tap("tab-settings")
        app.node("settings-account").assertTextEquals("MangaBaka", "Signed in as reader")
        app.tap("settings-account")
        app.text("account-name-title", "reader")
        app.has("sign-out")
    }

    @Test
    fun `a user name MangaBaka did not give still shows the session`() {
        app.signedIn()
        app.network.answer(CHANGES, 200, changes("c0", false))
        app.network.answer(PROFILE, 500, """{"status":500}""")
        app.launch()
        app.tap("tab-settings")
        app.node("settings-account").assertTextEquals("MangaBaka", "Signed in")
        app.tap("settings-account")
        app.text("account-name-title", "MangaBaka account")
    }
}
