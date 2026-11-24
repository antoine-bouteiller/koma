package app.koma.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * What the content under the screen header hands it: its scroll, which lays the header's band down,
 * and an accessory the header shows under the title.
 */
@Stable
class HeaderBackdrop {
    private val sources = mutableStateListOf<ScrollableState>()

    var accessory by mutableStateOf<(@Composable () -> Unit)?>(null)
        private set

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

/** The backdrop of the screen shown; null on a screen leaving, which hands the header nothing. */
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
 * Shows [content] in the screen header, under the title, while this is composed. The header's band
 * covers it, and it slides away under the title as the content scrolls down.
 */
@Composable
fun HeaderAccessory(content: @Composable () -> Unit) {
    val backdrop = LocalHeaderBackdrop.current ?: return
    val current by rememberUpdatedState(content)
    DisposableEffect(backdrop) {
        val slot: @Composable () -> Unit = { current() }
        backdrop.show(slot)
        onDispose { backdrop.remove(slot) }
    }
}
