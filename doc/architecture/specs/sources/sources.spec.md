---
title: Koma sources
kind: umbrella
status: amended
author: Antoine Bouteiller
date: 2026-11-27
module:
  - app/src/main/kotlin/app/koma/source/
  - app/src/main/kotlin/eu/kanade/tachiyomi/source/
  - app/src/main/kotlin/eu/kanade/tachiyomi/network/
  - app/src/main/kotlin/keiyoushi/
  - app/src/main/kotlin/app/koma/data/SourceRepository.kt
  - app/src/main/kotlin/app/koma/data/LibraryUpdater.kt
  - app/src/main/kotlin/app/koma/library/
  - app/src/main/kotlin/app/koma/data/Store.kt
  - app/src/main/kotlin/app/koma/data/Http.kt
  - app/src/main/kotlin/app/koma/title/
  - app/src/main/kotlin/app/koma/AppContainer.kt
  - app/src/main/kotlin/app/koma/KomaApplication.kt
  - app/src/main/sqldelight/
  - app/build.gradle.kts
  - gradle/libs.versions.toml
  - treefmt.nix
related:
  - doc/architecture/specs/app.spec.md
  - doc/architecture/specs/reader.spec.md
---

# Koma sources

## Why

Sources only supply chapters and pages, through Mihon's extension API, so Mihon's extensions can
supply them. A source manga exists only as the content behind a backend media
(`doc/architecture/specs/app.spec.md` `[PI-1]`): Koma finds it for the title the user opens, and
proves the match before binding it.

- `[G-2]` Read chapters from Atsumaru, found for a backend title without a manual search.
- `[G-8]` Keep sources behind Mihon's extension API, so another source can be added without
  touching the UI.
- `[NG-3.1]` Mihon interoperability beyond the source API in v1: installing extension APKs,
  extension repositories, and backups.

## Design

| Decision | Choice | Rationale |
| --- | --- | --- |
| `[KD-11.1]` Title to source binding | On first read, try the user's ordered preferred sources. A source with a `Finder` binds only what its finder answers. A source without one is searched with every backend title variant and binds on an exact normalized title match. When no source binds, the title screen shows ranked search candidates from every source. The binding is editable from the title screen when Koma lists more than one source. `[CT-19.1]` gives the algorithm. | A backend id proves the match, so a same-titled novel or edition never binds. Title matching stays for sources without ids. Finders live in Koma, so copied extensions stay as upstream ships them. |
| `[KD-18.2]` Sources | Mihon's extension API, at the version keiyoushi's extensions build against. Koma ships the `eu.kanade.tachiyomi.source` and `eu.kanade.tachiyomi.network` packages, and the keiyoushi helper packages a copied extension imports (`[CT-18]`). v1 copies the extensions it uses from `keiyoushi/extensions-source` into `app/src/main/kotlin/eu/kanade/tachiyomi/extension/`, and lists them in `app/src/main/kotlin/app/koma/source/Sources.kt`. v1 lists Atsumaru only. | A copied extension builds with few or no edits and stays comparable with upstream. The same API can later load installed extension APKs (`[NG-3.1]`). Atsumaru's JSON API answers plain HTTPS with no Cloudflare challenge, and its 33,632 series (2026-11-18) cover the titles the user reads. |
| `[KD-32]` Chapter store | A source manga's chapter list is stored on its first load, and the title screen shows the stored list. Only a library update (`[CT-22]`) or a pull on the title screen fetches it again. A library update refetches the titles filed `Reading` or `Rereading`, and fetches any other title's list only until one is stored. | Mihon's model: opening a title costs no source request, and the titles being read stay current. Titles the user does not follow cost one request in total. `LibraryUpdater` holds no screen state, so a periodic job can run it unchanged. |
| `[KD-28]` Source runtime | Koma has no KSP processor and no Mihon app around the extension. It writes by hand what keiyoushi's build generates (`[CT-18]`), and registers in Injekt what Mihon's app provides: the `Application`, a `NetworkHelper` over `AppContainer.http`, and a `Json`. | The copied `KeiSource` and helpers read these through Injekt and check the client's interceptors by class name, so they run unchanged. |

| Leaf | Module |
| --- | --- |
| `doc/architecture/specs/sources/atsumaru.spec.md` | `app/src/main/kotlin/eu/kanade/tachiyomi/extension/en/atsumaru/`, `app/src/main/kotlin/app/koma/source/atsumaru/` |

## Outcome

- `[SO-10]` The title screen, binding and the reader run unchanged against any source built on
  Mihon's extension API. — demonstrated by `[VC-17.1]`
