package app.koma.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import com.materialkolor.dynamiccolor.DynamicScheme
import com.materialkolor.hct.Hct
import com.materialkolor.quantize.QuantizerCelebi
import com.materialkolor.scheme.SchemeContent
import com.materialkolor.score.Score

/** The colors [settings] call for. [wallpaper] is Material You's scheme, null before Android 12. */
fun colorScheme(settings: ThemeSettings, dark: Boolean, wallpaper: ColorScheme?): ColorScheme {
    val style = settings.effectiveStyle(dynamicColor = wallpaper != null)
    val baseline = if (dark) darkColorScheme() else lightColorScheme()
    var scheme =
        when {
            style == ColorStyle.MaterialYou && wallpaper != null -> wallpaper
            style == ColorStyle.Monochrome ->
                baseline.withAccents(0f, 0f, dark).withNeutrals { (_, _, l) -> hsl(0f, 0f, l) }
            else -> baseline
        }
    val accent = settings.accent
    if (accent != null && style != ColorStyle.MaterialYou) {
        val (hue, saturation) = hslOf(Color(accent))
        scheme = scheme.withAccents(hue, saturation, dark)
        // Monochrome keeps its grey surfaces under an accent.
        if (style != ColorStyle.Monochrome) {
            scheme = scheme.withNeutrals { (_, s, l) -> hsl(hue, s, l) }
        }
    }
    if (dark && settings.trueBlack) scheme = scheme.trueBlack()
    return scheme
}

/**
 * The color of [pixels], ARGB, best to draw a scheme from, or null when none is colorful enough.
 */
fun seedColor(pixels: IntArray): Int? =
    Score.score(QuantizerCelebi.quantize(pixels, 128), desired = 1, fallbackColorArgb = null)
        .firstOrNull()

/**
 * Colors drawn from [seed], an ARGB, under [settings]; null for Monochrome, which stays grey, and
 * with cover colors off.
 */
fun seededColorScheme(settings: ThemeSettings, dark: Boolean, seed: Int): ColorScheme? {
    if (settings.style == ColorStyle.Monochrome || !settings.coverColors) return null
    val scheme = SchemeContent(Hct.fromInt(seed), dark, contrastLevel = 0.0).toColorScheme()
    return if (dark && settings.trueBlack) scheme.trueBlack() else scheme
}

// Every role is set, so the base scheme only lends the type.
private fun DynamicScheme.toColorScheme(): ColorScheme =
    lightColorScheme()
        .copy(
            primary = Color(primary),
            onPrimary = Color(onPrimary),
            primaryContainer = Color(primaryContainer),
            onPrimaryContainer = Color(onPrimaryContainer),
            inversePrimary = Color(inversePrimary),
            secondary = Color(secondary),
            onSecondary = Color(onSecondary),
            secondaryContainer = Color(secondaryContainer),
            onSecondaryContainer = Color(onSecondaryContainer),
            tertiary = Color(tertiary),
            onTertiary = Color(onTertiary),
            tertiaryContainer = Color(tertiaryContainer),
            onTertiaryContainer = Color(onTertiaryContainer),
            background = Color(background),
            onBackground = Color(onBackground),
            surface = Color(surface),
            onSurface = Color(onSurface),
            surfaceVariant = Color(surfaceVariant),
            onSurfaceVariant = Color(onSurfaceVariant),
            surfaceTint = Color(surfaceTint),
            inverseSurface = Color(inverseSurface),
            inverseOnSurface = Color(inverseOnSurface),
            error = Color(error),
            onError = Color(onError),
            errorContainer = Color(errorContainer),
            onErrorContainer = Color(onErrorContainer),
            outline = Color(outline),
            outlineVariant = Color(outlineVariant),
            scrim = Color(scrim),
            surfaceBright = Color(surfaceBright),
            surfaceDim = Color(surfaceDim),
            surfaceContainer = Color(surfaceContainer),
            surfaceContainerHigh = Color(surfaceContainerHigh),
            surfaceContainerHighest = Color(surfaceContainerHighest),
            surfaceContainerLow = Color(surfaceContainerLow),
            surfaceContainerLowest = Color(surfaceContainerLowest),
            primaryFixed = Color(primaryFixed),
            primaryFixedDim = Color(primaryFixedDim),
            onPrimaryFixed = Color(onPrimaryFixed),
            onPrimaryFixedVariant = Color(onPrimaryFixedVariant),
            secondaryFixed = Color(secondaryFixed),
            secondaryFixedDim = Color(secondaryFixedDim),
            onSecondaryFixed = Color(onSecondaryFixed),
            onSecondaryFixedVariant = Color(onSecondaryFixedVariant),
            tertiaryFixed = Color(tertiaryFixed),
            tertiaryFixedDim = Color(tertiaryFixedDim),
            onTertiaryFixed = Color(onTertiaryFixed),
            onTertiaryFixedVariant = Color(onTertiaryFixedVariant),
        )

