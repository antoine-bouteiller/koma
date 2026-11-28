package app.koma.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.koma.data.AutoUpdatePreferences
import app.koma.data.UpdateInterval
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class AutoUpdateViewModel(private val preferences: AutoUpdatePreferences) : ViewModel() {
    val page: StateFlow<AutoUpdatePage> =
        preferences.settings
            .map(::autoUpdatePage)
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                autoUpdatePage(preferences.settings.value),
            )

    fun setInterval(interval: UpdateInterval) = preferences.update { it.copy(interval = interval) }

    fun setWifiOnly(on: Boolean) = preferences.update { it.copy(wifiOnly = on) }

    fun setCharging(on: Boolean) = preferences.update { it.copy(charging = on) }

    fun setBatteryNotLow(on: Boolean) = preferences.update { it.copy(batteryNotLow = on) }
}