- `[SO-13]` A title binds without a prompt to a source without a finder when that source answers
  one of the title's variants, whatever its case, accents and punctuation. — demonstrated by
  `[VC-19]`
- `[SO-14]` A title opens with the chapters last stored for it, without a source request. A library
  update fetches the chapters of every title being read, and those of other titles once. A launch
  over a synced library sends no library or chapter request. — demonstrated by `[VC-20]`,
  `[VC-21]`

## Contracts

### [CT-2.3] Sources

Kind: api · Anchor: `app/src/main/kotlin/app/koma/source/`

```kotlin
// Mihon's API, eu.kanade.tachiyomi.source: the members Koma calls.
interface Source {
    val id: Long
    val name: String
    suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage
    suspend fun getMangaUpdate(
        manga: SManga, chapters: List<SChapter>, fetchDetails: Boolean, fetchChapters: Boolean,
    ): SMangaUpdate
    suspend fun getPageList(chapter: SChapter): List<Page>
}
interface CatalogueSource : Source {
    val lang: String
}
abstract class HttpSource : CatalogueSource {
    open val client: OkHttpClient
    open suspend fun getImageUrl(page: Page): String
    suspend fun getImage(page: Page): Response    // Koma's: imageRequest(page) on client, 2xx only
}

// Koma, app/src/main/kotlin/app/koma/source/
/** Proves that a source manga is a backend media. */
fun interface Finder {
    suspend fun find(details: MediaDetails): SManga?
}
class CatalogSource(val source: HttpSource, val finder: Finder? = null)
fun sources(): List<CatalogSource>     // Sources.kt: fresh instances on each call
```

`SManga.url` and `SChapter.url` are the source's keys and opaque to the core; a binding stores
`Source.id` and `SManga.url`. Chapters are `getMangaUpdate(SManga.create().apply { url =
binding.mangaUrl; title = binding.title }, emptyList(), fetchDetails = false, fetchChapters =
true).chapters`. Koma never calls the deprecated Rx members, and never calls `getFilterList()`:
`KeiSource` answers it by fetching filters in the background. Koma passes `FilterList()` unless a
finder knows the source's filters. `find` answers a manga only when the source proves it is that
media, for example by a backend id; `null` means unproven (`[KD-11.1]`). A source never writes to
the backend or the store.

A source member may throw any `Exception`, not only `IOException`: Mihon's `HttpException`, which
`awaitSuccess` throws on a non-2xx answer, is an `IllegalStateException`. Callers treat every
exception but `CancellationException` as a failed request. A `KeiSource` throws
`IllegalStateException` when `getMangaUpdate` runs twice at once for one `SManga.url`, so callers
run at most one chapter load per binding.

`sources()` builds new instances because a source resolves its `NetworkHelper` from Injekt once,
on its first request: a process-wide list would keep the first container's client. `AppContainer`
takes the list as a constructor parameter, a seam: `KomaApplication` passes `sources()`, and tests
pass `sources()` or their own.

### [CT-18] Source runtime

Kind: boundary · Anchor: `app/src/main/kotlin/eu/kanade/tachiyomi/`

Koma's copies, all under `app/src/main/kotlin/`:

| Package | Holds | From |
| --- | --- | --- |
| `eu.kanade.tachiyomi.source`, `.model`, `.online` | `Source`, `CatalogueSource`, `ConfigurableSource`, `HttpSource`, and the models `Filter`, `FilterList`, `MangasPage`, `Page`, `SChapter`, `SChapterImpl`, `SManga`, `SMangaImpl`, `SMangaUpdate`, `UpdateStrategy` | Signatures from `keiyoushi/extensions-lib` `42255ee5fa` (`library/src/main/java/`), which are stubs; bodies from `mihonapp/mihon` `f8e30a8` (`source-api/src/main/kotlin/`) |
| `eu.kanade.tachiyomi.network` | `NetworkHelper`, `HttpException`, `Call.await`, `Call.awaitSuccess`, `GET`, `POST` | Signatures from extensions-lib; bodies from Mihon `core/common/src/main/kotlin/` |
| `eu.kanade.tachiyomi.network.interceptor` | `UncaughtExceptionInterceptor`, `UserAgentInterceptor`, `CloudflareInterceptor` | Mihon `core/common/`, except `CloudflareInterceptor`, which passes requests through (`[C-3.1]`) |
| `keiyoushi.annotation`, `.network`, `.source`, `.utils` | `annotation/Source.kt`; `network/{BaseUrlFetchInterceptor,BrowserHeaders,BrowserRequests,CacheControlInterceptor,Deflate,OkHttp,RateLimit}.kt`; `source/{FetchableBaseUrl,KeiSource,SMangaUpdate}.kt`; `utils/{Collections,Context,Date,Json,JsonElement,Markdown,Preferences}.kt` | `keiyoushi/extensions-source` `7f91c9d` (`core/src/main/kotlin/keiyoushi/`), unchanged |

