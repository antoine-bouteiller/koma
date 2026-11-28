package app.koma.title

import android.os.Build
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.koma.AppContainer
import app.koma.R
import app.koma.data.ListStatus
import app.koma.data.MediaRef
import app.koma.ui.ConnectedShapes
import app.koma.ui.PillButton
import app.koma.ui.PillShape
import app.koma.ui.ScreenColors
import app.koma.ui.TitleFont
import app.koma.ui.TrackHeaderBackdrop
import app.koma.ui.morphingShape
import app.koma.ui.theme.seededColors
import coil3.ImageLoader
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch

/** A series of the library: its cover and facts, its library status, summary and tags. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TitleView(container: AppContainer, ref: MediaRef, contentPadding: PaddingValues) {
    val model = viewModel(key = "title-${ref.backend}-${ref.id}") { TitleViewModel(container, ref) }
    val state by model.state.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize().testTag("title")) {
        ScreenColors(seededColors(state.seed), waiting = { TitleLoading(contentPadding) }) {
            if (state.ready) TitleContent(container, model, state, contentPadding)
            else TitleLoading(contentPadding)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TitleContent(
    container: AppContainer,
    model: TitleViewModel,
    state: TitleState,
    contentPadding: PaddingValues,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    var rating by rememberSaveable { mutableStateOf(false) }
    val list = rememberLazyListState()
    TrackHeaderBackdrop(list)
    val pull = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = model::refresh,
        state = pull,
        enabled = state.chapters != null,
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
        LazyColumn(
            state = list,
            contentPadding =
                PaddingValues(bottom = contentPadding.calculateBottomPadding() + 16.dp),
            modifier = Modifier.fillMaxSize().testTag("title-content"),
        ) {
            item(key = "header") {
                Header(state, container.images, contentPadding.calculateTopPadding())
            }
            section(key = "status") {
                StatusButton(state, onPick = { picking = true }, onRate = { rating = true })
            }
            when (val about = state.about) {
                About.Loading -> Unit
                About.Failed ->
                    section(key = "failed") {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                "${model.backend} did not answer.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            PillButton(
                                "Try Again",
                                onClick = model::load,
                                outlined = true,
                                modifier = Modifier.testTag("title-retry"),
                            )
                        }
                    }
                is About.Loaded -> {
                    if (about.summary.isNotEmpty())
                        section(key = "summary") { Summary(about.summary) }
                    if (about.tags.isNotEmpty()) section(key = "tags") { Tags(about.tags) }
                }
            }
            state.chapters?.let { chapters(it, model) }
        }
    }
    if (picking) {
        StatusSheet(
            state.status,
            onPick = {
                picking = false
                model.setStatus(it)
            },
            onDismiss = { picking = false },
        )
    }
    val score = state.rating?.score
    if (rating && score != null) {
        RatingSheet(
            score,
            onRate = {
                rating = false
                model.setRating(it)
            },
            onDismiss = { rating = false },
        )
    }
}

/** An item set apart from the one above it; list rows use plain items and sit closer. */
internal fun LazyListScope.section(key: String, content: @Composable () -> Unit) =
    item(key = key) { Box(Modifier.padding(top = 16.dp)) { content() } }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TitleLoading(contentPadding: PaddingValues) {
    Box(
        Modifier.fillMaxSize().padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        LoadingIndicator(Modifier.size(48.dp).testTag("title-loading"))
    }
}

