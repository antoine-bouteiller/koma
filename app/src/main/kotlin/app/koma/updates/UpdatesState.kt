package app.koma.updates

import app.koma.data.MediaRef
import app.koma.data.NewChapter
import app.koma.ui.formatDay
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

sealed interface UpdatesItem {
    val id: String

    /** A day's header, such as "Today". */
    data class Day(override val id: String, val label: String) : UpdatesItem

    /** A chapter found on [ref]'s title. */
    data class Chapter(
        override val id: String,
        val ref: MediaRef,
        val title: String,
        val cover: String,
        val chapter: String,
    ) : UpdatesItem
}

sealed interface UpdatesState {
    data object Loading : UpdatesState

    data object Empty : UpdatesState

    data class Rows(val items: List<UpdatesItem>) : UpdatesState
}

/** Day headers, each followed by the chapters found that day, in [found]'s order. */
fun updatesState(
    found: List<NewChapter>,
    now: Instant,
    zone: ZoneId,
    locale: Locale,
): UpdatesState {
    if (found.isEmpty()) return UpdatesState.Empty
    val items =
        found
            .groupBy { Instant.ofEpochMilli(it.foundAt).atZone(zone).toLocalDate() }
            .flatMap { (day, chapters) ->
                val header =
                    UpdatesItem.Day(
                        "day-$day",
                        formatDay(Instant.ofEpochMilli(chapters[0].foundAt), now, zone, locale),
                    )
                listOf(header) +
                    chapters.map {
                        UpdatesItem.Chapter(
                            id = "${it.media.ref.id}:${it.url}",
                            ref = it.media.ref,
                            title = it.media.title,
                            cover = it.media.cover,
                            chapter = it.name,
                        )
                    }
            }
    return UpdatesState.Rows(items)
}
