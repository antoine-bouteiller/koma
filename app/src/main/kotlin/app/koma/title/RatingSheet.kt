package app.koma.title

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import app.koma.ui.PillButton
import app.koma.ui.TitleFont
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.launch

/** Rates the series on a gauge, from 0, unrated, to 5, starting at [current]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RatingSheet(current: Double, onRate: (Double) -> Unit, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var draft by rememberSaveable { mutableDoubleStateOf(current) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Rating",
                style =
                    MaterialTheme.typography.titleLarge.copy(
                        fontFamily = TitleFont,
                        fontWeight = FontWeight.SemiBold,
                    ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            )
            RatingGauge(
                draft,
                onChange = { draft = it },
                Modifier.widthIn(max = 280.dp).fillMaxWidth(),
            )
            PillButton(
                "Save",
                onClick = {
                    scope.launch { sheet.hide() }.invokeOnCompletion { onRate(draft) }
                },
                enabled = draft != current,
                modifier = Modifier.fillMaxWidth().testTag("rating-save"),
            )
        }
    }
}

/**
 * A dial, open at the bottom, whose arc fills up to [score] around the score and what it says.
 * Tapping or dragging along the arc moves it by [RATING_STEP]s.
 */
@Composable
private fun RatingGauge(score: Double, onChange: (Double) -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val latest by rememberUpdatedState(score)
    val notify by rememberUpdatedState(onChange)
    val fill by
        animateFloatAsState(
            (score / 5).toFloat(),
            MaterialTheme.motionScheme.fastSpatialSpec(),
        )
    val word = ratingWord(score)
    fun change(value: Double) {
        if (value == latest) return
        haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        notify(value)
    }
    Box(
        modifier
            .aspectRatio(GaugeAspect)
            .pointerInput(Unit) {
                detectTapGestures {
                    val ring = ring(size.toSize())
                    if (ring.holds(it)) change(snapRating(ring.valueAt(it)))
                }
            }
            .pointerInput(Unit) {
                var dragging = false
                detectDragGestures(
                    onDragStart = { dragging = ring(size.toSize()).holds(it) },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                ) { touch, _ ->
                    if (!dragging) return@detectDragGestures
                    val value = snapRating(ring(size.toSize()).valueAt(touch.position))
                    // Across the opening, the value would leap from one end to the other.
                    if (abs(value - latest) <= 2.5) change(value)
                }
            }
            .semantics {
                contentDescription = "Rating"
                stateDescription = word
                progressBarRangeInfo =
                    ProgressBarRangeInfo(
                        score.toFloat(),
                        0f..5f,
                        steps = (5 / RATING_STEP).toInt() - 1,
                    )
                setProgress {
                    change(snapRating(it))
                    true
                }
            }
            .drawBehind {
                drawGauge(
                    fill,
                    active = colors.primary,
                    inactive = colors.secondaryContainer,
                    onActive = colors.onPrimary,
                    onInactive = colors.onSecondaryContainer,
                )
            }
            .testTag("rating-gauge")
    ) {
        Column(
            Modifier.layout { measurable, constraints ->
                val text = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                val box = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
                layout(constraints.maxWidth, constraints.maxHeight) {
                    text.place(
                        (constraints.maxWidth - text.width) / 2,
                        (ring(box).center.y - text.height / 2f).roundToInt(),
                    )
                }
            },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedContent(
                score,
                transitionSpec = {
                    val up = targetState > initialState
                    (slideInVertically { if (up) it else -it } + fadeIn()) togetherWith
                        (slideOutVertically { if (up) -it else it } + fadeOut())
                },
                label = "score",
            ) {
                Text(
                    if (it > 0) it.toString() else "–",
                    style =
                        MaterialTheme.typography.displayLarge.copy(
                            fontFamily = TitleFont,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    color = if (it > 0) colors.onSurface else colors.outline,
                    modifier = Modifier.testTag("rating-score"),
                )
            }
            Text(
                word,
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.testTag("rating-word"),
            )
        }
    }
}

private const val START = 135f
private const val SWEEP = 270f
private val Track = 16.dp
private val Handle = DpSize(4.dp, 40.dp)

/** Wide enough for the ring's two ends and their handle, which reach below its center. */
private const val GaugeAspect = 1.15f

/** The gauge's circle within a box of its size: as wide as the box, the handle included. */
private class Ring(val center: Offset, val radius: Float) {
    /** Whether [at] lies on the ring rather than inside it. */
    fun holds(at: Offset): Boolean = (at - center).getDistance() >= radius / 2

    /** The value, from 0 to 5, at [at]'s angle; the opening goes to the nearer end. */
    fun valueAt(at: Offset): Float {
        val degrees = Math.toDegrees(atan2(at.y - center.y, at.x - center.x).toDouble()).toFloat()
        val along = ((degrees - START) % 360 + 360) % 360
        val fraction =
            when {
                along <= SWEEP -> along / SWEEP
                along < SWEEP + (360 - SWEEP) / 2 -> 1f
                else -> 0f
            }
        return fraction * 5
    }

    fun pointAt(degrees: Float, distance: Float = radius): Offset {
        val radians = degrees * PI.toFloat() / 180
        return center + Offset(cos(radians), sin(radians)) * distance
    }
}

private fun Density.ring(box: Size): Ring {
    // The handle's round caps reach past its ends.
    val reach = (Handle.height + Handle.width).toPx() / 2
    val radius = box.width / 2 - reach
    return Ring(Offset(box.width / 2, reach + radius), radius)
}

/**
 * An expressive slider bent into an arc: an active track up to [fill], a gap, an upright handle, a
 * gap and the inactive track, with a dot on each whole score.
 */
private fun DrawScope.drawGauge(
    fill: Float,
    active: Color,
    inactive: Color,
    onActive: Color,
    onInactive: Color,
) {
    val ring = ring(size)
    val track = Track.toPx()
    val handle = Handle.toSize()
    fun degrees(length: Float) = Math.toDegrees((length / ring.radius).toDouble()).toFloat()
    // Round caps reach past an arc's ends by half the track.
    val cap = degrees(track / 2)
    val gap = degrees(handle.width / 2 + 6.dp.toPx())
    val at = START + SWEEP * fill
    fun arc(color: Color, from: Float, to: Float) {
        if (to - from <= 2 * cap) return
        drawArc(
            color,
            from + cap,
            to - from - 2 * cap,
            useCenter = false,
            topLeft = ring.center - Offset(ring.radius, ring.radius),
            size = Size(ring.radius * 2, ring.radius * 2),
            style = Stroke(track, cap = StrokeCap.Round),
        )
    }
    arc(active, START, at - gap)
    arc(inactive, at + gap, START + SWEEP)
    for (whole in 0..5) {
        val angle = (START + SWEEP * whole / 5).coerceIn(START + cap, START + SWEEP - cap)
        if (abs(angle - at) < gap + cap) continue
        drawCircle(
            if (angle < at) onActive else onInactive,
            radius = 2.dp.toPx(),
            center = ring.pointAt(angle),
        )
    }
    drawLine(
        active,
        ring.pointAt(at, ring.radius - handle.height / 2),
        ring.pointAt(at, ring.radius + handle.height / 2),
        strokeWidth = handle.width,
        cap = StrokeCap.Round,
    )
}