/** Black background, with the containers on it darkened yet still apart from it. */
private fun ColorScheme.trueBlack(): ColorScheme {
    fun Color.darker() = hslOf(this).let { (h, s, l) -> hsl(h, s, l * 0.5f) }
    return copy(
        background = Color.Black,
        surface = Color.Black,
        surfaceDim = Color.Black,
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = surfaceContainerLow.darker(),
        surfaceContainer = surfaceContainer.darker(),
        surfaceContainerHigh = surfaceContainerHigh.darker(),
        surfaceContainerHighest = surfaceContainerHighest.darker(),
        surfaceBright = surfaceBright.darker(),
        surfaceVariant = surfaceVariant.darker(),
    )
}

/** Primary, secondary and tertiary roles from one hue, at the lightness each role takes. */
private fun ColorScheme.withAccents(hue: Float, saturation: Float, dark: Boolean): ColorScheme {
    fun tone(light: Float, darkTone: Float, s: Float = saturation, h: Float = hue) =
        hsl(h, s, if (dark) darkTone else light)
    val muted = saturation * 0.35f
    val third = (hue + 60f) % 360f
    val tertiarySaturation = saturation * 0.6f
    return copy(
        primary = tone(0.40f, 0.78f),
        onPrimary = tone(0.98f, 0.16f),
        primaryContainer = tone(0.88f, 0.30f),
        onPrimaryContainer = tone(0.12f, 0.90f),
        inversePrimary = tone(0.78f, 0.40f),
        surfaceTint = tone(0.40f, 0.78f),
        secondary = tone(0.40f, 0.78f, muted),
        onSecondary = tone(0.98f, 0.16f, muted),
        secondaryContainer = tone(0.88f, 0.30f, muted),
        onSecondaryContainer = tone(0.12f, 0.90f, muted),
        tertiary = tone(0.40f, 0.78f, tertiarySaturation, third),
        onTertiary = tone(0.98f, 0.16f, tertiarySaturation, third),
        tertiaryContainer = tone(0.88f, 0.30f, tertiarySaturation, third),
        onTertiaryContainer = tone(0.12f, 0.90f, tertiarySaturation, third),
    )
}

/** Every surface, outline and text-on-surface color passed through [change], given its HSL. */
private fun ColorScheme.withNeutrals(change: (FloatArray) -> Color): ColorScheme {
    fun Color.map() = change(hslOf(this))
    return copy(
        background = background.map(),
        onBackground = onBackground.map(),
        surface = surface.map(),
        onSurface = onSurface.map(),
        surfaceVariant = surfaceVariant.map(),
        onSurfaceVariant = onSurfaceVariant.map(),
        inverseSurface = inverseSurface.map(),
        inverseOnSurface = inverseOnSurface.map(),
        outline = outline.map(),
        outlineVariant = outlineVariant.map(),
        surfaceBright = surfaceBright.map(),
        surfaceDim = surfaceDim.map(),
        surfaceContainer = surfaceContainer.map(),
        surfaceContainerHigh = surfaceContainerHigh.map(),
        surfaceContainerHighest = surfaceContainerHighest.map(),
        surfaceContainerLow = surfaceContainerLow.map(),
        surfaceContainerLowest = surfaceContainerLowest.map(),
    )
}

private fun hslOf(color: Color): FloatArray =
    FloatArray(3).also { ColorUtils.colorToHSL(color.toArgb(), it) }

private fun hsl(hue: Float, saturation: Float, lightness: Float): Color =
    Color(ColorUtils.HSLToColor(floatArrayOf(hue, saturation, lightness)))
