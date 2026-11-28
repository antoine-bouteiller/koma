package app.koma.updates

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.koma.AppContainer
import app.koma.R
import app.koma.data.MediaRef
import app.koma.ui.EmptyState
import app.koma.ui.TrackHeaderBackdrop
import coil3.ImageLoader
import coil3.compose.AsyncImage

/** The chapters found on library titles, newest first. [onOpen] opens a title. */
@Composable
fun UpdatesView(
    container: AppContainer,
    contentPadding: PaddingValues,
    onOpen: (MediaRef) -> Unit,
) {
    val model = viewModel { UpdatesViewModel(container) }
    val state by model.state.collectAsStateWithLifecycle()
    val motion = MaterialTheme.motionScheme
    AnimatedContent(
        state,
        transitionSpec = {
            fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec())
        },
        contentKey = { it::class },
        label = "updates",
    ) { shown ->
        when (shown) {
            is UpdatesState.Rows -> ChapterList(shown, container.images, contentPadding, onOpen)
            UpdatesState.Empty ->
                EmptyState(
                    R.drawable.ic_notifications,
                    "No new chapters",
                    "New chapters of the titles you follow show up here.",
                    Modifier.padding(contentPadding).testTag("empty-updates"),
                )
            UpdatesState.Loading -> Unit
        }
    }
}

@Composable
private fun ChapterList(
    rows: UpdatesState.Rows,
    images: ImageLoader,
    contentPadding: PaddingValues,
    onOpen: (MediaRef) -> Unit,
) {
    val list = rememberLazyListState()
    TrackHeaderBackdrop(list)
    LazyColumn(
        state = list,
        contentPadding = contentPadding,
        modifier = Modifier.fillMaxSize().testTag("updates"),
    ) {
        items(rows.items, key = { it.id }) { item ->
            when (item) {
                is UpdatesItem.Day -> DayHeader(item, Modifier.animateItem())
                is UpdatesItem.Chapter -> ChapterRow(item, images, onOpen, Modifier.animateItem())
            }
        }
    }
}

@Composable
private fun DayHeader(day: UpdatesItem.Day, modifier: Modifier) {
    Text(
        day.label,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier =
            modifier
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
                .semantics { heading() }
                .testTag("updates-${day.id}"),
    )
}

@Composable
private fun ChapterRow(
    chapter: UpdatesItem.Chapter,
    images: ImageLoader,
    onOpen: (MediaRef) -> Unit,
    modifier: Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { onOpen(chapter.ref) }
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("update-${chapter.id}"),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = chapter.cover,
            contentDescription = null,
            imageLoader = images,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier.width(48.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                chapter.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                chapter.chapter,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
