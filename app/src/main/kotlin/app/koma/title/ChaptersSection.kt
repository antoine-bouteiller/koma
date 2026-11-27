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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.koma.ui.PillButton
import app.koma.ui.TitleFont

/** The title's chapters, from its bound source, or the source manga it could be bound to. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun LazyListScope.chapters(chapters: Chapters, model: TitleViewModel) {
    item(key = "chapters-header") { ChaptersHeader(chapters, model) }
    when (chapters) {
        Chapters.Loading ->
            item(key = "chapters-loading") {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    LoadingIndicator(Modifier.size(40.dp).testTag("chapters-loading"))
                }
            }
        Chapters.Failed ->
            item(key = "chapters-failed") {
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
            item(key = "chapters-picking") {
                Note(
                    when {
                        chapters.candidates.isEmpty() -> "No source has this title."
                        chapters.cancel -> "Pick the title on another source."
                        else -> "No source matched this title. Pick it below."
                    },
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
                item(key = "chapters-keep") {
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
            if (chapters.chapters.isEmpty()) {
                item(key = "chapters-empty") {
                    Note("No chapters yet.", Modifier.padding(horizontal = 16.dp))
                }
            }
            items(chapters.chapters, key = { "chapter-${it.key}" }) { chapter ->
                Line(chapter.name, chapter.scanlator, Modifier.testTag("chapter-${chapter.key}"))
            }
        }
    }
}

@Composable
private fun ChaptersHeader(chapters: Chapters, model: TitleViewModel) {
    val bound = chapters as? Chapters.Bound
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("chapters-header"),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "Chapters",
                style =
                    MaterialTheme.typography.titleLarge.copy(
                        fontFamily = TitleFont,
                        fontWeight = FontWeight.SemiBold,
                    ),
            )
            if (bound != null) {
                Text(
                    bound.source,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("chapters-source"),
                )
            }
        }
        if (bound?.changeable == true) {
            PillButton(
                "Change",
                onClick = model::change,
                outlined = true,
                modifier = Modifier.testTag("chapters-change"),
            )
        }
    }
}

/** A chapter or a source manga: its name over a smaller detail. */
@Composable
private fun Line(name: String, detail: String?, modifier: Modifier) {
    Column(
        modifier
            .semantics(mergeDescendants = true) {}
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
        if (!detail.isNullOrEmpty()) {
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
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
