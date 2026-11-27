package app.koma.testing

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.parseAs
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl

const val TEST_SOURCE = "https://source.test"

/**
 * A source on Mihon's extension API with ids, titles and a site Atsumaru could not produce. Its
 * search, chapters and pages answer the JSON of [mangas], [chapters] and [pages].
 */
class TestSource(private val label: String = "Test Source", private val key: Long = 77L) :
    KeiSource() {
    override val name: String
        get() = label

    override val lang: String
        get() = "xx"

    override val id: Long
        get() = key

    override val baseUrl: String
        get() = TEST_SOURCE

    override suspend fun getPopularManga(page: Int): MangasPage =
        throw UnsupportedOperationException()

    override suspend fun getLatestUpdates(page: Int): MangasPage =
        throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage {
        val url = "$baseUrl/search".toHttpUrl().newBuilder().addQueryParameter("q", query).build()
        val found = client.get(url, Fetch.XHR).parseAs<List<Hit>>()
        return MangasPage(
            found.map {
                SManga.create().apply {
                    this.url = it.url
                    title = it.title
                }
            },
            hasNextPage = false,
        )
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val listed = client.get("$baseUrl/manga/${manga.url}", Fetch.XHR).parseAs<List<Chapter>>()
        return SMangaUpdate(
            manga,
            listed.map {
                SChapter.create().apply {
                    url = it.url
                    name = it.name
                    chapter_number = it.number
                    scanlator = it.scanlator
                }
            },
        )
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> =
        client
            .get("$baseUrl/chapter/${chapter.url}", Fetch.XHR)
            .parseAs<List<String>>()
            .mapIndexed { index, image ->
                Page(index, imageUrl = image)
            }

    @Serializable private class Hit(val url: String, val title: String)

    @Serializable
    private class Chapter(
        val url: String,
        val name: String,
        val number: Float,
        val scanlator: String? = null,
    )
}

/** A search answer: each pair is a manga's url and title. */
fun mangas(vararg hits: Pair<String, String>) =
    hits.joinToString(",", "[", "]") { (url, title) -> """{"url":"$url","title":"$title"}""" }

fun chapter(url: String, name: String, number: Float, scanlator: String? = null) =
    """{"url":"$url","name":"$name","number":$number,"scanlator":${scanlator?.let { "\"$it\"" } ?: "null"}}"""

fun chapters(vararg chapters: String) = chapters.joinToString(",", "[", "]")
