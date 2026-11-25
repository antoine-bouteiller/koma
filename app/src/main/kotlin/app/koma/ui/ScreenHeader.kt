package app.koma.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.koma.R
import kotlin.math.roundToInt

// Google Sans Flex cut to one instance: SemiBold, fully rounded, optical size 28.
val TitleFont = FontFamily(Font(R.font.google_sans_flex_rounded_semibold, FontWeight.SemiBold))

/** The header's row: the [title] of the screen shown, then [actions]. */
@Composable
fun ScreenHeader(actions: @Composable RowScope.() -> Unit = {}, title: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) { title() }
        actions()
    }
}

/** A screen's title, after a glass back button when [onBack] is set. */
@Composable
fun ScreenTitle(title: String, titleTag: String, onBack: (() -> Unit)? = null) {
    Row(
        Modifier.padding(start = if (onBack != null) 12.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            GlassIconButton(
                R.drawable.ic_arrow_back,
                contentDescription = "Back",
                onClick = onBack,
                modifier = Modifier.padding(end = 4.dp).testTag("back"),
            )
        }
        Text(
            title,
            style =
                MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = TitleFont,
                    fontWeight = FontWeight.SemiBold,
                ),
            maxLines = 1,
            modifier = Modifier.semantics { heading() }.testTag(titleTag),
        )
    }
}

/**
 * The header floating over the content. Bare at rest, it lays down a solid band as the content
 * [backdrop] tracks runs on under it, following the scroll rather than a timer. Under [content] it
 * shows [accessory], which slides away under the title as [hidden] goes from 0 to 1. [onHeight]
 * receives the header's height without the accessory, so each screen's start can add its own and
 * stay put while the accessory slides or changes.
 */
@Composable
fun FloatingHeader(
    backdrop: HeaderBackdrop,
    hidden: () -> Float,
    onHeight: (Int) -> Unit,
    accessory: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val band = MaterialTheme.colorScheme.surfaceContainer
    // Written while measuring, before the header's size is reported.
    val showing = remember { IntArray(1) }
    Box(
        modifier.fillMaxWidth().onSizeChanged { onHeight(it.height - showing[0]) }.testTag("header")
    ) {
        Box(
            Modifier.matchParentSize()
                .graphicsLayer {
                    alpha = (backdrop.scrolledPast / BandFadeDistance.toPx()).coerceIn(0f, 1f)
                }
                .background(band, BandShape)
        )
        Column(
            Modifier.windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
            )
        ) {
            content()
            Box(
                Modifier.clipToBounds().layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    val away = (placeable.height * hidden()).roundToInt()
                    showing[0] = placeable.height - away
                    layout(placeable.width, showing[0]) { placeable.place(0, -away) }
                }
            ) {
                accessory()
            }
        }
    }
}

private val BandShape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)
private val BandFadeDistance = 12.dp
