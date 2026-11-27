---
title: Atsumaru source
status: implementing
author: Antoine Bouteiller
date: 2026-11-27
module:
  - app/src/main/kotlin/eu/kanade/tachiyomi/extension/en/atsumaru/
  - app/src/main/kotlin/app/koma/source/atsumaru/
related:
  - doc/architecture/specs/reader.spec.md
---

# Atsumaru source

## Why

Atsumaru is v1's only source: an aggregator whose titles carry their MangaBaka id. Koma copies
keiyoushi's Atsumaru extension and adds a finder that binds a title by that id
(`doc/architecture/specs/sources/sources.spec.md` `[KD-11.1]`).

## Design

| Decision | Choice | Rationale |
| --- | --- | --- |
| `[KD-19.1]` Chapter deduplication | The source's own. Atsumaru lists one upload per scanlator, and the copied extension keeps one chapter per number: the upload whose scanlator has the highest `score`, then the latest upload. | Mihon's `SChapter` carries no scanlator score, so only the extension can rank uploads. |
| `[KD-29]` The copy | `Atsumaru.kt`, `Dto.kt` and `Filters.kt` from `keiyoushi/extensions-source` `7f91c9d` (`src/en/atsumaru/src/eu/kanade/tachiyomi/extension/en/atsumaru/`, extension `versionCode` 28), into `app/src/main/kotlin/eu/kanade/tachiyomi/extension/en/atsumaru/`. One edit: `deduplicateChapters` reads `prefs.getBoolean(PREF_DEDUPLICATE_CHAPTERS, true)`, where upstream defaults to `false`. Nothing else changes. | Upstream gates `[KD-19.1]` behind a preference that Koma shows no screen for. Changing the default keeps preferences and Injekt out of the container's construction order. |
| `[KD-30]` Adult titles in the finder | The finder searches with the extension's `AdultFilter` checked, so its search includes adult titles. The candidates' search uses `FilterList()` and leaves them out. | The MangaBaka id proves the match, so a wider search cannot bind a wrong title. Without the filter, the extension adds `isAdult:=false` to every search, and an adult title could never be found. |

## Outcome

- `[SO-9]` A user can read a title from Atsumaru without searching when Atsumaru carries its
  MangaBaka id. Each chapter number appears once. — demonstrated by `[VC-12.1]`, `[VC-13]`,
  `doc/architecture/specs/reader.spec.md` `[VC-3]`

## Contracts

### [CT-20] Atsumaru registration

Kind: api · Anchor: null

```kotlin
// app/src/main/kotlin/app/koma/source/atsumaru/AtsumaruSource.kt
// What keiyoushi's KSP processor generates from src/en/atsumaru/build.gradle.kts.
internal class AtsumaruSource : Atsumaru() {
    override val name: String get() = "Atsumaru"
    override val lang: String get() = "en"
    override val id: Long get() = 2327480808438768017L   // "atsumaru/en/2": versionId 2
    override val baseUrl: String get() = "https://atsu.moe"
}

// app/src/main/kotlin/app/koma/source/Sources.kt
fun sources(): List<CatalogSource> {
    val atsumaru = AtsumaruSource()
    return listOf(CatalogSource(atsumaru, AtsumaruFinder(atsumaru)))
}
```

Getters, as the generated class has, so the values exist while `KeiSource` initializes. The id
equals the published extension's, so a binding stays valid if Koma later loads the installed
extension (`doc/architecture/specs/sources/sources.spec.md` `[NG-3.1]`). The extension's
preferences live in the `SharedPreferences` file `source_2327480808438768017`, which Koma never
writes.

### [CT-21] Atsumaru API

Kind: api · Anchor: null

The copied extension's calls Koma reaches, through `AtsumaruSource.client`
(`doc/architecture/specs/sources/sources.spec.md` `[CT-18]`). Each goes out with `Fetch.XHR`,
`User-Agent` from `USER_AGENT`, `Referer: https://atsu.moe/`, and the browser headers `KeiSource`
adds. The client allows 2 requests per second (`rateLimit(2)`).

