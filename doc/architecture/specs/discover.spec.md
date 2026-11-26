---
title: Koma Discover
status: draft
author: Antoine Bouteiller
date: 2026-11-26
module:
  - app/src/main/kotlin/app/koma/discover/
  - app/src/main/kotlin/app/koma/data/
  - app/src/main/kotlin/app/koma/ui/KomaApp.kt
  - app/src/main/sqldelight/
related:
  - doc/architecture/specs/app.spec.md
  - doc/architecture/specs/backend/backend.spec.md
---

# Koma Discover

## Why

Finding something to read happens inside Koma, from the backend rather than from a source. The
Discover tab lists MangaBaka's trending, top-rated and popular titles, with its cross-site rating on
every card. Today the tab shows an empty state.

- `[G-1]` Discover manga, manhwa and manhua from the backend's trending, top-rated and popular
  lists, with the backend's score on every card and title screen.

## Design

| Decision | Choice | Rationale |
| --- | --- | --- |
| `[KD-27]` Rate limit | `MangaBakaBackend` retries a 429 after its `Retry-After`. Feed pages are read from `cache` (`[CT-15]`) before the network. | Every feed counts against MangaBaka's search limit (`doc/architecture/specs/backend/mangabaka.spec.md` `[C-4.1]`). |

## Outcome

- `[SO-1]` The Discover tab lists MangaBaka trending, top-rated and popular titles, filterable by
  manga, manhwa and manhua, each card showing the MangaBaka rating. — demonstrated by `[VC-16]`,
  `[VC-18]`

## Contracts

### [CT-11] Backend members

Kind: api · Anchor: null

Added to `doc/architecture/specs/backend/backend.spec.md` `[CT-1.5]`:

```kotlin
enum class Feed { Trending, Top, Popular }
data class Filters(val kinds: Set<Kind>? = null, val genres: List<String>? = null, val adult: Boolean? = null)
data class Paged<T>(val items: List<T>, val hasNext: Boolean)

interface Backend {
    suspend fun search(query: String, filters: Filters, page: Int): Paged<Media>
    suspend fun discover(feed: Feed, filters: Filters, page: Int): Paged<Media>? = null // null: no such feed
}
```

`discover` answers null when the backend has no such feed; the Discover tab hides it.
`MangaBakaBackend` maps both, and completes
`doc/architecture/specs/backend/mangabaka.spec.md` `[CT-16]`:

| Backend | MangaBaka |
| --- | --- |
| `search`, `discover` | `GET /v1/series/search`, `discover` with `sort_by` `trending_7d`, `score_desc` or `popularity_desc`; `type_not=novel` on every call (`doc/architecture/specs/app.spec.md` `[NG-6]`); unauthenticated |

### [CT-15] Feed cache

Kind: schema · Anchor: null

```sql
create table cache (key text primary key, json text not null, fetched_at integer not null);
```

`cache` holds feed pages, and `doc/architecture/specs/reader.spec.md` `[CT-9.2]`'s reading
directions, so the app opens offline. It extends `doc/architecture/specs/app.spec.md` `[CT-4.5]`
through an additive migration.

## Acceptance

Tests run on the JVM under Robolectric and stub MangaBaka's API with `FakeNetwork`.

- `[VC-16]` Given a stub returning distinct `trending_7d` and `score_desc` payloads, when the
  Discover tab opens, then it shows the stub's titles in the stub's order, the manhwa filter sends
  `type=manhwa`, every search sends `type_not=novel`, a card shows `4.2` for `rating: 84`, and
  Discover requests carry no `Authorization` header. — demonstrates `[SO-1]`
- `[VC-18]` Given a test `Backend` whose `discover` answers titles MangaBaka could not produce for
  `Trending` and `Top` and null for `Popular`, when the Discover tab opens, then it shows those
  titles and no popular feed. — demonstrates `[SO-1]`

## Open Questions

- `[OQ-1]` Does the Discover tab offer a text search and genre or adult filters, or only the three
  feeds with the kind filter? — needs: `priority` · what `[CT-11]`'s `Filters` already carries,
  MangaBaka's search limit (`doc/architecture/specs/backend/mangabaka.spec.md` `[C-4.1]`)
