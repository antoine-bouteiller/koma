package app.koma.ui

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.koma.R
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.hazeBlur

// Google Sans Flex cut to one instance: SemiBold, fully rounded, optical size 28.
val TitleFont = FontFamily(Font(R.font.google_sans_flex_rounded_semibold, FontWeight.SemiBold))

/** A screen's title, after a glass back button when [onBack] is set. */
@Composable
fun ScreenHeader(
    title: String,
    titleTag: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(start = if (onBack != null) 12.dp else 16.dp, end = 8.dp),
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
            modifier = Modifier.weight(1f).semantics { heading() }.testTag(titleTag),
        )
        actions()
    }
}

/**
 * The header floating over the content marked as the [haze] source. Bare at rest, it lays down a
 * band of glass as the content [backdrop] tracks runs on under it, following the scroll rather than
 * a timer.
 */
@Composable
fun GlassHeader(
    haze: HazeState,
    backdrop: HeaderBackdrop,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val style =
        remember(colors.onSurface, colors.surface) {
            glassBlurStyle(colors.onSurface.copy(alpha = 0.06f), colors.surface)
        }
    Box(modifier.fillMaxWidth().testTag("header")) {
        Box(
            Modifier.matchParentSize()
                .graphicsLayer {
                    alpha = (backdrop.scrolledPast / BandFadeDistance.toPx()).coerceIn(0f, 1f)
                }
                .clip(BandShape)
                .hazeBlur(HazeInput.Sources(haze), style)
        )
        Column(
            Modifier.windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
            ),
            content = content,
        )
    }
}

private val BandShape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)
private val BandFadeDistance = 12.dp
