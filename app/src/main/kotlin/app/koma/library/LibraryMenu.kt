package app.koma.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.koma.AppContainer
import app.koma.R
import app.koma.data.UpdateInterval
import app.koma.ui.GlassIconButton
import app.koma.ui.TitleFont

/** The Library header's menu. */
@Composable
internal fun LibraryMenu(onAutoUpdate: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        GlassIconButton(
            R.drawable.ic_more_vert,
            contentDescription = "Library options",
            onClick = { open = true },
            modifier = Modifier.testTag("library-menu"),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Automatic updates") },
                leadingIcon = { Icon(painterResource(R.drawable.ic_refresh), null) },
                onClick = {
                    open = false
                    onAutoUpdate()
                },
                modifier = Modifier.testTag("library-auto-update"),
            )
        }
    }
}

/** How often the library updates on its own, and what the device must offer first. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AutoUpdateSheet(container: AppContainer, onDismiss: () -> Unit) {
    val model = viewModel { AutoUpdateViewModel(container.autoUpdate) }
    val page by model.page.collectAsStateWithLifecycle()
    val settings = page.settings
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.padding(bottom = 16.dp).testTag("auto-update")) {
            Text(
                "Automatic updates",
                style =
                    MaterialTheme.typography.titleLarge.copy(
                        fontFamily = TitleFont,
                        fontWeight = FontWeight.SemiBold,
                    ),
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
            )
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
            Text(
                "Only update",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp),
            )
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
private fun Choice(label: String, tag: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .selectable(selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp)
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
            .padding(horizontal = 12.dp)
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
