package app.koma.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.koma.AppContainer
import app.koma.R
import app.koma.ui.EmptyState
import app.koma.ui.TrackHeaderBackdrop
import coil3.ImageLoader
import coil3.compose.AsyncImage

/** The chapters read on MangaBaka, newest first. Each visit asks for newer reads. */
@Composable
fun HistoryView(container: AppContainer, contentPadding: PaddingValues) {
    val model = viewModel { HistoryViewModel(container) }
    LaunchedEffect(model) { model.sync() }
    val state by model.state.collectAsStateWithLifecycle()
    when (val shown = state) {
        is HistoryState.Rows ->
            ReadList(shown, container.images, contentPadding, model::sync, model::loadOlder)
        HistoryState.Failed ->
            EmptyState(
                R.drawable.ic_error,
                "Can’t Load Your History",
                "MangaBaka did not answer.",
                Modifier.padding(contentPadding),
            ) {
                Button(onClick = model::sync, modifier = Modifier.testTag("history-retry")) {
                    Text("Try Again")
                }
            }
        HistoryState.Loading ->
            Text(
                "Loading your history…",
                textAlign = TextAlign.Center,
                modifier =
                    Modifier.fillMaxWidth()
                        .padding(contentPadding)
                        .padding(16.dp)
                        .testTag("history-loading"),
            )
        HistoryState.Empty ->
            EmptyState(
                R.drawable.ic_history,
                "Nothing read yet",
                "The chapters you read show up here.",
                Modifier.padding(contentPadding).testTag("empty-history"),
            )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadList(
    rows: HistoryState.Rows,
    images: ImageLoader,
    contentPadding: PaddingValues,
    onRefresh: () -> Unit,
    onEnd: () -> Unit,
) {
    val list = rememberLazyListState()
    val atEnd by remember {
        derivedStateOf {
            val layout = list.layoutInfo
            layout.visibleItemsInfo.lastOrNull()?.index == layout.totalItemsCount - 1
        }
    }
    // Asked again at each change, as a load refused while a sync ran is due once it ends. A failed
    // load waits for its Load More button.
    LaunchedEffect(atEnd, rows) { if (atEnd && rows.footer == HistoryState.Footer.None) onEnd() }
    TrackHeaderBackdrop(list)
    val pull = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = rows.refreshing,
        onRefresh = onRefresh,
        state = pull,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = pull,
                isRefreshing = rows.refreshing,
                modifier =
                    Modifier.align(Alignment.TopCenter)
                        .padding(top = contentPadding.calculateTopPadding()),
            )
        },
    ) {
        LazyColumn(
            state = list,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize().testTag("history"),
        ) {
            items(rows.items, key = { it.id }) { item ->
                when (item) {
                    is HistoryItem.Day -> DayHeader(item)
                    is HistoryItem.Read -> ReadRow(item, images)
                }
            }
            item(key = "footer") { Footer(rows.footer, onEnd) }
        }
    }
}

@Composable
private fun DayHeader(day: HistoryItem.Day) {
    Text(
        day.label,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier =
            Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
                .semantics { heading() }
                .testTag("history-day-${day.id}"),
    )
}

@Composable
private fun ReadRow(read: HistoryItem.Read, images: ImageLoader) {
    Row(
        Modifier.fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("history-${read.id}"),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = read.cover,
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
                read.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("history-title-${read.id}"),
            )
            Text(
                read.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("history-chapters-${read.id}"),
            )
        }
    }
}

@Composable
private fun Footer(footer: HistoryState.Footer, onRetry: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
        when (footer) {
            HistoryState.Footer.Loading ->
                Text("Loading…", modifier = Modifier.testTag("history-more-loading"))
            HistoryState.Footer.Retry ->
                OutlinedButton(
                    onClick = onRetry,
                    modifier = Modifier.testTag("history-more-retry"),
                ) {
                    Text("Load More")
                }
            HistoryState.Footer.None -> Unit
        }
    }
}
