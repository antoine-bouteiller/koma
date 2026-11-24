package app.koma.ui

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity

/**
 * How far the bars around the content have slid away, from 0, shown, to 1, hidden. Scrolling the
 * content down hides them and scrolling it up brings them back, following the finger over
 * [distance] pixels, then settling on the nearer end once the scroll stops.
 */
@Stable
class HidingBars(private val distance: Float, private val settle: AnimationSpec<Float>) :
    NestedScrollConnection {
    var hidden by mutableFloatStateOf(0f)
        private set

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        hidden = (hidden - consumed.y / distance).coerceIn(0f, 1f)
        return Offset.Zero
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        settleTo(if (hidden > 0.5f) 1f else 0f)
        return Velocity.Zero
    }

    suspend fun show() = settleTo(0f)

    private suspend fun settleTo(target: Float) {
        animate(hidden, target, animationSpec = settle) { value, _ -> hidden = value }
    }
}
