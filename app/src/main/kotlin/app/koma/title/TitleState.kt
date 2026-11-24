package app.koma.title

import androidx.annotation.DrawableRes
import app.koma.R
import app.koma.data.SeriesDetails
import app.koma.data.SeriesTag
import kotlin.math.roundToInt

/** What the store holds of a series. [state] is its library state, null when not in the library. */
data class StoredTitle(val title: String, val cover: String, val state: String?)

/** A library state the series can be filed under. */
data class Status(val state: String, val label: String, @DrawableRes val icon: Int)

val STATUSES =
    listOf(
        Status("reading", "Reading", R.drawable.ic_book),
        Status("rereading", "Rereading", R.drawable.ic_refresh),
        Status("plan_to_read", "Plan to read", R.drawable.ic_bookmark),
        Status("considering", "Considering", R.drawable.ic_lightbulb),
        Status("completed", "Completed", R.drawable.ic_done_all),
        Status("paused", "Paused", R.drawable.ic_pause),
        Status("dropped", "Dropped", R.drawable.ic_block),
    )

/** The title screen. */
data class TitleState(
    val title: String,
    val cover: String,
    /** Null when the series is not in the library. */
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
    stored: StoredTitle?,
    details: SeriesDetails?,
    loadFailed: Boolean,
    saving: Boolean,
    saveFailed: Boolean,
): TitleState {
    val state = stored?.state
    return TitleState(
        title = details?.title?.ifEmpty { null } ?: stored?.title.orEmpty(),
        cover = details?.cover?.ifEmpty { null } ?: stored?.cover.orEmpty(),
        status =
            state?.let {
                STATUSES.find { it.state == state } ?: Status(state, state, R.drawable.ic_book)
            },
        saving = saving,
        saveFailed = saveFailed,
        about =
            when {
                details != null ->
                    About.Loaded(
                        authors = details.authors.joinToString(", "),
                        publication = PUBLICATIONS[details.status],
                        year = details.startYear?.toString(),
                        rating = details.rating?.roundToInt()?.toString(),
                        summary = details.description,
                        tags = shownTags(details.tags),
                    )
                loadFailed -> About.Failed
                else -> About.Loading
            },
    )
}

private val PUBLICATIONS =
    mapOf(
        "releasing" to "Ongoing",
        "completed" to "Completed",
        "hiatus" to "On hiatus",
        "cancelled" to "Cancelled",
        "upcoming" to "Upcoming",
    )

private val WEIGHTS = listOf("core", "defining", "recurrent", "incidental", "unweighted")

/** Genres first, then the other tags from the most to the least relevant. Spoilers are left out. */
fun shownTags(tags: List<SeriesTag>): List<String> =
    tags
        .filterNot { it.spoiler }
        .sortedWith(
            compareBy(
                { !it.genre },
                { WEIGHTS.indexOf(it.weight).let { rank -> if (rank < 0) WEIGHTS.size else rank } },
            )
        )
        .map { it.name }
        .distinct()
