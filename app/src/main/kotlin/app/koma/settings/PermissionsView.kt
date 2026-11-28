package app.koma.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.koma.AppContainer
import app.koma.R
import app.koma.ui.Chevron
import app.koma.ui.GroupRow
import app.koma.ui.SettingsGroup
import app.koma.ui.SettingsRow

@Composable
internal fun PermissionsView(container: AppContainer) {
    val model = viewModel { PermissionsViewModel(container.permissions) }
    val page by model.page.collectAsStateWithLifecycle()
    // Back from Android's settings, a grant may have changed.
    LifecycleResumeEffect(model) {
        model.refresh()
        onPauseOrDispose {}
    }
    SettingsGroup(
        listOf(
            GroupRow { shape ->
                SettingsRow(
                    title = "Enable notifications",
                    icon = R.drawable.ic_notifications,
                    shape = shape,
                    tag = "permission-notifications",
                    subtitle = notificationsSummary(page.notificationsEnabled),
                    onClick = model::enableNotifications,
                    enabled = !page.notificationsEnabled,
                    trailing = { if (!page.notificationsEnabled) Chevron() },
                )
            },
            GroupRow { shape ->
                SettingsRow(
                    title = "Disable battery optimization",
                    icon = R.drawable.ic_battery,
                    shape = shape,
                    tag = "permission-battery",
                    subtitle = batteryOptimizationSummary(page.batteryOptimizationDisabled),
                    onClick = model::disableBatteryOptimization,
                    enabled = !page.batteryOptimizationDisabled,
                    trailing = { if (!page.batteryOptimizationDisabled) Chevron() },
                )
            },
        )
    )
}
