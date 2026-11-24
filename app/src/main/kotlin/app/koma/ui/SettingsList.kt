package app.koma.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.koma.R

/** A titled part of a settings page. */
@Composable
fun SettingsSection(
    title: String,
    @DrawableRes icon: Int,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp).semantics {
                heading()
            },
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                Icon(
                    painterResource(icon),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        content()
    }
}

/**
 * Rows that belong together, as one segmented card: rounded on the group's outer edges, nearly
 * square where two rows meet. Each row draws itself in the shape it is handed.
 */
@Composable
fun SettingsGroup(rows: List<@Composable (Shape) -> Unit>) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        rows.forEachIndexed { index, row -> row(ConnectedShapes.Card.inColumn(index, rows.size)) }
    }
}

/** The surface of a settings row, or of a card standing on its own. */
@Composable
fun SettingsCard(
    shape: Shape,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier.fillMaxWidth(),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        content = content,
    )
}

/** A row of a [SettingsGroup]: an icon, a title over a subtitle, and a [trailing] control. */
@Composable
fun SettingsRow(
    title: String,
    @DrawableRes icon: Int,
    shape: Shape,
    tag: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    /** Replaces the icon's and the title's colors, as the error color marks a destructive row. */
    color: Color? = null,
    trailing: @Composable () -> Unit = { if (onClick != null) Chevron() },
) {
    SettingsCard(
        shape,
        modifier
            .clip(shape)
            .then(
                if (onClick != null)
                    Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                else Modifier
            )
            .testTag(tag),
    ) {
        Row(
            Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val alpha = if (enabled) 1f else 0.38f
            Icon(
                painterResource(icon),
                contentDescription = null,
                tint = (color ?: MaterialTheme.colorScheme.primary).copy(alpha = alpha),
                modifier = Modifier.size(24.dp),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = (color ?: MaterialTheme.colorScheme.onSurface).copy(alpha = alpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("$tag-title"),
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                    )
                }
            }
            trailing()
        }
    }
}

/** A [SettingsRow] that toggles [checked] wherever it is tapped. */
@Composable
fun SettingsSwitchRow(
    title: String,
    @DrawableRes icon: Int,
    shape: Shape,
    tag: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
) {
    SettingsRow(
        title = title,
        icon = icon,
        shape = shape,
        tag = tag,
        subtitle = subtitle,
        modifier =
            Modifier.clip(shape)
                .toggleable(checked, role = Role.Switch, onValueChange = onCheckedChange),
    ) {
        Switch(
            checked = checked,
            onCheckedChange = null,
            thumbContent = {
                Icon(
                    painterResource(if (checked) R.drawable.ic_check else R.drawable.ic_close),
                    contentDescription = null,
                    modifier = Modifier.size(SwitchDefaults.IconSize),
                )
            },
        )
    }
}

@Composable
private fun Chevron() {
    Icon(
        painterResource(R.drawable.ic_chevron_right),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(24.dp),
    )
}
