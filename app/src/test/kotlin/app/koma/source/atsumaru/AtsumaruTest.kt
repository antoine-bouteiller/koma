package app.koma.source.atsumaru

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performScrollToNode
import app.koma.source.sources
import app.koma.testing.AppHarness
import app.koma.testing.BATCH
import app.koma.testing.CHANGES
import app.koma.testing.SERIES
import app.koma.testing.batch
import app.koma.testing.changes
import app.koma.testing.fullSeries
import app.koma.testing.series
import app.koma.testing.upsert
import java.security.MessageDigest
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val SEARCH = "https://atsu.moe/collections/manga/documents/search"
private const val PAGE = "https://atsu.moe/api/manga/page?id="
private const val CHAPTERS = "https://atsu.moe/api/manga/allChapters?mangaId="
private const val ATSUMARU = 2327480808438768017L

@RunWith(RobolectricTestRunner::class)
class AtsumaruTest {
    @get:Rule val app = AppHarness()

    /** MangaBaka's series 3397, Solo Leveling, also titled Na Honjaman Level Up, with Atsumaru. */
    private fun solo() {
        app.signedIn()
        app.network.answer(CHANGES, 200, changes("c1", false, upsert(3397, "reading")))
        app.network.answer(BATCH, 200, batch(series(3397, "Solo Leveling")))
        app.network.answer(
            "$SERIES/3397?schema=full",
            200,
            fullSeries(3397, "Solo Leveling", alternateTitle = "Na Honjaman Level Up"),
        )
        app.sources = ::sources
    }

    private fun shows(tag: String, vararg text: String) {
        app.node("title-content").performScrollToNode(hasTestTag(tag))
        app.node(tag).assertTextEquals(*text)
    }

    private fun filters(query: String): List<String?> =
        app.network.requested("$SEARCH?q=$query").map { it.toHttpUrl().queryParameter("filter_by") }

    @Test
    fun `the finder binds the hit whose page carries the title's MangaBaka id`() {
        solo()
        app.network.answer(
            "$SEARCH?q=Solo%20Leveling",
            200,
            search("A" to "Solo Leveling", "B" to "I Level Up Alone"),
        )
        app.network.answer("${PAGE}A", 200, mangaPage("A", "Solo Leveling", mangaBakaId = "1"))
        app.network.answer(
            "${PAGE}B",
            200,
            mangaPage("B", "I Level Up Alone", mangaBakaId = "3397"),
        )
        app.network.answer("${CHAPTERS}B", 200, """{"chapters":[]}""")
        app.launch()
        app.tap("manga-3397")

        shows("chapters-source", "Atsumaru · I Level Up Alone")
        app.missing("chapters-note")
        assertEquals(
            listOf("medium:!=[`Novel`] && views:>0 && hidden:!=true"),
            filters("Solo%20Leveling"),
        )
    }

    @Test
    fun `a title no page proves lists the search hits as candidates`() {
        solo()
        val hits = search("A" to "Solo Leveling", "B" to "I Level Up Alone")
        app.network.answer("$SEARCH?q=Solo%20Leveling", 200, hits)
        app.network.answer("$SEARCH?q=Na%20Honjaman%20Level%20Up", 200, hits)
        app.network.answer("${PAGE}A", 200, mangaPage("A", "Solo Leveling", mangaBakaId = "1"))
        app.network.answer("${PAGE}B", 200, mangaPage("B", "I Level Up Alone"))
        app.launch()
        app.tap("manga-3397")

        shows("pick-$ATSUMARU-A", "Solo Leveling", "Atsumaru")
        shows("pick-$ATSUMARU-B", "I Level Up Alone", "Atsumaru")
        assertEquals(listOf("${PAGE}A", "${PAGE}B"), app.network.requested(PAGE))
        // The candidates' search, the last, leaves adult titles out.
        assertEquals(
            "medium:!=[`Novel`] && isAdult:=false && views:>0 && hidden:!=true",
            filters("Solo%20Leveling").last(),
        )
    }

    @Test
    fun `each chapter number appears once, from the best scored and then latest upload`() {
        solo()
        app.network.answer("$SEARCH?q=Solo%20Leveling", 200, search("B" to "Solo Leveling"))
        val scanlators =
            """[{"id":"y","name":"Y","score":3},{"id":"z","name":"Z","score":3},""" +
                """{"id":"x","name":"X","score":1}]"""
        app.network.answer(
            "${PAGE}B",
            200,
            mangaPage("B", "Solo Leveling", mangaBakaId = "3397", scanlators = scanlators),
        )
        app.network.answer(
            "${CHAPTERS}B",
            200,
            """{"chapters":[${chapter("c5y", 5f, "y", 1)},${chapter("c5z", 5f, "z", 2)},""" +
                """${chapter("c5x", 5f, "x", 3)},${chapter("c45x", 4.5f, "x", 1)}]}""",
        )
        app.launch()
        app.tap("manga-3397")

        shows("chapter-B/c5z", "Chapter 5.0", "Z")
        shows("chapter-B/c45x", "Chapter 4.5", "X")
        app.missing("chapter-B/c5y")
        app.missing("chapter-B/c5x")
    }

    @Test
    fun `Atsumaru keeps the published extension's id`() {
        val bytes = MessageDigest.getInstance("MD5").digest("atsumaru/en/2".toByteArray())
        val id =
            (0..7).fold(0L) { id, i -> id shl 8 or (bytes[i].toLong() and 0xff) } and Long.MAX_VALUE
        assertEquals(id, AtsumaruSource().id)
    }
}

private fun search(vararg hits: Pair<String, String>) =
    """{"page":1,"found":${hits.size},"request_params":{"per_page":40},"hits":[""" +
        hits.joinToString(",") { (id, title) ->
            """{"document":{"id":"$id","title":"$title","posterMedium":"/static/posters/$id-medium.avif"}}"""
        } +
        "]}"

private fun mangaPage(
    id: String,
    title: String,
    mangaBakaId: String? = null,
    scanlators: String = "[]",
) =
    """{"mangaPage":{"id":"$id","title":"$title","poster":{"largeImage":"posters/$id-large.avif"},""" +
        """"authors":[],"genres":[],"tags":[],"otherNames":[],"scanlators":$scanlators,""" +
        """"mangaBakaId":${mangaBakaId?.let { "\"$it\"" } ?: "null"}}}"""

private fun chapter(id: String, number: Float, scanlator: String, day: Int) =
    """{"id":"$id","number":$number,"title":"Chapter $number","scanlationMangaId":"$scanlator",""" +
        """"createdAt":${day * 86_400_000L}}"""
