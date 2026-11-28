package app.koma.settings

/** What the Permissions page shows: which of Koma's background needs Android already grants. */
data class PermissionsPage(
    val notificationsEnabled: Boolean,
    val batteryOptimizationDisabled: Boolean,
)

fun notificationsSummary(enabled: Boolean): String =
    if (enabled) "Already enabled" else "Shows the library update's progress and new chapters"

fun batteryOptimizationSummary(disabled: Boolean): String =
    if (disabled) "Already disabled" else "Keeps the library update running in the background"
