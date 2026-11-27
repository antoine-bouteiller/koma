package app.koma.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import com.materialkolor.hct.Hct
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun `true black darkens containers and pills but keeps them off the background`() {
        val dark = colorScheme(ThemeSettings(style = ColorStyle.Default), dark = true, null)
        val black =
            colorScheme(ThemeSettings(style = ColorStyle.Default, trueBlack = true), true, null)
        for (role in
            listOf<(ColorScheme) -> Color>({ it.surfaceContainer }, { it.surfaceVariant })) {
            assertTrue(role(black).luminance() < role(dark).luminance())
            assertNotEquals(Color.Black, role(black))
        }
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
        assertEquals(Hct.fromInt(green.toArgb()).hue, Hct.fromInt(primary.toArgb()).hue, 1.0)
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

    @Test
    fun `a cover seeds its scheme with its colorful part, not its grey`() {
        val grey = Color(0xFF808080).toArgb()
        val pixels = IntArray(100) { if (it < 20) green.toArgb() else grey }
        val seed = seedColor(pixels)!!
        val scheme = seededColorScheme(ThemeSettings(), dark = true, seed)!!
        assertEquals(hue(green), hue(scheme.primaryContainer), 10f)
    }

    @Test
    fun `a grey cover seeds nothing`() {
        assertNull(seedColor(IntArray(100) { Color(0xFF808080).toArgb() }))
    }

    @Test
    fun `a cover leaves monochrome grey`() {
        assertNull(
            seededColorScheme(ThemeSettings(style = ColorStyle.Monochrome), false, green.toArgb())
        )
    }

    @Test
    fun `with cover colors off, a cover keeps the app's colors`() {
        assertNull(seededColorScheme(ThemeSettings(coverColors = false), false, green.toArgb()))
    }

    @Test
    fun `true black holds under a cover's colors`() {
        val scheme =
            seededColorScheme(ThemeSettings(trueBlack = true), dark = true, green.toArgb())!!
        assertEquals(Color.Black, scheme.surface)
    }

    @Test
    fun `a cover's tonal buttons keep their text crisp`() {
        for (dark in listOf(false, true)) {
            val scheme = seededColorScheme(ThemeSettings(), dark, green.toArgb())!!
            val contrast =
                ColorUtils.calculateContrast(
                    scheme.onSecondaryContainer.toArgb(),
                    scheme.secondaryContainer.toArgb(),
                )
            assertTrue("dark=$dark: $contrast", contrast >= 7.0)
        }
    }

    @Test
    fun `an accent of any hue keeps text readable on its colors`() {
        for (hue in 0 until 360 step 15) {
            val accent = ColorUtils.HSLToColor(floatArrayOf(hue.toFloat(), 1f, 0.5f))
            for (dark in listOf(false, true)) {
                val scheme = colorScheme(ThemeSettings(accent = accent), dark, null)
                for ((name, pair) in accentPairs(scheme)) {
                    val contrast = ColorUtils.calculateContrast(pair.first, pair.second)
                    assertTrue("hue=$hue dark=$dark $name: $contrast", contrast >= 4.5)
                }
            }
        }
    }

    private fun accentPairs(scheme: ColorScheme): Map<String, Pair<Int, Int>> =
        with(scheme) {
            mapOf(
                    "primary" to (onPrimary to primary),
                    "primaryContainer" to (onPrimaryContainer to primaryContainer),
                    "secondary" to (onSecondary to secondary),
                    "secondaryContainer" to (onSecondaryContainer to secondaryContainer),
                    "tertiary" to (onTertiary to tertiary),
                    "tertiaryContainer" to (onTertiaryContainer to tertiaryContainer),
                )
                .mapValues { (_, it) -> it.first.toArgb() to it.second.toArgb() }
        }

    private fun hue(color: Color): Float =
        FloatArray(3).also { ColorUtils.colorToHSL(color.toArgb(), it) }[0]
}
