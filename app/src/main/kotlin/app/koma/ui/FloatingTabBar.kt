package app.koma.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.animateIntSizeAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur

/** One tab of [FloatingTabBar]. */
interface TabItem {
    val title: String
    @get:DrawableRes val icon: Int
    val tag: String
}

/**
 * A glass pill floating over the content marked as the [haze] source, which it blurs. Its indicator
 * slides to the selected tab and takes its width.
 */
@Composable
fun <T : TabItem> FloatingTabBar(
    tabs: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    haze: HazeState,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val dark = colors.surface.luminance() < 0.5f
    val glass =
        colors.surfaceVariant.copy(alpha = if (dark) 0.35f else 0.6f).compositeOver(colors.surface)
    val glassStyle =
        remember(glass, colors.surface) {
            HazeBlurStyle {
                backgroundColor(colors.surface)
                colorEffects(listOf(HazeColorEffect.tint(glass.copy(alpha = 0.55f))))
                fallbackColorEffect(HazeColorEffect.tint(glass))
            }
        }
    val bounds = remember { mutableStateMapOf<T, Pair<IntOffset, IntSize>>() }
    Box(
        modifier
            .widthIn(max = PillInset * 2 + SlotMaxWidth * tabs.size)
            .fillMaxWidth()
            .height(PillHeight)
            .clip(CircleShape)
            .hazeBlur(HazeInput.Sources(haze), glassStyle)
            .border(1.dp, colors.outline.copy(alpha = if (dark) 0.4f else 0.6f), CircleShape)
            .padding(horizontal = PillInset)
            .testTag("tabs"),
        contentAlignment = Alignment.CenterStart,
    ) {
        bounds[selected]?.let { (offset, size) ->
            val x by animateIntOffsetAsState(offset, MaterialTheme.motionScheme.fastSpatialSpec())
            val width by animateIntSizeAsState(size, MaterialTheme.motionScheme.fastSpatialSpec())
            with(LocalDensity.current) {
                Box(
                    Modifier.offset { x }
                        .size(width.width.toDp(), width.height.toDp())
                        .background(
                            colors.primaryContainer.copy(alpha = if (dark) 0.55f else 0.72f),
                            CircleShape,
                        )
                        .border(
                            1.dp,
                            colors.primary.copy(alpha = if (dark) 0.55f else 0.45f),
                            CircleShape,
                        )
                )
            }
        }
        Row(Modifier.selectableGroup(), verticalAlignment = Alignment.CenterVertically) {
            for (tab in tabs) {
                Tab(
                    tab,
                    tab == selected,
                    onClick = { onSelect(tab) },
                    Modifier.onPlaced { bounds[tab] = it.positionInParent().round() to it.size },
                )
            }
        }
    }
}

@Composable
private fun RowScope.Tab(
    tab: TabItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by
        animateFloatAsState(
            if (pressed) 0.97f else 1f,
            MaterialTheme.motionScheme.fastSpatialSpec(),
        )
    val color: Color =
        if (selected) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier
            .weight(1f)
            .height(SlotHeight)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .selectable(
                selected,
                interactionSource = source,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .testTag(tab.tag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painterResource(tab.icon),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(22.dp),
        )
        Text(
            tab.title,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

private val PillHeight = 64.dp
private val PillInset = 6.dp
private val SlotHeight = 52.dp
private val SlotMaxWidth = 96.dp
