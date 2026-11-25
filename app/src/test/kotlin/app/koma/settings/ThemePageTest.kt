package app.koma.settings

import app.koma.ui.theme.ColorStyle
import app.koma.ui.theme.ThemeMode
import app.koma.ui.theme.ThemeSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePageTest {
    @Test
    fun `without dynamic color, Material You is not offered and Default stands in`() {
        val page = themePage(ThemeSettings(style = ColorStyle.MaterialYou), dynamicColor = false)
        assertEquals(listOf(ColorStyle.Default, ColorStyle.Monochrome), page.styles)
        assertEquals(ColorStyle.Default, page.style)
        assertTrue(page.accentShown)
    }

    @Test
    fun `light mode hides true black`() {
        assertFalse(themePage(ThemeSettings(mode = ThemeMode.Light), true).trueBlackShown)
        assertTrue(themePage(ThemeSettings(mode = ThemeMode.System), true).trueBlackShown)
    }

    @Test
    fun `monochrome hides cover colors`() {
        assertFalse(themePage(ThemeSettings(style = ColorStyle.Monochrome), true).coverColorsShown)
        assertTrue(themePage(ThemeSettings(style = ColorStyle.Default), true).coverColorsShown)
    }
}
