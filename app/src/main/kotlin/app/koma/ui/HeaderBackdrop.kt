package app.koma.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/** The scroll of the content under the screen header, which lays the header's glass down. */
@Stable
class HeaderBackdrop {
    private var source by mutableStateOf<ScrollableState?>(null)

    /** How far the content runs on under the header; none while no scroll is tracked. */
    val scrolledPast: Float
        get() =
            when (val state = source) {
                null -> 0f
                is ScrollState -> state.value.toFloat()
                // A lazy list measures only the rows in view, so a row out of sight counts as far.
                is LazyListState ->
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
}

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
