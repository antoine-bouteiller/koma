package app.koma.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp

private val InnerCorner = CornerSize(4.dp)

/**
 * Shapes of the members of a connected group: [outer] on the group's own edges, nearly square where
 * two members meet.
 */
class ConnectedShapes(outer: CornerSize) {
    private val single = RoundedCornerShape(outer)
    private val first = RoundedCornerShape(outer, InnerCorner, InnerCorner, outer)
    private val middle = RoundedCornerShape(InnerCorner)
    private val last = RoundedCornerShape(InnerCorner, outer, outer, InnerCorner)
    private val top = RoundedCornerShape(outer, outer, InnerCorner, InnerCorner)
    private val bottom = RoundedCornerShape(InnerCorner, InnerCorner, outer, outer)

    fun inRow(index: Int, count: Int): CornerBasedShape =
        when {
            count == 1 -> single
            index == 0 -> first
            index == count - 1 -> last
            else -> middle
        }

    fun inColumn(index: Int, count: Int): CornerBasedShape =
        when {
            count == 1 -> single
            index == 0 -> top
            index == count - 1 -> bottom
            else -> middle
        }

    companion object {
        val Pill = ConnectedShapes(CornerSize(50))
        val Card = ConnectedShapes(CornerSize(20.dp))
    }
}

val PillShape = RoundedCornerShape(50)

/**
 * [target], its corners easing over from the previous target, and rounding down to half while
 * [pressed], as an expressive button answers a press.
 */
@Composable
fun morphingShape(target: CornerBasedShape, pressed: Boolean): CornerBasedShape {
    val spec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val press by animateFloatAsState(if (pressed) 0.5f else 1f, spec)
    val morph = remember { Morph(target) }
    LaunchedEffect(target) { morph.to(target, spec) }
    return morph.at(press)
}

private class Morph(initial: CornerBasedShape) {
    private var from by mutableStateOf(initial)
    private var to by mutableStateOf(initial)
    private val progress = Animatable(1f)

    suspend fun to(target: CornerBasedShape, spec: AnimationSpec<Float>) {
        if (target == to) return
        // An interrupted morph carries on from where it stands.
        from = if (progress.value == 1f) to else at(1f)
        to = target
        progress.snapTo(0f)
        progress.animateTo(1f, spec)
    }

    fun at(scale: Float): CornerBasedShape {
        val t = progress.value
        return RoundedCornerShape(
            Corner(from.topStart, to.topStart, t, scale),
            Corner(from.topEnd, to.topEnd, t, scale),
            Corner(from.bottomEnd, to.bottomEnd, t, scale),
            Corner(from.bottomStart, to.bottomStart, t, scale),
        )
    }
}

private data class Corner(
    val from: CornerSize,
    val to: CornerSize,
    val progress: Float,
    val scale: Float,
) : CornerSize {
    override fun toPx(shapeSize: Size, density: Density): Float =
        lerp(from.toPx(shapeSize, density), to.toPx(shapeSize, density), progress) * scale
}
