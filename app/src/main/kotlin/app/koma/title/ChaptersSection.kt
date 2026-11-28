package app.koma.title

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.koma.ui.PillButton
import app.koma.ui.TitleFont

/** The title's chapters, from its bound source, or the source manga it could be bound to. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun LazyListScope.chapters(chapters: Chapters, model: TitleViewModel) {
    section(key = "chapters-header") { ChaptersHeader(chapters, model) }
    when (chapters) {
        Chapters.Loading ->
            section(key = "chapters-loading") {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    LoadingIndicator(Modifier.size(40.dp).testTag("chapters-loading"))
                }
            }
        Chapters.Failed ->
            section(key = "chapters-failed") {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Note("The source did not answer.")
                    PillButton(
                        "Try Again",
                        onClick = model::loadChapters,
                        outlined = true,
                        modifier = Modifier.testTag("chapters-retry"),
                    )
                }
            }
        is Chapters.Picking -> {
            section(key = "chapters-picking") {
                Note(
                    chapters.note,
                    Modifier.padding(horizontal = 16.dp).testTag("chapters-note"),
                )
            }
            items(chapters.candidates, key = { "pick-${it.key}" }) { pick ->
                Line(
                    pick.title,
                    pick.source,
                    Modifier.clickable(role = Role.Button) { model.pick(pick.key) }
                        .testTag("pick-${pick.key}"),
                )
            }
            if (chapters.cancel) {
                section(key = "chapters-keep") {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        PillButton(
                            "Keep Current",
                            onClick = model::keep,
                            outlined = true,
                            modifier = Modifier.testTag("chapters-keep"),
                        )
                    }
                }
            }
        }
        is Chapters.Bound -> {
            chapters.note?.let { note ->
                section(key = "chapters-empty") {
                    Note(note, Modifier.padding(horizontal = 16.dp).testTag("chapters-empty"))
                }
            }
            items(chapters.chapters, key = { "chapter-${it.key}" }) { chapter ->
                Line(
                    chapter.name,
                    chapter.scanlator,
                    Modifier.testTag("chapter-${chapter.key}"),
                    read = chapter.read,
                )
            }
        }
    }
}

@Composable
private fun ChaptersHeader(chapters: Chapters, model: TitleViewModel) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("chapters-header"),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Chapters",
            style =
                MaterialTheme.typography.titleLarge.copy(
                    fontFamily = TitleFont,
                    fontWeight = FontWeight.SemiBold,
                ),
            modifier = Modifier.weight(1f),
        )
        if ((chapters as? Chapters.Bound)?.changeable == true) {
            PillButton(
                "Change",
                onClick = model::change,
                outlined = true,
                modifier = Modifier.testTag("chapters-change"),
            )
        }
    }
}

/** A chapter or a source manga: its name over a smaller detail, greyed out once [read]. */
@Composable
private fun Line(name: String, detail: String?, modifier: Modifier, read: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    // Material's disabled-content opacity.
    val alpha = if (read) 0.38f else 1f
    Column(
        modifier
            .semantics(mergeDescendants = true) { if (read) stateDescription = "Read" }
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            name,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurface.copy(alpha = alpha),
            maxLines = 2,
        )
        if (!detail.isNullOrEmpty()) {
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant.copy(alpha = alpha),
                maxLines = 1,
            )
        }
    }
}

/** The scanlators of the bound chapters, each checked while its chapters show. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanlatorSheet(
    scanlators: List<Scanlator>,
    onShow: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.padding(bottom = 16.dp).testTag("scanlators")) {
            Text(
                "Scanlators",
                style =
                    MaterialTheme.typography.titleLarge.copy(
                        fontFamily = TitleFont,
                        fontWeight = FontWeight.SemiBold,
                    ),
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
            )
            for (scanlator in scanlators) {
                Row(
                    Modifier.fillMaxWidth()
                        .toggleable(scanlator.shown, role = Role.Checkbox) {
                            onShow(scanlator.name, it)
                        }
                        .padding(horizontal = 12.dp)
                        .testTag("scanlator-${scanlator.name}"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(scanlator.shown, onCheckedChange = null, Modifier.padding(12.dp))
                    Text(
                        scanlator.name,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun Note(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}