The `eu.kanade.tachiyomi` copies declare exactly extensions-lib's public members and fill each
body from Mihon. They drop Rx, Mihon's deprecated request and parse helpers, page progress, and
Compose's `@Stable`. Where extensions-lib makes a member abstract and Mihon gives it an Rx
default, Koma's stays abstract. `getImageUrl` throws `UnsupportedOperationException` unless a
source overrides it. The members below keep this shape, because `KeiSource` relies on them:

```kotlin
abstract class HttpSource : CatalogueSource {
    protected val network: NetworkHelper by injectLazy()
    override val id: Long by lazy { generateId(name, lang, versionId) }   // overridable
    val headers: Headers by lazy { headersBuilder().build() }   // KeiSource sets headers$delegate
    open val client: OkHttpClient get() = network.client
    protected open fun headersBuilder() = Headers.Builder().add("User-Agent", USER_AGENT)
    protected open fun imageRequest(page: Page): Request = GET(page.imageUrl!!, headers)
}

class NetworkHelper(http: OkHttpClient) {
    // http.newBuilder(), then UncaughtExceptionInterceptor, UserAgentInterceptor { USER_AGENT },
    // CloudflareInterceptor, in that order
    val client: OkHttpClient
}

// Mihon's default_user_agent preference default
const val USER_AGENT = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 " +
    "(KHTML, like Gecko) Chrome/149.0.0.0 Mobile Safari/537.36"
```

`NetworkHelper` takes `AppContainer.http` where extensions-lib's takes a `Context`, since no
extension constructs one. `KeiSource` builds its client from `network.client` and fails its first
request unless that client holds interceptors whose simple names are `UncaughtExceptionInterceptor`,
`UserAgentInterceptor` and `CloudflareInterceptor`. It also fails on a network interceptor named
`IgnoreGzipInterceptor` or a `BrotliInterceptor`. `httpClient()` in
`app/src/main/kotlin/app/koma/data/Http.kt` sets `User-Agent: koma` only on a request that has none,
so a source's own `User-Agent` reaches the site.

`AppContainer`'s `init` registers, before any source request:

```kotlin
val application = context.applicationContext as Application
val network = NetworkHelper(http)
val json = Json { ignoreUnknownKeys = true; explicitNulls = false }   // Mihon's
Injekt.addFactory<Application> { application }
Injekt.addFactory { network }
Injekt.addFactory { json }
```

Each registration is an `addFactory` answering the container's instance, never `addSingleton`:
Injekt keeps the first instance a singleton resolves for the whole process, so a later container's
registration would not take effect. `keiyoushi.utils.applicationContext` and `jsonInstance` are
top-level values read once per process, which tests share across containers.

Each copied extension's `@Source` class is abstract. Koma replaces the subclass keiyoushi's KSP
processor generates with one written by hand in `app/src/main/kotlin/app/koma/source/<name>/`. It
overrides `name`, `lang`, `id` and `baseUrl` with getters holding the values of the extension's
`build.gradle.kts` `source {}` block. `id` is the first 8 bytes of
`MD5("${name.lowercase()}/$lang/$versionId")` read big-endian, with the sign bit cleared.
`keiyoushi.annotation.Source` keeps its `SOURCE` retention, and no processor reads it.

Libraries added to `gradle/libs.versions.toml` and `app/build.gradle.kts` (`implementation`), at
Mihon's versions (`doc/architecture/specs/app.spec.md` `[C-1.1]`):

| Library | Version | Needed by |
| --- | --- | --- |
| `uy.kohesive.injekt:injekt-core` | `1.16.1` | `HttpSource`, `keiyoushi.utils` |
| `androidx.preference:preference-ktx` | `1.2.1` | `ConfigurableSource`, the copied extensions' preference screens |
| `org.jsoup:jsoup` | `1.24.1` | `keiyoushi.utils.Markdown` |
| `com.squareup.okhttp3:okhttp-brotli`, `okhttp-zstd` | Koma's `okhttp` | `KeiSource`'s `CompressionInterceptor`, its filter cache |
| `com.squareup.zstd:zstd-kmp-okio` | `0.4.0`, the one `okhttp-zstd` resolves | `KeiSource`'s filter cache, which imports it while `okhttp-zstd` keeps it off the compile classpath |
| `org.jetbrains.kotlinx:kotlinx-serialization-json-okio` | Koma's `kotlinx-serialization` | `keiyoushi.utils.Json`, `KeiSource` |

