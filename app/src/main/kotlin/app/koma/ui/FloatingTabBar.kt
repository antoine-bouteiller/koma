package app.koma.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.animateIntSizeAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

/** One tab of [FloatingTabBar]. */
interface TabItem {
    val title: String
    @get:DrawableRes val icon: Int
    val tag: String
}

/**
 * A pill floating over the content, whose indicator slides to the selected tab and takes its width.
 */
@Composable
fun <T : TabItem> FloatingTabBar(
    tabs: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bounds = remember { mutableStateMapOf<T, Pair<IntOffset, IntSize>>() }
    Surface(
        modifier = modifier.testTag("tabs"),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
    ) {
        Box(Modifier.padding(6.dp)) {
            bounds[selected]?.let { (offset, size) ->
                val x by
                    animateIntOffsetAsState(offset, MaterialTheme.motionScheme.fastSpatialSpec())
                val width by
                    animateIntSizeAsState(size, MaterialTheme.motionScheme.fastSpatialSpec())
                with(LocalDensity.current) {
                    Box(
                        Modifier.offset { x }
                            .size(width.width.toDp(), width.height.toDp())
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                    )
                }
            }
            Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for (tab in tabs) {
                    val isSelected = tab == selected
                    val color =
                        if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    Column(
                        Modifier.onPlaced { bounds[tab] = it.positionInParent().round() to it.size }
                            .clip(CircleShape)
                            .selectable(isSelected, role = Role.Tab, onClick = { onSelect(tab) })
                            .testTag(tab.tag)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(painterResource(tab.icon), contentDescription = null, tint = color)
                        Text(
                            tab.title,
                            color = color,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
