package app.koma.update

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.koma.R
import app.koma.ui.PillButton
import app.koma.ui.PillShape
import app.koma.ui.TitleFont

/** The header pill shown while a newer release is on offer; it opens [UpdateDialog]. */
@Composable
fun UpdatePill(model: UpdateViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    val pill = state?.pill ?: return
    PillButton(
        pill,
        onClick = model::openDialog,
        icon = R.drawable.ic_download,
        modifier = Modifier.testTag("update-pill"),
    )
}

/** Koma's icon over the step, its body, then the step's action above the way out. */
@Composable
fun UpdateDialog(model: UpdateViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    val dialog = state?.dialog ?: return
    Dialog(onDismissRequest = model::closeDialog) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.testTag("update-dialog"),
        ) {
            Column(
                Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Header(dialog.title, dialog.subtitle)
                AnimatedContent(
                    dialog.body,
                    contentKey = { it::class },
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                ) { body ->
                    when (body) {
                        is UpdateBody.Summary -> Summary(body)
                        is UpdateBody.Progress -> Progress(body)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    dialog.action?.let {
                        PillButton(
                            it.label,
                            onClick = model::act,
                            icon = it.icon,
                            modifier = Modifier.fillMaxWidth().testTag("update-action"),
                        )
                    }
                    PillButton(
                        dialog.dismiss,
                        onClick = model::closeDialog,
                        outlined = true,
                        modifier = Modifier.fillMaxWidth().testTag("update-dismiss"),
                    )
                }
            }
        }
    }
}

@Composable
private fun Header(title: String, subtitle: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon()
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                style =
                    MaterialTheme.typography.titleLarge.copy(
                        fontFamily = TitleFont,
                        fontWeight = FontWeight.SemiBold,
                    ),
                modifier = Modifier.testTag("update-title"),
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("update-subtitle"),
            )
        }
    }
}

/** The launcher icon, masked to the device's icon shape as the launcher shows it. */
@Composable
private fun AppIcon() {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { IconSize.roundToPx() }
    val icon =
        remember(context, px) {
            checkNotNull(ContextCompat.getDrawable(context, R.mipmap.ic_launcher))
                .toBitmap(px, px)
                .asImageBitmap()
        }
    Image(icon, contentDescription = null, modifier = Modifier.size(IconSize))
}

@Composable
private fun Summary(body: UpdateBody.Summary) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    VersionBadge(body.from, highlighted = false)
                    Icon(
                        painterResource(R.drawable.ic_arrow_forward),
                        contentDescription = "to",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    VersionBadge(body.to, highlighted = true)
                }
                body.message?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("update-message"),
                    )
                }
            }
        }
        body.error?.let { Notice(it) }
    }
}

@Composable
private fun VersionBadge(version: String, highlighted: Boolean) {
    val colors = MaterialTheme.colorScheme
    Text(
        version,
        style = MaterialTheme.typography.labelLarge,
        color = if (highlighted) colors.onPrimaryContainer else colors.onSurfaceVariant,
        modifier =
            Modifier.then(
                    if (highlighted) Modifier.background(colors.primaryContainer, PillShape)
                    else Modifier.border(1.dp, colors.outlineVariant, PillShape)
                )
                .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun Notice(message: String) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painterResource(R.drawable.ic_error),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp),
            )
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.testTag("update-error"),
            )
        }
    }
}

/** A wavy ring around the percent done and the megabytes fetched. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Progress(body: UpdateBody.Progress) {
    val stroke = with(LocalDensity.current) { Stroke(6.dp.toPx(), cap = StrokeCap.Round) }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(Modifier.size(RingSize), contentAlignment = Alignment.Center) {
            val ring = Modifier.fillMaxSize().testTag("update-progress")
            val fraction = body.fraction
            if (fraction == null) {
                CircularWavyProgressIndicator(
                    ring,
                    stroke = stroke,
                    trackStroke = stroke,
                    wavelength = Wavelength,
                )
            } else {
                val shown by
                    animateFloatAsState(fraction, MaterialTheme.motionScheme.slowEffectsSpec())
                CircularWavyProgressIndicator(
                    { shown },
                    ring,
                    stroke = stroke,
                    trackStroke = stroke,
                    wavelength = Wavelength,
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                body.percent?.let {
                    Text(
                        it,
                        style =
                            MaterialTheme.typography.displaySmall.copy(
                                fontFamily = TitleFont,
                                fontWeight = FontWeight.SemiBold,
                            ),
                    )
                }
                Text(
                    body.size,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("update-size"),
                )
            }
        }
    }
}

private val IconSize = 48.dp
private val RingSize = 176.dp
private val Wavelength = 40.dp
