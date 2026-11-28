package app.koma.settings

import androidx.lifecycle.ViewModel
import app.koma.Permissions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PermissionsViewModel(private val permissions: Permissions) : ViewModel() {
    private val state = MutableStateFlow(read())
    val page: StateFlow<PermissionsPage> = state.asStateFlow()

    /** Reads the grants again, which Android's settings may have changed. */
    fun refresh() {
        state.value = read()
    }

    fun enableNotifications() = permissions.enableNotifications()

    fun disableBatteryOptimization() = permissions.disableBatteryOptimization()

    private fun read() =
        PermissionsPage(
            notificationsEnabled = permissions.notificationsEnabled(),
            batteryOptimizationDisabled = permissions.batteryOptimizationDisabled(),
        )
}
