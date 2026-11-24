package app.koma.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ColorSchemeTest {
    private val wallpaper = darkColorScheme(primary = Color(0xFFFF0000))
    private val green = Color(0xFF43A047)

    @Test
    fun `true black blackens the background in dark mode only`() {
        val settings = ThemeSettings(style = ColorStyle.Default, trueBlack = true)
        assertEquals(Color.Black, colorScheme(settings, dark = true, null).surface)
        assertEquals(Color.Black, colorScheme(settings, dark = true, null).background)
        assertEquals(lightColorScheme().surface, colorScheme(settings, dark = false, null).surface)
    }

    @Test
    fun `Material You takes the wallpaper's colors over an accent`() {
        val settings = ThemeSettings(style = ColorStyle.MaterialYou, accent = green.toArgb())
        assertEquals(wallpaper.primary, colorScheme(settings, dark = true, wallpaper).primary)
    }

    @Test
    fun `an accent sets the primary hue, also where Material You is missing`() {
        val settings = ThemeSettings(style = ColorStyle.MaterialYou, accent = green.toArgb())
        val primary = colorScheme(settings, dark = true, wallpaper = null).primary
        assertEquals(hue(green), hue(primary), 1f)
    }

    @Test
    fun `monochrome is grey`() {
        val scheme = colorScheme(ThemeSettings(style = ColorStyle.Monochrome), false, wallpaper)
        for (color in listOf(scheme.primary, scheme.primaryContainer, scheme.surfaceContainer)) {
            assertEquals(color.red, color.green, 0.01f)
            assertEquals(color.green, color.blue, 0.01f)
        }
        assertNotEquals(scheme.primary, scheme.surface)
    }

    private fun hue(color: Color): Float =
        FloatArray(3).also { ColorUtils.colorToHSL(color.toArgb(), it) }[0]
}