`treefmt.nix` excludes `app/src/main/kotlin/eu/kanade/tachiyomi/**` and
`app/src/main/kotlin/keiyoushi/**`, so `nix fmt` leaves copied code as upstream wrote it.

### [CT-19.1] Source repository

Kind: api · Anchor: `app/src/main/kotlin/app/koma/data/SourceRepository.kt`

```kotlin
package app.koma.data

data class Binding(val source: Long, val mangaUrl: String, val title: String)
data class Candidate(val source: Long, val sourceName: String, val manga: SManga)

class SourceRepository(store: Store, sources: List<CatalogSource>) {
    fun binding(ref: MediaRef): Flow<Binding?>                 // from the store
    suspend fun resolve(details: MediaDetails): Binding?       // stored, else bound now, else null
    suspend fun candidates(details: MediaDetails): List<Candidate>
    suspend fun bind(ref: MediaRef, candidate: Candidate)      // replaces any binding
    suspend fun chapters(binding: Binding, refresh: Boolean = false): List<SChapter>?
                                                               // stored unless refresh; null when
                                                               // sources() lacks it
    fun excludedScanlators(ref: MediaRef): Flow<Set<String>>   // from the store
    suspend fun showScanlator(ref: MediaRef, scanlator: String, shown: Boolean)
    fun source(id: Long): HttpSource?                          // null when sources() lacks it
    val count: Int                                             // how many sources Koma lists
}
```

`AppContainer` builds one from its `sources`. The title screen calls `resolve` when it first loads
a title's chapter list, and `[CT-22]` calls it for a library title without a binding. On `null` it lists `candidates`, and picking one calls `bind`. When Koma
lists more than one source, the binding action on a bound title also lists `candidates`; with one
source, the title screen offers no binding action.

The title screen's header offers a scanlator filter while the bound chapters name a scanlator. It
lists them, and hides the chapters of each one the user unchecks, through `showScanlator`. A
chapter that names no scanlator always shows.

`resolve(details)`:

1. A stored `binding` row for `details.media.ref` answers it.
2. Sources are tried in `preferred_source` order, then the sources it lacks in `sources()` order.
3. A source with a finder binds `finder.find(details)`. A source without one searches each variant
   in turn, `getSearchManga(1, variant, FilterList())`, and binds the first hit whose normalized
   title equals a normalized variant.
4. The first manga bound is stored as `(backend, media_id, source.id, manga.url, manga.title)` and
   answered. When no source binds one, nothing is stored and `resolve` answers `null`.

A failed request ends `resolve` with its exception and stores nothing, so the next opening retries.

```kotlin
// app/src/main/kotlin/app/koma/source/Titles.kt, shared with the finders
fun variants(details: MediaDetails): List<String> =
    (listOf(details.media.title) + details.titles).distinct()

fun normalize(title: String): String =
    Normalizer.normalize(title, Normalizer.Form.NFKD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()
```

`candidates` is each source's `getSearchManga(1, details.media.title, FilterList()).mangas`, in
the order step 2 gives, and each source's hits in the order it answers them. A source whose search
fails is left out.

`chapters` answers the list `[CT-13.1]` stores for `(source, mangaUrl)`. Without one, or with
`refresh`, it runs `[CT-2.3]`'s chapter load and replaces the stored list with its answer. A failed
load stores nothing. It holds a lock per `(source, mangaUrl)` for the whole call, so two loads of
one binding, such as a title opened during a library update, run in turn instead of failing, and
the second reads what the first stored.

### [CT-13.1] Source store

Kind: schema · Anchor: `app/src/main/sqldelight/app/koma/data/db/Binding.sq`

```sql
create table binding (
  backend text not null, media_id text not null,
  source integer not null, manga_url text not null, title text not null,
  primary key (backend, media_id)
);
create table preferred_source (source integer primary key, position integer not null);
create table chapter (
  source integer not null, manga_url text not null, position integer not null,
  url text not null, name text not null, scanlator text, number real not null,
  uploaded integer not null,
  primary key (source, manga_url, position)
);
create table chapter_list (
  source integer not null, manga_url text not null,
  primary key (source, manga_url)
);
create table excluded_scanlator (
  backend text not null, media_id text not null, scanlator text not null,
  primary key (backend, media_id, scanlator)
);
```

