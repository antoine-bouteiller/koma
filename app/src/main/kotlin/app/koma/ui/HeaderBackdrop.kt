package app.koma.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * What a screen's content hands its header: its scroll, which lays the header's band down, the
 * accessory the header shows under the title, the action it shows at its end, and the colors of
 * all.
 */
@Stable
class HeaderBackdrop {
    private val sources = mutableStateListOf<ScrollableState>()

    /** What the screen shows under the title, if anything. */
    var accessory by mutableStateOf<(@Composable () -> Unit)?>(null)
        private set

    /** What the screen shows at the header's end, if anything. */
    var action by mutableStateOf<(@Composable () -> Unit)?>(null)
        private set

    /** Whether the screen picks its own colors. */
    var tinted by mutableStateOf(false)
        internal set

    /** The colors of the screen, header included, or null for the app's. */
    var colors by mutableStateOf<ColorScheme?>(null)
        internal set

    /** How far the content runs on under the header; none while no scroll is tracked. */
    val scrolledPast: Float
        get() = sources.maxOfOrNull { it.scrolledPast() } ?: 0f

    /** The scrolls in view, which change as a shelf swipes in or another screen opens. */
    internal val tracked: List<ScrollableState>
        get() = sources.toList()

    internal fun track(state: ScrollableState) {
        sources.add(state)
    }

    internal fun release(state: ScrollableState) {
        sources.remove(state)
    }

    internal fun show(slot: @Composable () -> Unit) {
        accessory = slot
    }

    internal fun remove(slot: @Composable () -> Unit) {
        if (accessory === slot) accessory = null
    }

    internal fun showAction(slot: @Composable () -> Unit) {
        action = slot
    }

    internal fun removeAction(slot: @Composable () -> Unit) {
        if (action === slot) action = null
    }
}

// A lazy layout measures only the items in view, so one out of sight counts as far.
private fun ScrollableState.scrolledPast(): Float =
    when (this) {
        is ScrollState -> value.toFloat()
        is LazyListState ->
            if (firstVisibleItemIndex > 0) Float.MAX_VALUE
            else firstVisibleItemScrollOffset.toFloat()
        is LazyGridState ->
            if (firstVisibleItemIndex > 0) Float.MAX_VALUE
            else firstVisibleItemScrollOffset.toFloat()
        else -> if (canScrollBackward) Float.MAX_VALUE else 0f
    }

/** The backdrop of the screen this is composed in. */
val LocalHeaderBackdrop = staticCompositionLocalOf<HeaderBackdrop?> { null }

/**
 * Hands [state], a scroll in view, to the screen header floating over it. The band follows the
 * furthest of the scrolls tracked at once.
 */
@Composable
fun TrackHeaderBackdrop(state: ScrollableState) {
    val backdrop = LocalHeaderBackdrop.current ?: return
    DisposableEffect(backdrop, state) {
        backdrop.track(state)
        onDispose { backdrop.release(state) }
    }
}

/**
 * Shows [content] in the screen header, under the title, while this is composed. It enters and
 * leaves with its screen. The header's band covers it, and it slides away under the title as the
 * content scrolls down. Answers its height, which the screen's content starts below.
 */
@Composable
fun headerAccessory(content: @Composable () -> Unit): Dp {
    val backdrop = LocalHeaderBackdrop.current ?: return 0.dp
    val current by rememberUpdatedState(content)
    // Saved, so the content starts in place when its screen comes back, before the header shows it.
    var height by rememberSaveable { mutableIntStateOf(0) }
    DisposableEffect(backdrop) {
        val slot: @Composable () -> Unit = {
            Box(Modifier.onSizeChanged { height = it.height }) { current() }
        }
        backdrop.show(slot)
        onDispose { backdrop.remove(slot) }
    }
    return with(LocalDensity.current) { height.toDp() }
}

/** Shows [content] at the end of the screen header while this is composed. */
@Composable
fun HeaderAction(content: @Composable () -> Unit) {
    val backdrop = LocalHeaderBackdrop.current ?: return
    val current by rememberUpdatedState(content)
    DisposableEffect(backdrop) {
        val slot: @Composable () -> Unit = { current() }
        backdrop.showAction(slot)
        onDispose { backdrop.removeAction(slot) }
    }
}

/** Shows [content] once its colors also apply to the floating header. */
@Composable
fun ScreenColors(
    colors: ColorScheme?,
    waiting: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val backdrop = LocalHeaderBackdrop.current
    if (backdrop == null) {
        MaterialTheme(colorScheme = colors ?: MaterialTheme.colorScheme, content = content)
        return
    }
    SideEffect {
        backdrop.tinted = true
        backdrop.colors = colors
    }
    DisposableEffect(backdrop) {
        onDispose {
            backdrop.tinted = false
            backdrop.colors = null
        }
    }
    if (colors == null || MaterialTheme.colorScheme === colors) content() else waiting()
}
