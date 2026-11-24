package app.koma.ui.theme

import app.koma.PreferenceStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet

enum class ThemeMode {
    System,
    Light,
    Dark,
}

enum class ColorStyle {
    Default,
    MaterialYou,
    Monochrome,
}

data class ThemeSettings(
    val mode: ThemeMode = ThemeMode.System,
    val style: ColorStyle = ColorStyle.MaterialYou,
    /** Black backgrounds in dark mode. */
    val trueBlack: Boolean = false,
    /** ARGB of the accent picked over the style's own colors, or null. */
    val accent: Int? = null,
)

/**
 * The style in effect: without [dynamicColor], Android 12's, Material You falls back to Default.
 */
fun ThemeSettings.effectiveStyle(dynamicColor: Boolean): ColorStyle =
    if (style == ColorStyle.MaterialYou && !dynamicColor) ColorStyle.Default else style

private const val MODE = "theme.mode"
private const val STYLE = "theme.style"
private const val TRUE_BLACK = "theme.trueBlack"
private const val ACCENT = "theme.accent"

class ThemePreferences(private val store: PreferenceStore) {
    private val current = MutableStateFlow(read())

    val settings: StateFlow<ThemeSettings> = current.asStateFlow()

    fun update(change: (ThemeSettings) -> ThemeSettings) {
        val next = current.updateAndGet(change)
        store.set(MODE, next.mode.name)
        store.set(STYLE, next.style.name)
        store.set(TRUE_BLACK, next.trueBlack.toString())
        store.set(ACCENT, next.accent?.let { "%08X".format(it) } ?: "")
    }

    private fun read(): ThemeSettings {
        val default = ThemeSettings()
        return ThemeSettings(
            mode = ThemeMode.entries.find { it.name == store.get(MODE) } ?: default.mode,
            style = ColorStyle.entries.find { it.name == store.get(STYLE) } ?: default.style,
            trueBlack = store.get(TRUE_BLACK) == "true",
            accent = store.get(ACCENT)?.toLongOrNull(16)?.toInt(),
        )
    }
}
