package app.koma.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
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
 * What the content under the screen header hands it: its scroll, which lays the header's band down,
 * and each screen's accessory the header shows under the title.
 */
@Stable
class HeaderBackdrop {
    private val sources = mutableStateListOf<ScrollableState>()
    private val accessories = mutableStateMapOf<String, @Composable () -> Unit>()

    /** What the screen keyed [screen] shows under the title, if anything. */
    fun accessory(screen: String): (@Composable () -> Unit)? = accessories[screen]

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

    internal fun show(screen: String, slot: @Composable () -> Unit) {
        accessories[screen] = slot
    }

    internal fun remove(screen: String, slot: @Composable () -> Unit) {
        if (accessories[screen] === slot) accessories.remove(screen)
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

/** The backdrop of the screen shown; null on a screen leaving, which hands the header no scroll. */
val LocalHeaderBackdrop = staticCompositionLocalOf<HeaderBackdrop?> { null }

class HeaderScreen(val backdrop: HeaderBackdrop, val key: String)

/** The screen under the header, entering or leaving, and the key its accessory shows under. */
val LocalHeaderScreen = staticCompositionLocalOf<HeaderScreen?> { null }

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
    val screen = LocalHeaderScreen.current ?: return 0.dp
    val current by rememberUpdatedState(content)
    // Saved, so the content starts in place when its screen comes back, before the header shows it.
    var height by rememberSaveable { mutableIntStateOf(0) }
    DisposableEffect(screen) {
        val slot: @Composable () -> Unit = {
            Box(Modifier.onSizeChanged { height = it.height }) { current() }
        }
        screen.backdrop.show(screen.key, slot)
        onDispose { screen.backdrop.remove(screen.key, slot) }
    }
    return with(LocalDensity.current) { height.toDp() }
}
