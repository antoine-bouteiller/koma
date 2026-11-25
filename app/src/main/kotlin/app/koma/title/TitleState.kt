package app.koma.title

import androidx.annotation.DrawableRes
import app.koma.R
import app.koma.data.ListStatus
import app.koma.data.Media
import app.koma.data.MediaDetails
import app.koma.data.Publication
import app.koma.data.Tag

/** A library status the title can be filed under. */
data class Status(val status: ListStatus, val label: String, @DrawableRes val icon: Int)

val STATUSES =
    listOf(
        Status(ListStatus.Reading, "Reading", R.drawable.ic_book),
        Status(ListStatus.Rereading, "Rereading", R.drawable.ic_refresh),
        Status(ListStatus.Planning, "Plan to read", R.drawable.ic_bookmark),
        Status(ListStatus.Considering, "Considering", R.drawable.ic_lightbulb),
        Status(ListStatus.Completed, "Completed", R.drawable.ic_done_all),
        Status(ListStatus.Paused, "Paused", R.drawable.ic_pause),
        Status(ListStatus.Dropped, "Dropped", R.drawable.ic_block),
    )

/** The title screen. */
data class TitleState(
    val title: String,
    val cover: String,
    /** Null when the title is not in the library. */
    val status: Status?,
    val saving: Boolean,
    val saveFailed: Boolean,
    val about: About,
)

/** What MangaBaka says of the series beyond its title and cover. */
sealed interface About {
    data object Loading : About

    data object Failed : About

    data class Loaded(
        /** Empty when MangaBaka names none. */
        val authors: String,
        /** The publication status, as "Ongoing". */
        val publication: String?,
        /** The year publication started. */
        val year: String?,
        val rating: String?,
        val summary: String,
        val tags: List<String>,
    ) : About
}

fun titleState(
    media: Media?,
    status: ListStatus?,
    details: MediaDetails?,
    loadFailed: Boolean,
    saving: Boolean,
    saveFailed: Boolean,
): TitleState =
    TitleState(
        title = details?.media?.title?.ifEmpty { null } ?: media?.title.orEmpty(),
        cover = details?.media?.cover?.ifEmpty { null } ?: media?.cover.orEmpty(),
        status = status?.let { s -> STATUSES.first { it.status == s } },
        saving = saving,
        saveFailed = saveFailed,
        about =
            when {
                details != null ->
                    About.Loaded(
                        authors = details.authors.joinToString(", "),
                        publication = PUBLICATIONS[details.publication],
                        year = details.startYear?.toString(),
                        rating = details.media.score?.toString(),
                        summary = details.description,
                        tags = shownTags(details.tags),
                    )
                loadFailed -> About.Failed
                else -> About.Loading
            },
    )

private val PUBLICATIONS =
    mapOf(
        Publication.Releasing to "Ongoing",
        Publication.Completed to "Completed",
        Publication.Hiatus to "On hiatus",
        Publication.Cancelled to "Cancelled",
        Publication.Upcoming to "Upcoming",
    )

/** Genres first, then the other tags from the most to the least relevant. Spoilers are left out. */
fun shownTags(tags: List<Tag>): List<String> =
    tags
        .filterNot { it.spoiler }
        .sortedWith(compareBy({ !it.genre }, { -(it.rank ?: -1) }))
        .map { it.name }
        .distinct()