/** The cover over its own blurred, faded copy, beside the title and the backend's facts. */
@Composable
private fun Header(state: TitleState, images: ImageLoader, top: Dp) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth()) {
        AsyncImage(
            model = state.cover,
            contentDescription = null,
            imageLoader = images,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier.matchParentSize()
                    // Blur needs Android 12; older ones fade the cover further instead.
                    .graphicsLayer {
                        alpha = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 0.4f else 0.2f
                    }
                    .blur(24.dp),
        )
        Box(
            Modifier.matchParentSize()
                .background(Brush.verticalGradient(listOf(Color.Transparent, colors.surface)))
        )
        Row(
            Modifier.padding(start = 16.dp, end = 16.dp, top = top + 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            AsyncImage(
                model = state.cover,
                contentDescription = null,
                imageLoader = images,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier.width(120.dp)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.surfaceContainerHighest)
                        .testTag("title-cover"),
            )
            Facts(state, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Facts(state: TitleState, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val about = state.about as? About.Loaded
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            state.title,
            style =
                MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = TitleFont,
                    fontWeight = FontWeight.SemiBold,
                ),
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("title-name"),
        )
        if (about == null) return@Column
        if (about.authors.isNotEmpty()) {
            Text(
                about.authors,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("title-authors"),
            )
        }
        val facts =
            listOfNotNull(
                about.rating?.let { Fact(it, "title-rating", R.drawable.ic_star) },
                about.publication?.let { Fact(it, "title-publication") },
                about.year?.let { Fact(it, "title-year") },
            )
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            facts.forEachIndexed { index, fact ->
                if (index > 0) {
                    Text("·", style = MaterialTheme.typography.labelLarge, color = colors.outline)
                }
                FactText(fact)
            }
        }
    }
}

/** A short fact of the series. */
private class Fact(val text: String, val tag: String, @DrawableRes val icon: Int? = null)

@Composable
private fun FactText(fact: Fact) {
    val colors = MaterialTheme.colorScheme
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (fact.icon != null) {
            Icon(
                painterResource(fact.icon),
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            fact.text,
            style = MaterialTheme.typography.labelLarge,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.testTag(fact.tag),
        )
    }
}

/**
 * The series' library status and, once the library holds it, the user's rating, as one split
 * button. A pressed half widens at the other's expense.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StatusButton(state: TitleState, onPick: () -> Unit, onRate: () -> Unit) {
    val motion = MaterialTheme.motionScheme
    val height = ButtonDefaults.MediumContainerHeight
    val iconSize = ButtonDefaults.iconSizeFor(height)
    val textStyle = ButtonDefaults.textStyleFor(height)
    val statusSource = remember { MutableInteractionSource() }
    val ratingSource = remember { MutableInteractionSource() }
    val statusPressed by statusSource.collectIsPressedAsState()
    val ratingPressed by ratingSource.collectIsPressedAsState()
    val rating = state.rating
    Column(
        Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            FilledTonalButton(
                onClick = onPick,
                shape =
                    morphingShape(
                        if (rating == null) PillShape else ConnectedShapes.Pill.inRow(0, 2),
                        statusPressed,
                    ),
                enabled = state.saving == null,
                contentPadding = ButtonDefaults.contentPaddingFor(height),
                interactionSource = statusSource,
                modifier = Modifier.weight(1f).heightIn(min = height).testTag("title-status"),
            ) {
                if (state.saving == Change.Status) {
                    LoadingIndicator(Modifier.size(iconSize))
                } else {
                    Icon(
                        painterResource(state.status?.icon ?: R.drawable.ic_bookmark),
                        contentDescription = null,
                        modifier = Modifier.size(iconSize),
                    )
                }
                Text(
                    state.status?.label ?: "Add to Library",
                    style = textStyle,
                    modifier = Modifier.padding(start = ButtonDefaults.iconSpacingFor(height)),
                )
            }
            AnimatedVisibility(
                rating != null,
                enter = expandHorizontally(motion.defaultSpatialSpec()) + fadeIn(),
                exit = shrinkHorizontally(motion.fastSpatialSpec()) + fadeOut(),
            ) {
                val width by
                    animateDpAsState(
                        when {
                            ratingPressed -> RatingWidth * 1.15f
                            statusPressed -> RatingWidth * 0.85f
                            else -> RatingWidth
                        },
                        motion.fastSpatialSpec(),
                    )
                FilledTonalButton(
                    onClick = onRate,
                    shape = morphingShape(ConnectedShapes.Pill.inRow(1, 2), ratingPressed),
                    enabled = state.saving == null,
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    interactionSource = ratingSource,
                    modifier =
                        Modifier.widthIn(min = width).heightIn(min = height).testTag("title-score"),
                ) {
                    if (state.saving == Change.Rating) {
                        LoadingIndicator(Modifier.size(20.dp))
                    } else {
                        Icon(
                            painterResource(rating?.icon ?: R.drawable.ic_star_outline),
                            contentDescription = "Your rating",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Text(
                        rating?.label.orEmpty(),
                        style = textStyle,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        }
        if (state.saveError != null) {
            Text(
                state.saveError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth().testTag("title-status-error"),
            )
        }
    }
}

private val RatingWidth = 96.dp

/** The statuses as a grid of tiles, two to a row; a lone last tile spans the row. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatusSheet(current: Status?, onPick: (ListStatus) -> Unit, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    // The tapped tile takes the selection while the sheet slides away.
    var chosen by remember { mutableStateOf(current?.status) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Status",
                style =
                    MaterialTheme.typography.titleLarge.copy(
                        fontFamily = TitleFont,
                        fontWeight = FontWeight.SemiBold,
                    ),
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 4.dp),
            )
            for (row in STATUSES.chunked(2)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (status in row) {
                        StatusTile(
                            status,
                            selected = status.status == chosen,
                            wide = row.size == 1,
                            onClick = {
                                chosen = status.status
                                scope
                                    .launch { sheet.hide() }
                                    .invokeOnCompletion { onPick(status.status) }
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/**
 * A status of the [StatusSheet]: round on the sheet, squarer and filled in primary once [selected].
 * A [wide] tile sets its icon beside its label rather than above it.
 */