`source` is Mihon's `Source.id`. The tables extend `doc/architecture/specs/app.spec.md`
`[CT-4.5]` through additive migrations: `6.sqm` adds `binding` and `preferred_source`, whose queries
live in `Binding.sq`, and `7.sqm` adds `chapter` and `chapter_list`, whose queries live in
`Chapter.sq`, and `8.sqm` adds `excluded_scanlator`, whose queries live in `ExcludedScanlator.sq`.
All five keep data that sign-out leaves in place. v1 writes no
`preferred_source` row, so sources are tried in `sources()` order.

`chapter` holds a source manga's chapters in the order the source answered them: `SChapter.url`,
`name`, `scanlator`, `chapter_number` and `date_upload`. A `chapter_list` row marks a manga whose
list is stored, so a manga with no chapters is not fetched again.

`excluded_scanlator` holds the scanlators whose chapters a title hides. It is keyed by title, so a
binding change keeps it.

### [CT-22] Library update

Kind: api · Anchor: `app/src/main/kotlin/app/koma/data/LibraryUpdater.kt`

```kotlin
package app.koma.data

class LibraryUpdater(
    store: Store, library: LibraryRepository, media: MediaRepository, sources: SourceRepository,
) {
    suspend fun update()      // throws when the library sync fails
}
```

`AppContainer` builds one as `updater`. `update()`:

1. Runs `library.sync()`. Its failure ends the update with its exception.
2. Ends when Koma lists no source.
3. Takes each title of `Library.items` in turn, novels left out, and stops at a sign-out
   (`Store.generation`). The title's stored binding, else `resolve(media.loadDetails(ref))`, gives
   its source manga; a title no source binds is skipped. It then calls
   `chapters(binding, refresh = status in { Reading, Rereading })`.
4. A failed request skips its title. Titles load one at a time.

The library screen runs it as `doc/architecture/specs/app.spec.md` `[KD-31]` says. It shows its
refresh indicator until `update()` returns.

## Acceptance

Source tests stub each source's API with `FakeNetwork`, through the client sources share
(`[CT-2.3]`). `FakeNetwork` matches a stub by URL prefix, and OkHttp encodes a space in a query as
`%20`.

- ~~`[VC-17]` Given a test `HttpSource` with ids and titles that Atsumaru could not produce, when the
  title-screen, binding and reader tests run against it, then they show the stub's values.
  — demonstrates `[SO-10]`~~
- `[VC-17.1]` Given a test `HttpSource` with ids and titles that Atsumaru could not produce, when the
  title-screen and binding tests run against it, then they show the stub's values. The reader is
  not built yet: `doc/architecture/specs/reader.spec.md` `[VC-3]` covers a test source once it is.
  — demonstrates `[SO-10]`
- `[VC-19]` Given media `Solo Leveling` with variant `Na Honjaman Level Up`, and a test source
  without a finder whose search answers `Solo Leveling: Ragnarok` for the first variant and
  `NA HONJAMAN — LÉVEL UP!` for the second, when the title is first read, then it binds to
  `NA HONJAMAN — LÉVEL UP!`; when the second search answers nothing, nothing binds and the title
  screen lists `Solo Leveling: Ragnarok` as a candidate. — demonstrates `[SO-13]`

- `[VC-20]` Given a bound title whose chapters a library update stored, when the app restarts and
  the title opens again, then it lists them and the source received one chapter request; when the
  title is pulled down, then the source receives a second request and the title lists its answer.
  — demonstrates `[SO-14]`
- `[VC-21]` Given a library with a `reading` and a `completed` title on a test source, when the
  library first opens, then each title's chapters are fetched once, and a restart fetches neither
  the library nor a chapter list. When the completed title's first fetch fails, each pull fetches
  the reading title's chapters again, and the completed title's until one fetch succeeds.
  — demonstrates `[SO-14]`

## Caveats

- `[C-3.1]` A source behind a Cloudflare challenge, such as Asura Scans or Weeb Central, can pass it
  in a WebView, as Mihon's extensions do. v1's `CloudflareInterceptor` only satisfies `KeiSource`'s
  check.
- `[C-12]` Koma's copy of Mihon's source API follows the `extensions-lib` commit that keiyoushi's
  `gradle/libs.versions.toml` pins (`tachiyomi-lib-v16`, `42255ee5fa` on 2026-11-25). An extension
  copied from a later upstream may need that copy and the `keiyoushi` copy updated first, from the
  same `extensions-source` commit. Copied code keeps its Apache 2.0 headers, and lint treats its
  warnings as errors like Koma's own.
- `[C-15]` A library title no source binds is searched again on every library update, since Koma
  stores no failed match.
