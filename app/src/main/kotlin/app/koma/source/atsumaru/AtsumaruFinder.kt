package app.koma.source.atsumaru

import app.koma.data.BackendId
import app.koma.data.MediaDetails
import app.koma.source.Finder
import app.koma.source.variants
import eu.kanade.tachiyomi.extension.en.atsumaru.AdultFilter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.utils.parseAs
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl

/** Finds the Atsumaru manga whose page carries the media's MangaBaka id. */
internal class AtsumaruFinder(private val source: AtsumaruSource) : Finder {
    override suspend fun find(details: MediaDetails): SManga? {
        if (details.media.ref.backend != BackendId.MangaBaka) return null
        val read = mutableSetOf<String>()
        for (variant in variants(details)) {
            // The id proves the match, so adult titles may be searched too.
            val filters = FilterList(AdultFilter().apply { state = true })
            val hits = source.getSearchManga(1, variant, filters).mangas
            for (hit in hits.filter { it.url !in read }.take(3)) {
                read += hit.url
                if (mangaBakaId(hit.url) == details.media.ref.id) return hit
            }
        }
        return null
    }

    private suspend fun mangaBakaId(url: String): String? {
        val page =
            "https://atsu.moe/api/manga/page"
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter("id", url)
                .build()
        return source.client
            .get(page, source.headers, Fetch.XHR)
            .parseAs<FinderPage>()
            .mangaPage
            .mangaBakaId
    }
}

// The extension's MangaPageDto keeps mangaBakaId private.
@Serializable private class FinderPage(val mangaPage: Ids)

@Serializable private class Ids(val mangaBakaId: String? = null)
