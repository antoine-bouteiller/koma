package app.koma.settings

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import app.koma.data.AutoUpdate
import app.koma.data.UpdateInterval
import app.koma.testing.AppHarness
import app.koma.testing.CHANGES
import app.koma.testing.changes
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AutoUpdateTest {
    @get:Rule val app = AppHarness()

    @Before
    fun signedIn() {
        app.signedIn()
        app.network.answer(CHANGES, 200, changes("c1", false))
    }

    private fun openPage() {
        app.tap("tab-settings")
        app.tap("settings-library")
        app.text("title-library-settings", "Library")
    }

    @Test
    fun `automatic updates start off, with their restrictions disabled`() {
        app.launch()
        openPage()
        app.selected("auto-update-off")
        app.node("auto-update-wifi").assertIsNotEnabled().assertIsOn()
        app.node("auto-update-charging").assertIsNotEnabled().assertIsOff()
        app.node("auto-update-battery").assertIsNotEnabled().assertIsOff()
        assertEquals(emptyList<AutoUpdate>(), app.scheduled)
    }

    @Test
    fun `each change reschedules the update, and the choice outlives a restart`() {
        app.launch()
        openPage()
        app.tap("auto-update-daily")
        app.node("auto-update-charging").assertIsEnabled()
        app.tap("auto-update-charging")
        app.tap("auto-update-wifi")
        val daily = AutoUpdate(UpdateInterval.Daily, wifiOnly = true)
        assertEquals(
            listOf(
                daily,
                daily.copy(charging = true),
                daily.copy(charging = true, wifiOnly = false),
            ),
            app.scheduled,
        )

        app.reload()
        app.has("auto-update")
        app.selected("auto-update-daily")
        app.node("auto-update-wifi").assertIsOff()
        app.node("auto-update-charging").assertIsOn()

        app.tap("auto-update-off")
        assertEquals(UpdateInterval.Off, app.scheduled.last().interval)
        app.node("auto-update-charging").assertIsNotEnabled()
    }
}
