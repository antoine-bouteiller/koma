---
title: Koma backend
kind: umbrella
status: amended
author: Antoine Bouteiller
date: 2026-11-26
module:
  - app/src/main/kotlin/app/koma/data/Backend.kt
related:
  - doc/architecture/specs/app.spec.md
  - doc/architecture/specs/discover.spec.md
  - doc/architecture/specs/reader.spec.md
---

# Koma backend

## Why

Koma is a client of one backend, and that backend is the product surface: the account, the library
and the reading history are the backend's. v1's backend is MangaBaka. Everything above the
`Backend` interface reads and writes them without knowing which service answers.

- `[G-5.2]` Keep the backend behind one interface, so another backend can replace MangaBaka without
  touching the UI.
- `[NG-5]` More than one active backend at a time.

## Design

| Decision | Choice | Rationale |
| --- | --- | --- |
| `[KD-20]` Backend | One interface, `Backend` (`[CT-1.5]`). MangaBaka implements it as `MangaBakaBackend` in `app.koma.data.mangabaka`, with its sign-in. `KomaApplication` builds it and passes it to `AppContainer`; nothing else imports that package. | Koma is built as a client of this service, so "tracker", Mihon's word for a sync target added afterwards, misnames it. A name ending in `Service` would be confused with Android's component. One interface, not one per capability: `[NG-5]` allows one backend at a time, and library entries reference the backend's own media ids. |

| Leaf | Module |
| --- | --- |
| `doc/architecture/specs/backend/mangabaka.spec.md` | `app/src/main/kotlin/app/koma/data/mangabaka/` |

`doc/architecture/specs/discover.spec.md` `[CT-11]` and `doc/architecture/specs/reader.spec.md`
`[CT-12]` add the members their features call. Each moves into `[CT-1.5]` when its feature ships.

## Outcome

- `[SO-6.2]` Library, History, the title screen and Settings run unchanged against any `Backend`,
  the backend's name in copy included. — demonstrated by `[VC-7.2]`

## Contracts

### [CT-1.5] Backend

Kind: api · Anchor: `app/src/main/kotlin/app/koma/data/Backend.kt`

```kotlin
package app.koma.data

enum class BackendId { MangaBaka }
data class MediaRef(val backend: BackendId, val id: String)
enum class Kind { Manga, Manhwa, Manhua, Oel, Novel, Other }
enum class ListStatus { Reading, Planning, Considering, Completed, Paused, Dropped, Rereading }
enum class Publication { Releasing, Completed, Hiatus, Cancelled, Upcoming, Unknown }

/** What a card, a shelf or a history row shows. */
data class Media(
    val ref: MediaRef,
    val title: String,               // display title
    val cover: String,
    val kind: Kind,
    val score: Double?,              // backend rating, 0.0–5.0, one decimal
    val adult: Boolean,
    val longStrip: Boolean,          // false when the backend does not say
)

/** What the title screen shows beyond [media]. */
data class MediaDetails(
    val media: Media,
    val titles: List<String>,        // every variant: native, romanized, secondary titles
    val authors: List<String>,
    val publication: Publication,
    val startYear: Int?,
    val chapters: Double?,           // total, when the backend knows it
    val description: String,
    val tags: List<Tag>,
)
data class Tag(val name: String, val genre: Boolean, val spoiler: Boolean, val rank: Int?) // rank 0–100

data class ListEntry(
    val ref: MediaRef,
    val status: ListStatus,
    val progress: Double?,           // chapters read; may be fractional
    val score: Double?,              // the user's rating, 0.0–5.0, one decimal
    val updatedAt: Long,             // epoch ms
)
sealed interface ListChange {
    data class Upsert(val entry: ListEntry) : ListChange
    data class Removed(val ref: MediaRef) : ListChange
}
data class ReadEvent(val id: String, val ref: MediaRef, val at: Long, val from: Double?, val to: Double?)
data class CursorPage<T>(val items: List<T>, val cursor: String, val more: Boolean)

data class Session(val signedIn: Boolean, val error: String)
data class SignInResult(val ok: Boolean, val error: String)
class SignedOutException : IOException("Not signed in.")
class ExpiredCursorException : IOException("Cursor refused.")

interface Backend {
    val id: BackendId
    val name: String                                               // shown in copy
    val sessions: StateFlow<Session>
    suspend fun signIn(): SignInResult
    suspend fun signOut()
    suspend fun userName(): String                                 // empty when the account has none
    suspend fun changes(cursor: String?): CursorPage<ListChange>
    suspend fun addEntry(ref: MediaRef, status: ListStatus, progress: Double? = null, score: Double? = null)
    suspend fun updateEntry(
        ref: MediaRef,
        status: ListStatus? = null,
        progress: Double? = null,
        score: Double? = null,
    )
    suspend fun readEvents(cursor: String?, since: Long?, limit: Int): CursorPage<ReadEvent>
    suspend fun media(refs: List<MediaRef>): List<Media>
    suspend fun details(ref: MediaRef): MediaDetails
}
```

The identity-replacement member is specified separately by `[CT-2]` on the same interface.

Every member throws an `IOException` for any failure, an unreadable reply included, so callers never
see HTTP statuses or serialization. `changes(null)` pages through the whole library; a refused
cursor throws `ExpiredCursorException`, and a backend without a changes feed refuses every cursor.
`addEntry` puts media in the library; `updateEntry` changes only the fields it is given, and a
`score` of 0 clears the rating. Either throws an `IOException` when the library already holds the
entry, or does not hold it, as the call assumed. A backend whose API only upserts implements both
the same way. `media` accepts any number of refs; a ref the backend no longer answers is missing
from the result. `readEvents` answers newest first; `since` keeps events at or after that instant
(epoch ms), and a cursor is valid only with the filters that made it. Every score, the backend's
and the user's, is on one 0.0–5.0 scale to one decimal, and tag ranks on 0–100, so no screen formats
a backend-specific scale.

### [CT-2] Identity replacements

Kind: api · Anchor: `app/src/main/kotlin/app/koma/data/Backend.kt#replacements`

```kotlin
interface Backend {
    suspend fun replacements(refs: List<MediaRef>): Map<MediaRef, MediaRef>
}
```

Only replaced refs appear, including intermediate refs discovered while following a replacement.
Each value is the final surviving ref on the same backend, distinct from its key. Unchanged and
unavailable refs are omitted. Any number of refs is accepted.
Malformed replacements and cycles throw `IOException`; a backend without deduplication returns
an empty map. The interface exposes identity, not a service's lifecycle-state vocabulary.
Library repair belongs to `doc/architecture/specs/app.spec.md` `[CT-11]`.

## Acceptance

- `[VC-7.2]` Given a test `Backend` named `Shelfie`, with ids and titles that MangaBaka could not
  produce, when the Library, History, title-screen and Settings tests run against it, then they
  show and write the stub's values, and every screen that names the backend reads `Shelfie`.
  — demonstrates `[SO-6.2]`
- `[VC-8]` Given a test backend with nonnumeric replacement refs, a library sync follows its
  replacement without knowing the backend's wire format. — demonstrates `[SO-6.2]`
