package app.koma.title

import androidx.annotation.DrawableRes
import app.koma.R
import app.koma.data.ListEntry
import app.koma.data.ListStatus
import app.koma.data.Media
import app.koma.data.MediaDetails
import app.koma.data.Publication
import app.koma.data.Tag
import kotlin.math.round

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

/** The user's rating of a title in the library. */
data class Rating(
    /** From 0 to 5; 0 when unrated. */
    val score: Double,
    /** As "4.5", or "Rate" when unrated. */
    val label: String,
    @DrawableRes val icon: Int,
)

/** What the title screen writes to the library. */
enum class Change {
    Status,
    Rating,
}

/** The title screen. */
data class TitleState(
    val title: String,
    val cover: String,
    /** Null when the title is not in the library. */
    val status: Status?,
    /** Null when the title is not in the library. */
    val rating: Rating?,
    /** The change being saved. */
    val saving: Change?,
    /** Why the last change was not saved. */
    val saveError: String?,
    val about: About,
    val ready: Boolean,
    val seed: Int?,
)

/** What the backend says of the title beyond its name and cover. */
sealed interface About {
    data object Loading : About

    data object Failed : About

    data class Loaded(
        /** Empty when the backend names none. */
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
    backend: String,
    media: Media?,
    entry: ListEntry?,
    details: MediaDetails?,
    loadFailed: Boolean,
    saving: Change?,
    saveFailed: Change?,
    colorsReady: Boolean,
    seed: Int?,
): TitleState =
    TitleState(
        title = details?.media?.title?.ifEmpty { null } ?: media?.title.orEmpty(),
        cover = details?.media?.cover?.ifEmpty { null } ?: media?.cover.orEmpty(),
        status = entry?.let { e -> STATUSES.first { it.status == e.status } },
        rating =
            entry?.let {
                val score = it.score ?: 0.0
                if (score > 0) Rating(score, score.toString(), R.drawable.ic_star)
                else Rating(score, "Rate", R.drawable.ic_star_outline)
            },
        saving = saving,
        saveError =
            saveFailed?.let { "$backend did not save the ${it.name.lowercase()}. Try again." },
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
        ready = colorsReady && (details != null || loadFailed),
        seed = seed,
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

/** The step a rating moves by on the gauge. */
const val RATING_STEP = 0.5

/** [value] on the gauge, to the nearest step from 0 to 5. */
fun snapRating(value: Float): Double = (round(value / RATING_STEP) * RATING_STEP).coerceIn(0.0, 5.0)

/** What a rating says, as "Liked it". */
fun ratingWord(score: Double): String =
    when {
        score <= 0 -> "Not rated"
        score <= 1 -> "Hated it"
        score <= 2 -> "Didn't like it"
        score <= 3 -> "It was okay"
        score <= 4 -> "Liked it"
        score < 5 -> "Really liked it"
        else -> "Loved it"
    }
