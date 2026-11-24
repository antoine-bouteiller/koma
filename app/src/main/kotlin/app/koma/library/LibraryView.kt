package app.koma.library

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.koma.AppContainer
import app.koma.R
import app.koma.ui.EmptyState
import app.koma.ui.LoadingState

/** The signed-in person's MangaBaka library. [onOpen] opens a series by its id. */
@Composable
fun LibraryView(container: AppContainer, contentPadding: PaddingValues, onOpen: (Long) -> Unit) {
    val model = viewModel { LibraryViewModel(container) }
    LaunchedEffect(model) { model.open() }
    val state by model.state.collectAsStateWithLifecycle()
    val motion = MaterialTheme.motionScheme
    AnimatedContent(
        state,
        transitionSpec = {
            fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec())
        },
        contentKey = { it::class },
        label = "library",
    ) { shown ->
        when (shown) {
            LibraryState.Failed ->
                EmptyState(
                    R.drawable.ic_error,
                    "Can’t Load Your Library",
                    "MangaBaka did not answer.",
                    Modifier.padding(contentPadding),
                ) {
                    Button(onClick = model::sync, modifier = Modifier.testTag("library-retry")) {
                        Text("Try Again")
                    }
                }
            LibraryState.Loading ->
                LoadingState(
                    "Loading your library…",
                    Modifier.padding(contentPadding).testTag("library-loading"),
                )
            is LibraryState.Shelves ->
                ShelfPager(shown, container.images, contentPadding, model::sync, onOpen)
        }
    }
}
