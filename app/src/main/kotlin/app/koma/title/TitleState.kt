package app.koma.title

import androidx.annotation.DrawableRes
import app.koma.R
import app.koma.data.Candidate
import app.koma.data.ListEntry
import app.koma.data.ListStatus
import app.koma.data.Media
import app.koma.data.MediaDetails
import app.koma.data.Publication
import app.koma.data.Tag
import app.koma.data.chapterNumber
import app.koma.data.isChapterRead
import app.koma.data.withoutOutdatedParts
import eu.kanade.tachiyomi.source.model.SChapter
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
    /** Null when Koma lists no source. */
    val chapters: Chapters? = null,
    /** Whether the chapters are being asked for again while the current ones show. */
    val refreshing: Boolean = false,
    /** The scanlators the bound chapters name; empty offers no filter. */
    val scanlators: List<Scanlator> = emptyList(),
)

/** A scanlator of the bound chapters, and whether its chapters show. */
data class Scanlator(val name: String, val shown: Boolean)

/** Where the title's chapters come from, and what they are. */
sealed interface Chapters {
    data object Loading : Chapters

    data object Failed : Chapters

    /** No source manga is bound: the user picks one of [candidates], or [cancel]s a change. */
    data class Picking(val note: String, val candidates: List<Pick>, val cancel: Boolean) : Chapters

    data class Bound(
        val chapters: List<ChapterRow>,
        /** Whether another source could replace this one. */
        val changeable: Boolean,
        /** Shown in place of an empty list. */
        val note: String?,
    ) : Chapters
}

data class ChapterRow(
    val key: String,
    val name: String,
    val scanlator: String?,
    /** Null when the source gives the chapter no number. */
    val number: Double?,
    /** Whether the user's progress covers it. */
    val read: Boolean = false,
)

/** A source manga the title can be bound to. */
data class Pick(val key: String, val source: String, val title: String)

fun chapterRow(chapter: SChapter) =
    ChapterRow(chapter.url, chapter.name, chapter.scanlator, number(chapter))

fun number(chapter: SChapter): Double? = chapterNumber(chapter.chapter_number)

/** [chapters] with each chapter [progress] reaches marked read; one without a number never is. */
fun readChapters(chapters: Chapters?, progress: Double?): Chapters? =
    if (chapters !is Chapters.Bound) chapters
    else
        chapters.copy(
            chapters =
                chapters.chapters.map { row ->
                    row.copy(read = isChapterRead(row.number, progress))
                }
        )

/** The scanlators [chapters] name, alphabetically; none unless bound. */
fun scanlators(chapters: Chapters?, excluded: Set<String>): List<Scanlator> =
    (chapters as? Chapters.Bound)
        ?.chapters
        .orEmpty()
        .mapNotNull { it.scanlator?.ifEmpty { null } }
        .distinct()
        .sortedWith(String.CASE_INSENSITIVE_ORDER)
        .map { Scanlator(it, it !in excluded) }

/** [chapters] without those of an [excluded] scanlator; a chapter naming none always shows. */
fun shownChapters(chapters: Chapters?, excluded: Set<String>): Chapters? {
    if (chapters !is Chapters.Bound || excluded.isEmpty()) return chapters
    val shown = chapters.chapters.filter { it.scanlator == null || it.scanlator !in excluded }
    return chapters.copy(
        chapters = shown,
        note = chapters.note ?: "Every chapter is filtered out.".takeIf { shown.isEmpty() },
    )
}

fun pickingChapters(candidates: List<Candidate>, cancel: Boolean) =
    Chapters.Picking(
        note =
            when {
                candidates.isEmpty() -> "No source has this title."
                cancel -> "Pick the title on another source."
                else -> "No source matched this title. Pick it below."
            },
        candidates = candidates.map(::pick),
        cancel = cancel,
    )

fun boundChapters(chapters: List<SChapter>, changeable: Boolean) =
    Chapters.Bound(
        chapters = withoutOutdatedParts(chapters).map(::chapterRow),
        changeable = changeable,
        note = "No chapters yet.".takeIf { chapters.isEmpty() },
    )

fun pick(candidate: Candidate) =
    Pick(pickKey(candidate), candidate.sourceName, candidate.manga.title)

fun pickKey(candidate: Candidate) = "${candidate.source}-${candidate.manga.url}"

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
