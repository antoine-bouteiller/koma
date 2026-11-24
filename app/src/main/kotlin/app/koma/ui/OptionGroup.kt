package app.koma.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

data class Option<T>(val value: T, @DrawableRes val icon: Int, val label: String, val tag: String)

/**
 * A single choice among a few, as one connected group of tiles. A pressed tile widens at its
 * neighbors' expense, and the selected one rounds into a pill.
 */
@Composable
fun <T> OptionGroup(
    options: List<Option<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().height(IntrinsicSize.Min).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { index, option ->
            val source = remember { MutableInteractionSource() }
            val pressed by source.collectIsPressedAsState()
            val width by
                animateFloatAsState(
                    if (pressed) 1.15f else 1f,
                    MaterialTheme.motionScheme.fastSpatialSpec(),
                )
            OptionTile(
                option = option,
                selected = option.value == selected,
                shape = ConnectedShapes.Pill.inRow(index, options.size),
                source = source,
                pressed = pressed,
                onClick = { onSelect(option.value) },
                modifier = Modifier.weight(width).fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun <T> OptionTile(
    option: Option<T>,
    selected: Boolean,
    shape: CornerBasedShape,
    source: MutableInteractionSource,
    pressed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val dark = colors.surface.luminance() < 0.5f
    val outline = morphingShape(if (selected) PillShape else shape, pressed)
    val container =
        if (selected) colors.primaryContainer.copy(alpha = if (dark) 0.55f else 0.72f)
        else colors.surfaceVariant.copy(alpha = if (dark) 0.35f else 0.6f)
    val border =
        if (selected) colors.primary.copy(alpha = if (dark) 0.55f else 0.45f)
        else colors.outline.copy(alpha = if (dark) 0.4f else 0.6f)
    Column(
        modifier
            .heightIn(min = 64.dp)
            .clip(outline)
            .background(container)
            .border(if (selected) 2.dp else 1.dp, border, outline)
            .selectable(
                selected,
                interactionSource = source,
                indication = null,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(12.dp)
            .testTag(option.tag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painterResource(option.icon),
            contentDescription = null,
            tint = if (selected) colors.onPrimaryContainer else colors.primary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            option.label,
            color = if (selected) colors.onPrimaryContainer else colors.onSurface,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
