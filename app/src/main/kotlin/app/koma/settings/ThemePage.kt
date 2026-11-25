package app.koma.settings

import app.koma.ui.theme.ColorStyle
import app.koma.ui.theme.ThemeMode
import app.koma.ui.theme.ThemeSettings
import app.koma.ui.theme.effectiveStyle

/** What the Theme page shows: the choices on offer, those picked, and which rows apply. */
data class ThemePage(
    val mode: ThemeMode,
    val styles: List<ColorStyle>,
    val style: ColorStyle,
    /** Material You takes its accent from the wallpaper, leaving none to pick. */
    val accentShown: Boolean,
    val accent: Int?,
    val trueBlackShown: Boolean,
    val trueBlack: Boolean,
    /** Monochrome keeps titles grey whatever their cover. */
    val coverColorsShown: Boolean,
    val coverColors: Boolean,
)

fun themePage(settings: ThemeSettings, dynamicColor: Boolean): ThemePage {
    val style = settings.effectiveStyle(dynamicColor)
    return ThemePage(
        mode = settings.mode,
        styles = ColorStyle.entries.filter { it != ColorStyle.MaterialYou || dynamicColor },
        style = style,
        accentShown = style != ColorStyle.MaterialYou,
        accent = settings.accent,
        trueBlackShown = settings.mode != ThemeMode.Light,
        trueBlack = settings.trueBlack,
        coverColorsShown = style != ColorStyle.Monochrome,
        coverColors = settings.coverColors,
    )
}
