package app.koma.update

import androidx.annotation.DrawableRes
import app.koma.R
import java.util.Locale

/** How far the offered release has got. */
sealed interface UpdateStep {
    val release: Release

    data class Offered(override val release: Release) : UpdateStep

    /** [total] is null while the APK's size is unknown. */
    data class Downloading(override val release: Release, val done: Long, val total: Long?) :
        UpdateStep

    /** The APK went to Android's installer, which the user may have dismissed. */
    data class Downloaded(override val release: Release) : UpdateStep

    data class Failed(override val release: Release) : UpdateStep
}

/** The header pill offering a newer release, and its dialog while open. */
data class UpdateState(val pill: String, val dialog: UpdateDialog?)

/** [action] is null while downloading. */
data class UpdateDialog(
    val title: String,
    val subtitle: String,
    val body: UpdateBody,
    val action: UpdateAction?,
    val dismiss: String,
)

sealed interface UpdateBody {
    /** The installed version going to the offered one; [error] when the download failed. */
    data class Summary(
        val from: String,
        val to: String,
        val message: String?,
        val error: String?,
    ) : UpdateBody

    /** [fraction] and [percent] are null while the size is unknown. */
    data class Progress(val fraction: Float?, val percent: String?, val size: String) : UpdateBody
}

data class UpdateAction(val label: String, @param:DrawableRes val icon: Int)

fun updateState(step: UpdateStep?, dialogOpen: Boolean, installed: String): UpdateState? {
    step ?: return null
    val pill =
        when (step) {
            is UpdateStep.Downloading -> percent(step)?.let { "$it%" } ?: "Downloading"
            is UpdateStep.Downloaded -> "Install"
            is UpdateStep.Offered,
            is UpdateStep.Failed -> "Update"
        }
    return UpdateState(pill, if (dialogOpen) updateDialog(step, installed) else null)
}

private fun updateDialog(step: UpdateStep, installed: String): UpdateDialog {
    val subtitle = "Koma ${step.release.version}"
    fun summary(message: String?, error: String? = null) =
        UpdateBody.Summary("v$installed", "v${step.release.version}", message, error)
    return when (step) {
        is UpdateStep.Offered ->
            UpdateDialog(
                "Update available",
                subtitle,
                summary(
                    "Koma downloads it, then Android's installer asks to replace this version."
                ),
                UpdateAction("Download", R.drawable.ic_download),
                dismiss = "Later",
            )
        is UpdateStep.Downloading ->
            UpdateDialog(
                "Downloading update",
                subtitle,
                UpdateBody.Progress(
                    step.total?.let { step.done.toFloat() / it },
                    percent(step)?.let { "$it%" },
                    listOfNotNull(step.done, step.total)
                        .joinToString(" of ", transform = ::megabytes),
                ),
                action = null,
                dismiss = "Close",
            )
        is UpdateStep.Downloaded ->
            UpdateDialog(
                "Ready to install",
                subtitle,
                summary("Android's installer asks to replace this version."),
                UpdateAction("Install", R.drawable.ic_phone_android),
                dismiss = "Later",
            )
        is UpdateStep.Failed ->
            UpdateDialog(
                "Download failed",
                subtitle,
                summary(null, "The download failed. Check the connection and try again."),
                UpdateAction("Retry", R.drawable.ic_refresh),
                dismiss = "Later",
            )
    }
}

private fun percent(step: UpdateStep.Downloading): Int? =
    step.total?.takeIf { it > 0 }?.let { (step.done * 100 / it).toInt() }

private fun megabytes(bytes: Long): String = "%.1f MB".format(Locale.ROOT, bytes / 1_000_000.0)
