---
title: MangaBaka backend
status: implemented
author: Antoine Bouteiller
date: 2026-11-26
module:
  - app/src/main/kotlin/app/koma/data/mangabaka/
related:
  - doc/architecture/specs/app.spec.md
---

# MangaBaka backend

## Why

MangaBaka is v1's backend: it holds the account, the library and the reading history, rates every
title across sites, and keeps a changes feed of the library. `MangaBakaBackend` implements
`doc/architecture/specs/backend/backend.spec.md` `[CT-1.5]` over its REST API and its OAuth
sign-in.

## Design

| Decision | Choice | Rationale |
| --- | --- | --- |
| `[KD-4]` Backend v1 | MangaBaka REST API (`https://api.mangabaka.org`) | Cross-site rating, decimal chapter progress, a library changes feed, and lookups from AniList, MyAnimeList and Kitsu ids. |
| `[KD-5.1]` Sign-in | OAuth authorization code with PKCE (S256) as a public client: Koma's own client on OkHttp, opening a Custom Tab, with the custom-scheme callback `koma://auth`. Scopes `openid library.read library.write offline_access`. Tokens kept by `KeystoreSecretStore`, encrypted by an AES-256-GCM key held in the Android Keystore. | AppAuth-Android has had no release since 2022, and the flow is small enough to test on the JVM. Mihon's MangaBaka tracker uses the same flow. MangaBaka rejects every token without `openid`. |
| `[KD-6]` Token refresh | Access tokens last 7 days. Each refresh returns a new refresh token, which replaces the stored one before the new access token is used. | MangaBaka revokes every token of the app for that user when a retired refresh token is sent. |
| `[KD-7.1]` Endpoint stability | Stable `v1` endpoints for library writes. The beta `v2` changes feed for library sync. | Each MangaBaka endpoint declares its own stability. The changes feed has no stable equivalent. |
| `[KD-9]` Public reads | Public endpoints (search, feeds, series) are called without credentials. | MangaBaka's CDN caches only unauthenticated GETs, and a cache hit does not count against the rate limit (`[C-4.1]`). |

## Outcome

- `[SO-11]` A session lasts across token rotations: each refresh sends the refresh token the
  previous one returned, so MangaBaka never revokes the app's tokens. — demonstrated by `[VC-9]`

## Contracts

### [CT-16] MangaBaka mapping

Kind: api · Anchor: `app/src/main/kotlin/app/koma/data/mangabaka/MangaBakaBackend.kt`

| Backend | MangaBaka |
| --- | --- |
| `MediaRef.id` | The series id, as a decimal string |
| `ListStatus` | `state`; `Planning` is `plan_to_read`, the others their lowercase names. An entry with an unknown `state` is skipped. |
| `Kind` | The series `type`: `manga`, `manhwa`, `manhua`, `oel`, `novel`; any other is `Other` |
| `Publication` | `status`; any value outside the enum is `Unknown` |
| `Media.title` | The primary English title, else the first primary title |
| `Media.score` | `rating` (0–100) divided by 20, rounded to one decimal |
| `ListEntry.score` | The entry's `rating`, scaled as `Media.score` |
| `Media.adult` | `content_rating` of `erotica` or `pornographic` |
| `Media.longStrip`, `MediaDetails.chapters` | Not mapped: `false` and `null` |
| `MediaDetails.tags` | Genres (`is_genre`) and tags weighted `core`; the others are dropped |
| `Tag.rank` | `weight`: `core` 100, `defining` 75, `recurrent` 50, `incidental` 25, `unweighted` null |
| `media` | `GET /v2/series/batch`, 50 ids per call, unauthenticated |
| `details` | `GET /v2/series/{id}?schema=full`, unauthenticated |
| `changes` | `GET /v2/my/library/changes?limit=1000`; a 400 to a request with a cursor throws `ExpiredCursorException`. Lists and unknown item kinds are skipped. |
| `addEntry`, `updateEntry` | `POST` and `PATCH /v1/my/library/{id}`, sending `state`, `progress_chapter` and `rating`, the score times 20 rounded to an integer; MangaBaka stores a `rating` of 0 as none |
| `readEvents` | `GET /v2/my/library/events?event_type=progress_update`; undated events are dropped |
| `userName` | `GET /v1/my/profile`: `preferred_username`, else `nickname`, else empty |

## Acceptance

Tests run on the JVM under Robolectric and stub MangaBaka's API and OAuth endpoints with
`FakeNetwork`, the test harness's OkHttp interceptor.

- `[VC-9]` Given an expired access token, when the app refreshes twice, then the second refresh
  sends the refresh token the first one returned. — demonstrates `[SO-11]`

## Caveats

- `[C-4.1]` MangaBaka allows 30 requests per minute for search, which includes every Discover feed,
  and 180 for everything else, per client IP. CDN cache hits are not counted.
- `[C-6.1]` MangaBaka is a volunteer project. Library sync depends on its beta changes feed
  (`[KD-7.1]`), which may change without notice; `doc/architecture/specs/app.spec.md` `[VC-8]`'s
  full-read fallback is the recovery path.
- `[C-7]` A public OAuth client must be registered on mangabaka.org with the redirect `koma://auth`
  before sign-in works. Its client id ships in the app.
- `[C-8.1]` MangaBaka data is CC BY-NC-SA 4.0 and requires attribution. Settings does not yet link
  to MangaBaka or name its upstream providers.