| Koma calls | Request | Read into |
| --- | --- | --- |
| `getSearchManga(1, q, filters)` | `GET https://atsu.moe/collections/manga/documents/search` | `MangasPage` of `SManga(url = id, title, thumbnail_url)` |
| `getMangaUpdate(…)` | `GET https://atsu.moe/api/manga/page?id={url}` and `GET https://atsu.moe/api/manga/allChapters?mangaId={url}`, concurrently, even when `fetchDetails` is `false` | `SMangaUpdate(manga, chapters)` |
| `getPageList(chapter)` | `GET https://atsu.moe/api/read/chapter?mangaId={a}&chapterId={b}`, where `chapter.url` is `{a}/{b}` | `List<Page>` with `imageUrl` set |
| `getPageList`, on a promo answer | `HEAD https://atsu.moe/api/android/download`, sent with `Cache-Control: no-cache`, then the chapter request again | Nothing |
| `getImage(page)` | `GET https://cdn.atsu.moe/static/…` with `Fetch.IMAGE` | Image bytes |
| `AtsumaruFinder` | `GET https://atsu.moe/api/manga/page?id={url}` | `mangaPage.mangaBakaId` |

The search is Atsumaru's Typesense index. Its query parameters, for a non-empty `q` and no filter
but `AdultFilter`:

| Parameter | Value |
| --- | --- |
| `q` | The query |
| `query_by` | `title,englishTitle,otherNames,authors,acronyms` |
| `query_by_weights` | `4,3,2,2,1` |
| `num_typos` | `4,3,2,1,0` |
| `prefix` | `true,true,true,true,false` |
| `include_fields` | `id,title,posterMedium` |
| `filter_by` | ``medium:!=[`Novel`] && isAdult:=false && views:>0 && hidden:!=true``; without `isAdult:=false` when `AdultFilter` is checked |
| `page`, `per_page` | The page, `40` |
| `infix` | `off,off,fallback,off,off` |

Each answer, trimmed to the fields the DTOs require, with every other field optional. Unknown
fields are ignored (`ignoreUnknownKeys`):

```json
// search
{"page": 1, "found": 2, "request_params": {"per_page": 40},
 "hits": [{"document": {"id": "oZOG5", "title": "Solo Leveling",
                        "posterMedium": "/static/posters/cGoOe70KFmmEBe4m-medium.avif"}}]}
// manga page
{"mangaPage": {"id": "oZOG5", "title": "Solo Leveling",
  "poster": {"largeImage": "posters/cGoOe70KFmmEBe4m-large.avif"},
  "authors": [{"name": "Chu-Gong", "type": "Author"}], "genres": [], "tags": [], "otherNames": [],
  "scanlators": [{"id": "cml2g1rs5000701pgm0b4uf46", "name": "Asura", "score": 15}],
  "mangaBakaId": "3397"}}
// all chapters
{"chapters": [{"id": "PwTgD1Gh", "number": 200, "title": "Chapter 200",
               "scanlationMangaId": "cml2g1rs5000701pgm0b4uf46", "createdAt": 1751846327498}]}
// read chapter
{"readChapter": {"pages": [{"image": "/static/pages/oZOG5/PwTgD1Gh/0.webp"}]}}
```

The extension maps them as follows:

- Search: `hasNextPage` is `page * per_page < found`. `thumbnail_url` is `posterMedium` with
  `-medium.` replaced by `-large.`, resolved against `https://cdn.atsu.moe/static/`.
- Manga page: `SManga.url` is `id`, and `mangaPage.scanlators` names the chapters' scanlators.
  `mangaBakaId` is a decimal string, absent on titles Atsumaru has not matched.
- Chapters: each becomes `SChapter(url = "{mangaId}/{id}", chapter_number = number, name = title,
  scanlator = the scanlator's name, date_upload = createdAt)`. `createdAt` is epoch milliseconds
  or an ISO-8601 string. `[KD-19.1]` groups them by `number` and keeps the upload with the
  highest scanlator `score`, then the latest `createdAt`; an upload with no known scanlator ranks
  lowest. The list is sorted by `chapter_number` descending, then scanlator, then newest.
