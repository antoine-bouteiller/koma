package app.koma.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * What the content under the screen header hands it: its scroll, which lays the header's glass
 * down, and an accessory the header shows under the title.
 */
@Stable
class HeaderBackdrop {
    private var source by mutableStateOf<ScrollableState?>(null)

    var accessory by mutableStateOf<(@Composable () -> Unit)?>(null)
        private set

    /** How far the content runs on under the header; none while no scroll is tracked. */
    val scrolledPast: Float
        get() =
            when (val state = source) {
                null -> 0f
                is ScrollState -> state.value.toFloat()
                // A lazy layout measures only the items in view, so one out of sight counts as far.
                is LazyListState ->
                    if (state.firstVisibleItemIndex > 0) Float.MAX_VALUE
                    else state.firstVisibleItemScrollOffset.toFloat()
                is LazyGridState ->
                    if (state.firstVisibleItemIndex > 0) Float.MAX_VALUE
                    else state.firstVisibleItemScrollOffset.toFloat()
                else -> if (state.canScrollBackward) Float.MAX_VALUE else 0f
            }

    internal fun track(state: ScrollableState) {
        source = state
    }

    internal fun release(state: ScrollableState) {
        if (source === state) source = null
    }

    internal fun show(slot: @Composable () -> Unit) {
        accessory = slot
    }

    internal fun remove(slot: @Composable () -> Unit) {
        if (accessory === slot) accessory = null
    }
}

/** The backdrop of the screen shown; null on a screen leaving, which hands the header nothing. */
val LocalHeaderBackdrop = staticCompositionLocalOf<HeaderBackdrop?> { null }

/** Hands [state], the scroll of the content shown, to the screen header floating over it. */
@Composable
fun TrackHeaderBackdrop(state: ScrollableState) {
    val backdrop = LocalHeaderBackdrop.current ?: return
    DisposableEffect(backdrop, state) {
        backdrop.track(state)
        onDispose { backdrop.release(state) }
    }
}

/**
 * Shows [content] in the screen header, under the title, while this is composed. The header's glass
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
