package app.koma.history

import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.math.floor

sealed interface HistoryItem {
    val id: String

    /** A day's header, such as "Today". */
    data class Day(override val id: String, val label: String) : HistoryItem

    /** One series read on a day: consecutive reads merge into one row. */
    data class Read(
        override val id: String,
        val mediaId: String,
        val title: String,
        val cover: String,
        val detail: String,
    ) : HistoryItem
}

sealed interface HistoryState {
    data class Rows(val items: List<HistoryItem>, val footer: Footer, val refreshing: Boolean) :
        HistoryState

    data object Failed : HistoryState

    data object Loading : HistoryState

    data object Empty : HistoryState

    enum class Footer {
        None,
        Loading,
        Retry,
    }
}

private fun number(chapter: Double): String =
    if (chapter == floor(chapter)) chapter.toLong().toString() else chapter.toString()

internal fun chapterLabel(from: Double?, to: Double): String =
    if (to - (from ?: 0.0) <= 1) "Ch. ${number(to)}"
    else "Ch. ${floor(from ?: 0.0).toLong() + 1}–${number(to)}"

/**
 * Day headers, each followed by that day's reads, newest first. Consecutive reads of one series on
 * one day are one row spanning their chapters.
 */
fun historyItems(
    reads: List<StoredRead>,
    now: Instant,
    zone: ZoneId,
    locale: Locale,
): List<HistoryItem> {
    class Run(val read: StoredRead, var from: Double?)

    val days = mutableListOf<Pair<String, MutableList<Run>>>()
    for (read in reads) {
        val day = Instant.ofEpochMilli(read.at).atZone(zone).toLocalDate().toString()
        if (days.lastOrNull()?.first != day) days += day to mutableListOf()
        val runs = days.last().second
        val last = runs.lastOrNull()
        if (last != null && last.read.seriesId == read.seriesId) last.from = read.from
        else runs += Run(read, read.from)
    }
    return days.flatMap { (day, runs) ->
        val header =
            HistoryItem.Day(
                "day-$day",
                formatDay(Instant.ofEpochMilli(runs[0].read.at), now, zone, locale),
            )
        listOf(header) +
            runs.map { run ->
                val read = run.read
                HistoryItem.Read(
                    // The newest read's id: older pages only extend a row, never renumber it.
                    id = read.id.toString(),
                    mediaId = read.seriesId.toString(),
                    title = read.title,
                    cover = read.cover,
                    detail =
                        "${chapterLabel(run.from, read.to)} · " +
                            formatTime(Instant.ofEpochMilli(read.at), zone, locale),
                )
            }
    }
}

fun historyState(
    data: HistoryData,
    syncing: Boolean,
    syncFailed: Boolean,
    loadingOlder: Boolean,
    olderFailed: Boolean,
    now: Instant,
    zone: ZoneId,
    locale: Locale,
): HistoryState {
    val items = historyItems(data.reads, now, zone, locale)
    return when {
        items.isNotEmpty() ->
            HistoryState.Rows(
                items,
                footer =
                    when {
                        loadingOlder -> HistoryState.Footer.Loading
                        olderFailed -> HistoryState.Footer.Retry
                        else -> HistoryState.Footer.None
                    },
                refreshing = syncing,
            )
        !data.synced && syncFailed -> HistoryState.Failed
        !data.synced -> HistoryState.Loading
        else -> HistoryState.Empty
    }
}
