package app.koma.testing

import app.koma.Permissions

/** Grants nothing until a test says so, and records the settings the app opened. */
class FakePermissions : Permissions {
    var notifications = false
    var batteryOptimizationOff = false
    val opened = mutableListOf<String>()

    override fun notificationsEnabled() = notifications

    override fun batteryOptimizationDisabled() = batteryOptimizationOff

    override fun enableNotifications() {
        opened += "notifications"
    }

    override fun disableBatteryOptimization() {
        opened += "battery"
    }
}