- Pages: `Page(index, imageUrl = image resolved against https://cdn.atsu.moe/static/)`, so
  `/static/pages/…` becomes `https://cdn.atsu.moe/static/pages/…`. A chapter whose only page is
  under `/static/promo/` is Atsumaru's read limit: the extension unlocks it once with the `HEAD`
  call and reads again. A second promo answer throws `Exception("Chapter limit reached, try again
  later")`.

### [CT-17] Atsumaru finder

Kind: api · Anchor: null

```kotlin
// app/src/main/kotlin/app/koma/source/atsumaru/AtsumaruFinder.kt
internal class AtsumaruFinder(private val source: AtsumaruSource) : Finder {
    override suspend fun find(details: MediaDetails): SManga? {
        if (details.media.ref.backend != BackendId.MangaBaka) return null
        val read = mutableSetOf<String>()
        for (variant in variants(details)) {
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
        val page = "https://atsu.moe/api/manga/page".toHttpUrl().newBuilder()
            .addQueryParameter("id", url).build()
        return source.client.get(page, source.headers, Fetch.XHR)
            .parseAs<FinderPage>().mangaPage.mangaBakaId
    }
}

@Serializable private class FinderPage(val mangaPage: Ids)
@Serializable private class Ids(val mangaBakaId: String? = null)
```

`variants` is `doc/architecture/specs/sources/sources.spec.md` `[CT-19]`'s. It searches each
variant in order and reads the manga page of its top three hits not read for an earlier variant.
It answers the first hit whose `mangaBakaId` equals `media.ref.id`, and stops there. `AdultFilter`
is the extension's `internal` class, visible because Koma builds one module (`[KD-30]`). The
finder declares its own DTO because the extension's `MangaPageDto.mangaBakaId` is private. `get`,
`Fetch` and `parseAs` are keiyoushi's. A failed request propagates.

## Acceptance

Tests stub Atsumaru's API with `FakeNetwork`, through the client sources share
(`doc/architecture/specs/sources/sources.spec.md` `[CT-2.3]`). The stubs answer `[CT-21]`'s
shapes: the search under `https://atsu.moe/collections/manga/documents/search?q=…`, and each
manga page under `https://atsu.moe/api/manga/page?id=…`.

- `[VC-12.1]` Given media `3397` titled `Solo Leveling` with variant `Na Honjaman Level Up`, and
  an Atsumaru stub whose search answers `A` titled `Solo Leveling` with `mangaBakaId` `1`, then `B`
  titled `I Level Up Alone` with `mangaBakaId` `3397`, when the title is first read, then the
  Atsumaru finder binds it to `B` without a prompt; when neither hit carries `3397`, nothing is
  bound and the title screen lists `A` and `B` as candidates. — demonstrates `[SO-9]`
- `[VC-13]` Given an Atsumaru stub listing chapter 5 from scanlator `Y` (score 3, day 1), `Z`
  (score 3, day 2) and `X` (score 1, day 3), and chapter 4.5 from `X` alone, when the chapter list
  opens, then it shows one chapter 5, credited to `Z`, and chapter 4.5, credited to `X`.
  — demonstrates `[SO-9]`

## Caveats

- `[C-10.1]` Atsumaru's API is undocumented. The copied extension owns its endpoints; when one
  breaks, the fix is a fresh copy of `src/en/atsumaru/` from `keiyoushi/extensions-source`, with
  `[KD-29]`'s edit applied again, and `[CT-21]` updated. The finder's manga page read is Koma's and
  follows the extension's. On 2026-11-27 every `[CT-21]` call answered plain HTTPS without a
  challenge, also with `User-Agent: koma`.
- `[C-11.1]` The Atsumaru finder misses a title whose Atsumaru page lacks `mangaBakaId`, or whose
  title variants rank it below the top three hits. Such a title is bound by hand from the
  candidates. The extension's search leaves out novels, and the candidates' search also leaves out
  adult titles, which then cannot be bound by hand.
- `[C-14]` Binding costs up to one search and three manga pages per variant, at 2 requests per
  second: a title with five variants and no match takes about ten seconds. Opening a chapter list
  costs two requests.
