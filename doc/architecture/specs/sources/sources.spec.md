---
title: Koma sources
kind: umbrella
status: review
author: Antoine Bouteiller
date: 2026-11-26
module:
  - app/src/main/kotlin/app/koma/source/
  - app/src/main/kotlin/eu/kanade/tachiyomi/source/
  - app/src/main/kotlin/eu/kanade/tachiyomi/network/
  - app/src/main/kotlin/keiyoushi/
  - app/src/main/kotlin/app/koma/title/
  - app/src/main/kotlin/app/koma/AppContainer.kt
  - app/src/main/sqldelight/
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
| `[KD-11.1]` Title to source binding | On first read, try the user's ordered preferred sources. A source with a `Finder` binds only what its finder answers. A source without one is searched with every backend title variant and binds on an exact normalized title match. When no source binds, the title screen shows ranked search candidates from every source. The binding is editable from the title screen. | A backend id proves the match, so a same-titled novel or edition never binds. Title matching stays for sources without ids. Finders live in Koma, so copied extensions stay as upstream ships them. |
| `[KD-18.2]` Sources | Mihon's extension API, at the version keiyoushi's extensions build against. Koma ships the `eu.kanade.tachiyomi.source` and `eu.kanade.tachiyomi.network` packages, and the keiyoushi helper packages a copied extension imports. v1 copies the extensions it uses from `keiyoushi/extensions-source` into `app/src/main/kotlin/eu/kanade/tachiyomi/extension/`, and lists them in `app/src/main/kotlin/app/koma/source/Sources.kt`. v1 lists Atsumaru only. | A copied extension builds with few or no edits and stays comparable with upstream. The same API can later load installed extension APKs (`[NG-3.1]`). Atsumaru's JSON API answers plain HTTPS with no Cloudflare challenge, and its 33,632 series (2026-11-18) cover the titles the user reads. |

| Leaf | Module |
| --- | --- |
| `doc/architecture/specs/sources/atsumaru.spec.md` | `app/src/main/kotlin/eu/kanade/tachiyomi/extension/en/atsumaru/`, `app/src/main/kotlin/app/koma/source/atsumaru/` |

## Outcome

- `[SO-10]` The title screen, binding and the reader run unchanged against any source built on
  Mihon's extension API. — demonstrated by `[VC-17]`

## Contracts

### [CT-2.3] Sources

Kind: api · Anchor: null

```kotlin
// Mihon's API, eu.kanade.tachiyomi.source: the members Koma calls.
interface Source {
    val id: Long
    val name: String
    val lang: String
    suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage
    suspend fun getMangaUpdate(
        manga: SManga, chapters: List<SChapter>, fetchDetails: Boolean, fetchChapters: Boolean,
    ): SMangaUpdate
    suspend fun getPageList(chapter: SChapter): List<Page>
}
abstract class HttpSource : CatalogueSource {
    open val client: OkHttpClient
    open suspend fun getImageUrl(page: Page): String
    suspend fun getImage(page: Page, existingSize: Long = 0L): Response
}

// Koma, app/src/main/kotlin/app/koma/source/
/** Proves that a source manga is a backend media. */
fun interface Finder {
    suspend fun find(details: MediaDetails): SManga?
}
class CatalogSource(val source: HttpSource, val finder: Finder? = null)
val sources: List<CatalogSource>     // Sources.kt
```

`SManga.url` and `SChapter.url` are the source's keys and opaque to the core; a binding stores
`Source.id` and `SManga.url`. Chapters are `getMangaUpdate(manga, emptyList(), fetchDetails =
false, fetchChapters = true).chapters`. Koma never calls the deprecated Rx members. `find` answers
a manga only when the source proves it is that media, for example by a backend id; `null` means
unproven (`[KD-11.1]`). A source never writes to the backend or the store.

`AppContainer` registers Mihon's `NetworkHelper` in Injekt, its client built from
`AppContainer.http`, so every source request passes through the same seam as the app's.

### [CT-13] Binding store

Kind: schema · Anchor: null

```sql
create table binding (
  backend text not null, media_id text not null,
  source integer not null, manga_url text not null, title text not null,
  primary key (backend, media_id)
);
create table preferred_source (source integer primary key, position integer not null);
```

`source` is Mihon's `Source.id`. Both tables extend `doc/architecture/specs/app.spec.md`
`[CT-4.5]` through an additive migration.

## Acceptance

Source tests stub each source's API with `FakeNetwork`, through the client sources share
(`[CT-2.3]`).

- `[VC-17]` Given a test `HttpSource` with ids and titles that Atsumaru could not produce, when the
  title-screen, binding and reader tests run against it, then they show the stub's values.
  — demonstrates `[SO-10]`

## Caveats

- `[C-3.1]` A source behind a Cloudflare challenge, such as Asura Scans or Weeb Central, can pass it
  in a WebView, as Mihon's extensions do.
- `[C-12]` Koma's copy of Mihon's source API follows the `extensions-lib` commit that keiyoushi's
  `gradle/libs.versions.toml` pins (`tachiyomi-lib-v16`, `42255ee5fa` on 2026-11-25). An extension
  copied from a later upstream may need that copy updated first. Copied code keeps its Apache 2.0
  headers, and lint treats its warnings as errors like Koma's own.
