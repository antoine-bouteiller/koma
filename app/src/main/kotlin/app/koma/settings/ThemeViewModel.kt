package app.koma.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.koma.ui.theme.ColorStyle
import app.koma.ui.theme.ThemeMode
import app.koma.ui.theme.ThemePreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class ThemeViewModel(private val theme: ThemePreferences, dynamicColor: Boolean) : ViewModel() {
    val page: StateFlow<ThemePage> =
        theme.settings
            .map { themePage(it, dynamicColor) }
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                themePage(theme.settings.value, dynamicColor),
            )

    fun setMode(mode: ThemeMode) = theme.update { it.copy(mode = mode) }

    fun setStyle(style: ColorStyle) = theme.update { it.copy(style = style) }

    fun setAccent(accent: Int?) = theme.update { it.copy(accent = accent) }

    fun setTrueBlack(on: Boolean) = theme.update { it.copy(trueBlack = on) }
}
