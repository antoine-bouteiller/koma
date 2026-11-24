package app.koma.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect

/**
 * A surface floating over content that scrolls under it: the content blurred under [tint]. Where
 * nothing blurs, the tint laid on [background] hides the content all the same.
 */
fun glassBlurStyle(tint: Color, background: Color): HazeBlurStyle {
    val solid = tint.compositeOver(background)
    return HazeBlurStyle {
        backgroundColor(background)
        colorEffects(listOf(HazeColorEffect.tint(solid.copy(alpha = 0.55f))))
        fallbackColorEffect(HazeColorEffect.tint(solid))
    }
}
