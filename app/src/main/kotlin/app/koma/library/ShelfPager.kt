package app.koma.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.koma.R
import app.koma.data.MediaRef
import app.koma.ui.EmptyState
import app.koma.ui.HeaderAccessory
import app.koma.ui.TrackHeaderBackdrop
import coil3.ImageLoader
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch

/** The shelves' tabs, shown in the screen header, over a pager of their cover grids. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShelfPager(
    state: LibraryState.Shelves,
    backend: String,
    images: ImageLoader,
    contentPadding: PaddingValues,
    onRefresh: () -> Unit,
    onOpen: (MediaRef) -> Unit,
) {
    val pager = rememberPagerState { state.shelves.size }
    val scope = rememberCoroutineScope()
    HeaderAccessory {
        PrimaryScrollableTabRow(
            selectedTabIndex = pager.currentPage,
            edgePadding = 0.dp,
            containerColor = Color.Transparent,
            divider = {},
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
    }
    HorizontalPager(
        state = pager,
        modifier = Modifier.fillMaxSize().testTag("shelf-pager"),
        beyondViewportPageCount = 1,
        key = { state.shelves[it].id },
    ) { page ->
        val shelf = state.shelves[page]
        if (shelf.items.isEmpty()) {
            val (title, message) = emptyShelf(shelf.id, backend)
            EmptyState(
                R.drawable.ic_book,
                title,
                message,
                Modifier.padding(contentPadding)
                    .testTag(if (shelf.id == "archived") "empty-archived" else "empty-library"),
            )
        } else {
            val grid = rememberLazyGridState()
            val inView by remember {
                derivedStateOf { pager.layoutInfo.visiblePagesInfo.any { it.index == page } }
            }
            // Mid-swipe, the shelf leaving may still run under the header.
            if (inView) TrackHeaderBackdrop(grid)
            val pull = rememberPullToRefreshState()
            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = onRefresh,
                state = pull,
                indicator = {
                    PullToRefreshDefaults.LoadingIndicator(
                        state = pull,
                        isRefreshing = state.refreshing,
                        modifier =
                            Modifier.align(Alignment.TopCenter)
                                .padding(top = contentPadding.calculateTopPadding()),
                    )
                },
            ) {
                CoverGrid(shelf, images, grid, contentPadding, onOpen)
            }
        }
    }
}

@Composable
private fun CoverGrid(
    shelf: Shelf,
    images: ImageLoader,
    grid: LazyGridState,
    contentPadding: PaddingValues,
    onOpen: (MediaRef) -> Unit,
) {
    val direction = LocalLayoutDirection.current
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        state = grid,
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
        items(shelf.items, key = { it.ref.id }) { manga ->
            Column(
                Modifier.animateItem()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(role = Role.Button) { onOpen(manga.ref) }
                    .testTag("manga-${manga.ref.id}"),
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
                )
            }
        }
    }
}
