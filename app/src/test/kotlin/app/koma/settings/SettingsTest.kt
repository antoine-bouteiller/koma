package app.koma.settings

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import app.koma.testing.AppHarness
import app.koma.testing.CHANGES
import app.koma.testing.PROFILE
import app.koma.testing.changes
import app.koma.testing.profile
import org.junit.Assert.assertEquals
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

    @Test
    fun `the Permissions page opens the settings of each permission not yet granted`() {
        app.launch()
        app.tap("tab-settings")
        app.tap("settings-permissions")
        app.text("title-permissions", "Permissions")
        app.node("permission-notifications").assertIsEnabled()
        app.tap("permission-notifications")
        app.tap("permission-battery")
        assertEquals(listOf("notifications", "battery"), app.permissions.opened)
    }

    @Test
    fun `a granted permission says so and opens nothing`() {
        app.permissions.notifications = true
        app.permissions.batteryOptimizationOff = true
        app.launch()
        app.tap("tab-settings")
        app.tap("settings-permissions")
        app.node("permission-notifications")
            .assertIsNotEnabled()
            .assertTextEquals("Enable notifications", "Already enabled")
        app.node("permission-battery")
            .assertIsNotEnabled()
            .assertTextEquals("Disable battery optimization", "Already disabled")
        app.tap("permission-notifications")
        app.tap("permission-battery")
        assertEquals(emptyList<String>(), app.permissions.opened)
    }
}
