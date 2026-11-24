package app.koma.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/** One tab of [NavigationBar]. */
interface TabItem {
    val title: String
    @get:DrawableRes val icon: Int
    /** The filled icon; for [IconMotion.Fill], the animated vector filling [icon] in. */
    @get:DrawableRes val selectedIcon: Int
    val motion: IconMotion
    val tag: String
}

/** How a tab's icon moves as the tab gets selected. */
enum class IconMotion {
    /** Fills in as the tab gets selected, and empties again as it gets deselected. */
    Fill,
    /** Swings from its top, as a bell rings. */
    Ring,
    /** Winds back a full turn. */
    Rewind,
    /** Turns by one tooth of a six-toothed gear. */
    Turn;

    /** The icon's pose at [t], from 0 to 1; both ends are its resting pose. */
    internal fun GraphicsLayerScope.pose(t: Float) {
        when (this@IconMotion) {
            Fill -> Unit
            Ring -> {
                transformOrigin = TransformOrigin(0.5f, 0.1f)
                rotationZ = 22f * sin(4 * PI * t).toFloat() * (1 - t)
            }
            Rewind -> rotationZ = -360f * FastOutSlowInEasing.transform(t)
            Turn -> rotationZ = 60f * backOut(t)
        }
    }
}

private fun backOut(t: Float): Float {
    val overshoot = 1.70158f
    val u = t - 1
    return 1 + (overshoot + 1) * u * u * u + overshoot * u * u
}

/**
 * The bar of the app's tabs, along the bottom edge over the content. It slides down out of sight as
 * [hidden] goes from 0 to 1.
 */
@Composable
fun <T : TabItem> NavigationBar(
    tabs: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    hidden: () -> Float,
    modifier: Modifier = Modifier,
) {
    val edge = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    ShortNavigationBar(
        modifier =
            modifier
                .layout { measurable, constraints ->
                    val bar = measurable.measure(constraints)
                    layout(bar.width, bar.height) {
                        bar.place(0, (bar.height * hidden()).roundToInt())
                    }
                }
                .drawWithContent {
                    drawContent()
                    drawLine(edge, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx())
                }
                .testTag("tabs")
    ) {
        for (tab in tabs) {
            ShortNavigationBarItem(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                icon = { TabIcon(tab, tab == selected) },
                label = { Text(tab.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                modifier = Modifier.testTag(tab.tag),
            )
        }
    }
}

/** [tab]'s icon, filled while [selected], playing its motion each time it gets selected. */
@Composable
private fun TabIcon(tab: TabItem, selected: Boolean) {
    if (tab.motion == IconMotion.Fill) {
        val image = AnimatedImageVector.animatedVectorResource(tab.selectedIcon)
        Icon(rememberAnimatedVectorPainter(image, atEnd = selected), contentDescription = null)
        return
    }
    val progress = remember { Animatable(1f) }
    val was = remember { booleanArrayOf(selected) }
    LaunchedEffect(selected) {
        val play = selected && !was[0]
        was[0] = selected
        if (play) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(IconMotionMillis, easing = LinearEasing))
        }
    }
    Icon(
        painterResource(if (selected) tab.selectedIcon else tab.icon),
        contentDescription = null,
        modifier = Modifier.graphicsLayer { with(tab.motion) { pose(progress.value) } },
    )
}

private const val IconMotionMillis = 600
