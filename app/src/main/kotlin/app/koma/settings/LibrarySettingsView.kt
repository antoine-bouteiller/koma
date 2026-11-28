package app.koma.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.koma.AppContainer
import app.koma.data.UpdateInterval
import app.koma.ui.SettingsCard

/** How often the library updates on its own, and what the device must offer first. */
@Composable
internal fun LibrarySettingsView(container: AppContainer) {
    val model = viewModel { AutoUpdateViewModel(container.autoUpdate) }
    val page by model.page.collectAsStateWithLifecycle()
    val settings = page.settings
    SettingsCard(RoundedCornerShape(20.dp), Modifier.testTag("auto-update")) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Heading("Automatic updates")
            Column(Modifier.selectableGroup()) {
                for (interval in UpdateInterval.entries) {
                    Choice(
                        intervalLabel(interval),
                        "auto-update-${interval.name.lowercase()}",
                        selected = settings.interval == interval,
                    ) {
                        model.setInterval(interval)
                    }
                }
            }
            Heading("Only update")
            Restriction(
                "On Wi-Fi",
                "auto-update-wifi",
                settings.wifiOnly,
                page.restrictionsEnabled,
                model::setWifiOnly,
            )
            Restriction(
                "While charging",
                "auto-update-charging",
                settings.charging,
                page.restrictionsEnabled,
                model::setCharging,
            )
            Restriction(
                "When the battery is not low",
                "auto-update-battery",
                settings.batteryNotLow,
                page.restrictionsEnabled,
                model::setBatteryNotLow,
            )
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun Choice(label: String, tag: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .selectable(selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 4.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected, onClick = null, Modifier.padding(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun Restriction(
    label: String,
    tag: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .toggleable(checked, enabled = enabled, role = Role.Checkbox, onValueChange = onChange)
            .padding(horizontal = 4.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked, onCheckedChange = null, Modifier.padding(12.dp), enabled = enabled)
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f),
        )
    }
}
