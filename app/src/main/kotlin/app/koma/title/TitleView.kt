package app.koma.title

import android.os.Build
import androidx.annotation.DrawableRes
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.koma.AppContainer
import app.koma.R
import app.koma.ui.GroupRow
import app.koma.ui.PillButton
import app.koma.ui.PillShape
import app.koma.ui.SettingsGroup
import app.koma.ui.SettingsRow
import app.koma.ui.TitleFont
import app.koma.ui.TrackHeaderBackdrop
import coil3.ImageLoader
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch

/** A series of the library: its cover and facts, its library status, summary and tags. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TitleView(container: AppContainer, id: Long, contentPadding: PaddingValues) {
    val model = viewModel(key = "title-$id") { TitleViewModel(container, id) }
    val state by model.state.collectAsStateWithLifecycle()
    var picking by rememberSaveable { mutableStateOf(false) }
    val list = rememberLazyListState()
    TrackHeaderBackdrop(list)
    LazyColumn(
        state = list,
        contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize().testTag("title"),
    ) {
        item(key = "header") {
            Header(state, container.images, contentPadding.calculateTopPadding())
        }
        item(key = "status") { StatusButton(state, onClick = { picking = true }) }
        when (val about = state.about) {
            About.Loading ->
                item(key = "loading") {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        LoadingIndicator(Modifier.size(48.dp).testTag("title-loading"))
                    }
                }
            About.Failed ->
                item(key = "failed") {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            "MangaBaka did not answer.",
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
                if (about.summary.isNotEmpty()) item(key = "summary") { Summary(about.summary) }
                if (about.tags.isNotEmpty()) item(key = "tags") { Tags(about.tags) }
            }
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
}

/** The cover over its own blurred, faded copy, beside the title and MangaBaka's facts. */
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
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (about.rating != null) {
                Fact(about.rating, "title-rating", R.drawable.ic_star)
            }
            if (about.release.isNotEmpty()) Fact(about.release, "title-release")
        }
    }
}

/** A short fact of the series, in a tonal pill. */
@Composable
private fun Fact(text: String, tag: String, @DrawableRes icon: Int? = null) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.clip(PillShape)
            .background(colors.secondaryContainer)
            .padding(
                start = if (icon != null) 8.dp else 12.dp,
                end = 12.dp,
                top = 6.dp,
                bottom = 6.dp,
            ),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                painterResource(icon),
                contentDescription = null,
                tint = colors.onSecondaryContainer,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = colors.onSecondaryContainer,
            maxLines = 1,
            modifier = Modifier.testTag(tag),
        )
    }
}

/** The series' library status; it opens [StatusSheet] to change it. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StatusButton(state: TitleState, onClick: () -> Unit) {
    Column(
        Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val height = ButtonDefaults.MediumContainerHeight
        FilledTonalButton(
            onClick = onClick,
            shapes = ButtonDefaults.shapesFor(height),
            enabled = !state.saving,
            contentPadding = ButtonDefaults.contentPaddingFor(height),
            modifier = Modifier.fillMaxWidth().heightIn(min = height).testTag("title-status"),
        ) {
            if (state.saving) {
                LoadingIndicator(Modifier.size(ButtonDefaults.iconSizeFor(height)))
            } else {
                Icon(
                    painterResource(state.status?.icon ?: R.drawable.ic_bookmark),
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.iconSizeFor(height)),
                )
            }
            Text(
                state.status?.label ?: "Add to Library",
                style = ButtonDefaults.textStyleFor(height),
                modifier = Modifier.padding(start = ButtonDefaults.iconSpacingFor(height)),
            )
        }
        if (state.saveFailed) {
            Text(
                "MangaBaka did not save the status. Try again.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth().testTag("title-status-error"),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatusSheet(current: Status?, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Status",
                style =
                    MaterialTheme.typography.titleLarge.copy(
                        fontFamily = TitleFont,
                        fontWeight = FontWeight.SemiBold,
                    ),
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            SettingsGroup(
                STATUSES.map { status ->
                    GroupRow { shape ->
                        SettingsRow(
                            title = status.label,
                            icon = status.icon,
                            shape = shape,
                            tag = "status-${status.state}",
                            onClick = {
                                scope
                                    .launch { sheet.hide() }
                                    .invokeOnCompletion { onPick(status.state) }
                            },
                            trailing = {
                                if (status.state == current?.state) {
                                    Icon(
                                        painterResource(R.drawable.ic_check),
                                        contentDescription = "Current",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                            },
                        )
                    }
                }
            )
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
    Column(
        Modifier.fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = expanded || overflows, role = Role.Button) {
                expanded = !expanded
            }
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
