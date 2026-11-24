package app.koma.library

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.koma.AppContainer
import app.koma.R
import app.koma.ui.EmptyState

/** The signed-in person's MangaBaka library. */
@Composable
fun LibraryView(container: AppContainer, contentPadding: PaddingValues) {
    val model = viewModel { LibraryViewModel(container) }
    LaunchedEffect(model) { model.open() }
    val state by model.state.collectAsStateWithLifecycle()
    when (val shown = state) {
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
            Text(
                "Loading your library…",
                textAlign = TextAlign.Center,
                modifier =
                    Modifier.fillMaxWidth()
                        .padding(contentPadding)
                        .padding(16.dp)
                        .testTag("library-loading"),
            )
        is LibraryState.Shelves -> ShelfPager(shown, container.images, contentPadding, model::sync)
    }
}
