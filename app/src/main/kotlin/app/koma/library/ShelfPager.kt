package app.koma.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.koma.R
import app.koma.ui.EmptyState
import coil3.ImageLoader
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch

/** The shelves' tabs over a pager of their cover grids. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShelfPager(
    state: LibraryState.Shelves,
    images: ImageLoader,
    contentPadding: PaddingValues,
    onRefresh: () -> Unit,
) {
    val pager = rememberPagerState { state.shelves.size }
    val scope = rememberCoroutineScope()
    // The tabs sit under the header, so the grids scroll under them rather than under the header.
    val below = PaddingValues(bottom = contentPadding.calculateBottomPadding())
    Column(Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
        PrimaryScrollableTabRow(
            selectedTabIndex = pager.currentPage,
            edgePadding = 0.dp,
            modifier = Modifier.testTag("shelves"),
        ) {
            state.shelves.forEachIndexed { index, shelf ->
                Tab(
                    selected = pager.currentPage == index,
                    onClick = { scope.launch { pager.animateScrollToPage(index) } },
                    text = { Text(shelf.title, maxLines = 1) },
                    modifier = Modifier.testTag("shelf-${shelf.id}"),
                )
            }
        }
        HorizontalPager(
            state = pager,
            modifier = Modifier.weight(1f).testTag("shelf-pager"),
            beyondViewportPageCount = 1,
            key = { state.shelves[it].id },
        ) { page ->
            val shelf = state.shelves[page]
            if (shelf.items.isEmpty()) {
                val (title, message) = emptyShelf(shelf.id)
                EmptyState(
                    R.drawable.ic_book,
                    title,
                    message,
                    Modifier.padding(below)
                        .testTag(if (shelf.id == "archived") "empty-archived" else "empty-library"),
                )
            } else {
                val pull = rememberPullToRefreshState()
                PullToRefreshBox(
                    isRefreshing = state.refreshing,
                    onRefresh = onRefresh,
                    state = pull,
                    indicator = {
                        PullToRefreshDefaults.LoadingIndicator(
                            state = pull,
                            isRefreshing = state.refreshing,
                            modifier = Modifier.align(Alignment.TopCenter),
                        )
                    },
                ) {
                    CoverGrid(shelf, images, below)
                }
            }
        }
    }
}

@Composable
private fun CoverGrid(shelf: Shelf, images: ImageLoader, contentPadding: PaddingValues) {
    val direction = LocalLayoutDirection.current
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize().testTag("library-${shelf.id}"),
        contentPadding =
            PaddingValues(
                start = 16.dp + contentPadding.calculateStartPadding(direction),
                top = 12.dp + contentPadding.calculateTopPadding(),
                end = 16.dp + contentPadding.calculateEndPadding(direction),
                bottom = 12.dp + contentPadding.calculateBottomPadding(),
            ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(shelf.items, key = { it.id }) { manga ->
            Column(
                Modifier.testTag("manga-${manga.id}"),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                AsyncImage(
                    model = manga.cover,
                    contentDescription = null,
                    imageLoader = images,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier.fillMaxWidth()
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                )
                Text(
                    manga.title,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("manga-title-${manga.id}"),
                )
            }
        }
    }
}