@Composable
private fun StatusTile(
    status: Status,
    selected: Boolean,
    wide: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Color>()
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val container by
        animateColorAsState(if (selected) colors.primary else colors.surfaceContainerHigh, effects)
    val content by
        animateColorAsState(if (selected) colors.onPrimary else colors.onSurface, effects)
    val icon by animateColorAsState(if (selected) colors.onPrimary else colors.primary, effects)
    Surface(
        selected = selected,
        onClick = onClick,
        shape = morphingShape(RoundedCornerShape(if (selected) 12.dp else 28.dp), pressed),
        color = container,
        contentColor = content,
        interactionSource = source,
        modifier =
            modifier
                .heightIn(min = if (wide) 64.dp else 88.dp)
                .semantics { role = Role.RadioButton }
                .testTag("status-${status.status.name.lowercase()}"),
    ) {
        val label: @Composable () -> Unit = {
            Icon(
                painterResource(status.icon),
                contentDescription = null,
                tint = icon,
                modifier = Modifier.size(24.dp),
            )
            Text(status.label, style = MaterialTheme.typography.titleSmall, maxLines = 1)
        }
        if (wide) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                label()
            }
        } else {
            Column(
                Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            ) {
                label()
            }
        }
    }
}

/** The summary, folded to a few lines until it is tapped. */
@Composable
private fun Summary(text: String) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var overflows by remember { mutableStateOf(false) }
    val turn by
        animateFloatAsState(
            if (expanded) 180f else 0f,
            MaterialTheme.motionScheme.defaultSpatialSpec(),
        )
    // The text sits inside the clip so its rounded corners never cut a letter.
    Column(
        Modifier.fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = expanded || overflows, role = Role.Button) {
                expanded = !expanded
            }
            .padding(8.dp)
            .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec())
            .testTag("title-summary"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (expanded) Int.MAX_VALUE else SummaryLines,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow },
            modifier = Modifier.fillMaxWidth(),
        )
        if (expanded || overflows) {
            Icon(
                painterResource(R.drawable.ic_expand_more),
                contentDescription = if (expanded) "Show less" else "Show more",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp).rotate(turn),
            )
        }
    }
}

private const val SummaryLines = 3

/** The tags, on one line that scrolls sideways. */
@Composable
private fun Tags(tags: List<String>) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().testTag("title-tags"),
    ) {
        items(tags, key = { it }) { tag ->
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.testTag("tag-$tag"),
            ) {
                Text(
                    tag,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}
