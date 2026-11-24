package app.koma.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.koma.AppContainer
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Asks GitHub once for a newer release. A failure shows no banner and is not retried. */
class UpdateViewModel(container: AppContainer) : ViewModel() {
    private val newer = MutableStateFlow<Release?>(null)
    val release: StateFlow<Release?> = newer.asStateFlow()

    init {
        viewModelScope.launch(container.work) {
            newer.value =
                try {
                    newerRelease(container.http, container.installedVersion)
                } catch (_: IOException) {
                    null
                } catch (_: IllegalArgumentException) {
                    null
                }
        }
    }
}
