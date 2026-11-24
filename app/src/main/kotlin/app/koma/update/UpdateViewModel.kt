package app.koma.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.koma.AppContainer
import app.koma.data.failed
import java.io.File
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Asks GitHub once for a newer release, then downloads it on request and hands it to Android's
 * installer. A failed check offers nothing and is not retried.
 */
class UpdateViewModel(private val container: AppContainer) : ViewModel() {
    private val step = MutableStateFlow<UpdateStep?>(null)
    private val dialogOpen = MutableStateFlow(false)
    private val apk = File(container.context.cacheDir, "update/koma.apk")

    val state: StateFlow<UpdateState?> =
        combine(step, dialogOpen) { step, open ->
                updateState(step, open, container.installedVersion)
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch(container.work) {
            val release =
                try {
                    newerRelease(container.http, container.installedVersion)
                } catch (_: IOException) {
                    null
                } catch (_: IllegalArgumentException) {
                    null
                }
            step.value = release?.let(UpdateStep::Offered)
        }
    }

    fun openDialog() {
        dialogOpen.value = true
    }

    fun closeDialog() {
        dialogOpen.value = false
    }

    /** Downloads the release, or hands the downloaded APK to the installer again. */
    fun act() {
        when (val current = step.value) {
            is UpdateStep.Offered,
            is UpdateStep.Failed -> download(current.release)
            is UpdateStep.Downloaded -> container.installApk(apk)
            is UpdateStep.Downloading,
            null -> Unit
        }
    }

    private fun download(release: Release) {
        step.value = UpdateStep.Downloading(release, 0, null)
        viewModelScope.launch {
            val failed =
                withContext(container.work) {
                    failed {
                        downloadApk(container.http, release.url, apk) { done, total ->
                            step.value = UpdateStep.Downloading(release, done, total)
                        }
                    }
                }
            if (failed) {
                step.value = UpdateStep.Failed(release)
                return@launch
            }
            step.value = UpdateStep.Downloaded(release)
            container.installApk(apk)
        }
    }
}
