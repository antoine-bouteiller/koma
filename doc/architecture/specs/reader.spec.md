---
title: Koma reader
status: review
author: Antoine Bouteiller
date: 2026-11-28
module:
  - app/src/main/kotlin/app/koma/reader/
  - app/src/main/kotlin/app/koma/title/
  - app/src/main/kotlin/app/koma/data/
  - app/src/main/sqldelight/
related:
  - doc/architecture/specs/app.spec.md
  - doc/architecture/specs/backend/backend.spec.md
  - doc/architecture/specs/sources/sources.spec.md
  - doc/architecture/specs/discover.spec.md
---

# Koma reader

## Why

A bound title lists its source's chapters, and the reader shows their pages. Reading flows from one
chapter into the next, and an unfinished chapter reopens where the user left it. Reading a chapter
updates the backend without a manual step, and the chapter list shows as read what the backend's
progress covers.

- `[G-3]` Mark chapters read automatically, in both directions, with the backend as the source of
  truth for progress.
- ~~`[NG-4]` Offline chapter downloads and background library updates with notifications.~~
- `[NG-4.1]` Offline chapter downloads.

## Design

- `[PI-3]` The backend owns progress. Local state may cache or queue progress, never contradict it.

| Decision | Choice | Rationale |
| --- | --- | --- |
| ~~`[KD-10]` Merged series~~ | ~~A series answering `state: merged` has its id replaced by `merged_with` in every local row that stores it~~ | ~~MangaBaka merges duplicate series; an old id stops receiving updates.~~ |
| `[KD-13]` Read state | Derived, never stored: see `[CT-5.1]` | `[PI-3]`. A stored flag would contradict the backend after an edit on mangabaka.org. |
| `[KD-14]` Progress writes | Queued in a SQLite outbox that `LibraryRepository` owns, coalesced per media to the max, flushed right after each queueing and on every launch or foreground. Sign-out drops the queue. | Reading must not block on the network. Queued progress belongs to the account that read it, so the next account never receives it. |
| `[KD-15.2]` Page images | Coil loads each page through the source's `HttpSource.getImage(page)`, after `getImageUrl(page)` when the page has no `imageUrl`. The source's client, headers and interceptors apply. | Mihon's extensions put headers, rate limits and descrambling in their OkHttp client, so loading through it keeps them working unchanged. Coil caches pages on disk. |
| `[KD-16.1]` Reader | Three modes: a vertical long strip, paged left to right, and paged right to left. The strip is a `LazyColumn`. Paged modes are a `HorizontalPager`, with `reverseLayout` for right to left. The reader opens at `[CT-9.2]`'s mode, and the user can change it per title. | The series type alone misplaces titles: The Breaker is manhwa but paged. Both layouts virtualize, and the pager snaps one page at a time. |
| `[KD-26]` Collections endpoint | Reading direction comes from MangaBaka's beta `v1` series collections. A failed collections read falls back to `[CT-9.2]`'s kind default. | No stable endpoint gives it. |
| `[KD-38]` Opening and navigation | Tapping a chapter row on the title screen pushes `Route.Reader(ref, chapterUrl)` on the current tab's stack. The reader opens the chapter at its stored position (`[CT-14]`), else at its first page. Reading order runs up the title screen's shown list, from its last row to its first. Paging past a chapter's last page opens the next chapter at its first page. Paging back past its first page opens the previous chapter at its last page. After the first row's chapter, a note says no chapter follows. A tap in the middle of the page toggles a bar with the chapter's name and the mode menu. | Mihon's model: reading runs through a title without returning to its list. Sources list chapters newest first (`doc/architecture/specs/sources/atsumaru.spec.md`). The shown list already leaves out hidden scanlators and outdated part chapters (`doc/architecture/specs/sources/sources.spec.md` `[KD-36]`). |
| `[KD-39]` Resume | An unfinished chapter reopens at the page last shown. Finishing a chapter forgets its position. | Leaving mid-chapter keeps the place, and a reread starts over. |
| `[KD-40]` Load failures | A chapter whose page list fails to load shows an error with Retry in place of its pages. A page whose image fails shows Retry in its place. | A page never shown must not finish a chapter (`[CT-6.1]`). |

## Outcome

- `[SO-4]` Finishing a chapter raises MangaBaka progress without user action, and a chapter list
  shows as read every chapter the progress covers, decimal chapters included. — demonstrated by
  `[VC-4.1]`, `[VC-5]`, `[VC-31]`
- `[SO-7]` The reader opens a long-strip title as a vertical strip and any other title paged, in the
  direction the backend gives, else right to left for manga and left to right otherwise. The user
  can override the mode per title. — demonstrated by `[VC-11]`
- `[SO-12]` The reader shows a chapter's pages through its source's own client.
  — demonstrated by `[VC-3]`, `[VC-32]`
- `[SO-21]` Tapping a chapter opens it where the user left it, and reading past its last page
  continues into the next chapter. — demonstrated by `[VC-29]`, `[VC-30]`

## Contracts

### [CT-12] Backend members

Kind: api · Anchor: null

Added to `doc/architecture/specs/backend/backend.spec.md` `[CT-1.5]`:

```kotlin
enum class ReadingDirection { Ltr, Rtl }

interface Backend {
    suspend fun entry(ref: MediaRef): ListEntry?                   // null when the library lacks it
    suspend fun readingDirection(ref: MediaRef): ReadingDirection? = null
}
```

`readingDirection` answers null when the backend does not know: the reader then uses `[CT-9.2]`'s
kind default. `MangaBakaBackend` maps them, and completes
`doc/architecture/specs/backend/mangabaka.spec.md` `[CT-16]`:

| Backend | MangaBaka |
| --- | --- |
| `entry` | `GET /v1/my/library/{id}`; a 404 is `null` |
| `Media.longStrip` | In `details` only: the `tags` of `GET /v2/series/{id}?schema=full` hold tag id 93 (`Work Info > Page Layout > Longstrip`). `/v2/series/batch` carries no tags, so `media` keeps `false`. |
| `MediaDetails.chapters` | `total_chapters` |
| `readingDirection` | `GET /v1/series/{id}/collections`: the `reading` of the first collection whose `language.iso` equals the series' `original_language`, else of the first collection, else `null` |

### [CT-14] Reader store

Kind: schema · Anchor: null

```sql
create table progress_outbox (
  backend text not null, media_id text not null,
  progress real not null, queued_at integer not null,
  primary key (backend, media_id)
);
create table reader_position (
  source integer not null, chapter_url text not null, page integer not null,
  primary key (source, chapter_url)
);
create table reading_mode (
  backend text not null, media_id text not null, mode text not null,
  primary key (backend, media_id)
);
```

The tables extend `doc/architecture/specs/app.spec.md` `[CT-4.5]` through the next additive
migration, and their queries live in `Reader.sq`. `mode` holds a `ReadingMode` name, and `source`
is Mihon's `Source.id`. The store holds no read flags (`[KD-13]`). `reader_position.page` counts
from 0. Showing a page writes it, and finishing the chapter deletes the row. `Store.clearUserData`
clears `progress_outbox` and `reader_position`. `reading_mode` is kept across sign-outs, like
`excluded_scanlator`. `progress_outbox` and `reading_mode` join the merged-series id rewrite in
`doc/architecture/specs/app.spec.md`. The same migration invalidates every stored
`media_details.details` but keeps its cover sample, so the next opening fetches details carrying
`[CT-12]`'s `longStrip` and `chapters`.

### [CT-5.1] Read state

Kind: invariant · Anchor: `app/src/main/kotlin/app/koma/data/LibraryRepository.kt`

```kotlin
fun chapterNumber(number: Float): Double? = number.takeIf { it >= 0f }?.toString()?.toDouble()
fun isChapterRead(number: Double?, progress: Double?): Boolean =
    number != null && number <= (progress ?: 0.0)

val effectiveProgress = maxOf(entry?.progress ?: 0.0, outbox?.progress ?: 0.0)
```

Chapter numbers compare exactly: progress 4 leaves chapter 4.5 unread. Mihon gives `-1` for a
chapter with no number, which is never shown read and never moves progress. The `Float` goes
through its decimal string, so chapter `10.1f` equals progress `10.1`. Every read state passes
`effectiveProgress`, both the title screen's and the unread badge's
(`doc/architecture/specs/sources/sources.spec.md` `[KD-33]`). A queued write therefore shows as
read before the backend has it.

### [CT-6.1] Progress push

Kind: invariant · Anchor: null

A chapter is finished when its last page's image has been shown. Finishing chapter `c` with
`n = chapterNumber(c.chapter_number)` and `n > effectiveProgress` raises the outbox row to `n`.
Flushes never overlap. Flushing a row of progress `p`:

1. Read `backend.entry(ref)`. If its progress is at least `p`, or its status is `Completed`,
   delete the row and stop.
2. Status: no entry, `Planning`, `Considering`, `Paused` or `Dropped` becomes `Reading`. `Reading`
   and `Rereading` stay. When the details' `chapters` is known and `p ≥ chapters`, a `Reading`
   status becomes `Completed`.
3. Call `addEntry(ref, status, progress = p)` when there was no entry, else
   `updateEntry(ref, status, progress = p)`. Store the entry in `library_entry` as `setStatus`
   does, then delete the row if it still holds `p`.

Progress never decreases through the app. A failed flush keeps the row for the next flush.

### [CT-9.2] Reading mode

Kind: invariant · Anchor: null

```kotlin
enum class ReadingMode { LongStrip, PagedLtr, PagedRtl }

val details = mediaRepository.loadDetails(ref)
val direction = try { backend.readingDirection(ref) } catch (e: IOException) { null }
val mode = readingMode?.mode
    ?: when {
        details.media.longStrip -> ReadingMode.LongStrip
        (direction ?: if (details.media.kind == Kind.Manga) ReadingDirection.Rtl else ReadingDirection.Ltr) ==
            ReadingDirection.Rtl -> ReadingMode.PagedRtl
        else -> ReadingMode.PagedLtr
    }
```

`readingMode` is the title's `reading_mode` row, written only by the user's choice. `direction` is
read when a title that is not a long strip first opens. It is kept in
`doc/architecture/specs/discover.spec.md` `[CT-15]`'s `cache`, which the reader's migration creates
if Discover has not shipped it. Paged right to left shows the first page at the right and advances
leftward. Changing the mode keeps the current page.

## Acceptance

Tests run on the JVM under Robolectric. They stub MangaBaka's API and Atsumaru's with
`FakeNetwork`. The harness records image URLs and normally returns image failures.

- `[VC-3]` Given a title bound to the Atsumaru stub, when a chapter whose stubbed page list holds
  three images opens in the reader, then the reader shows those three images, loaded from the
  stub's `/static/pages/…` URLs. — demonstrates `[SO-12]`
- ~~`[VC-4]` Given remote progress 4 and stubbed chapters 1, 2, 4, 4.5 and one without a number,
  when the chapter list opens, then 1, 2 and 4 show read, and 4.5 and the unnumbered one show
  unread.~~
- `[VC-4.1]` Given remote progress 4, stubbed chapters 4.5 and 5, and the stub offline, when
  chapter 4.5's last page is shown and the chapter list reopens, then 4.5 shows read and 5 shows
  unread. — demonstrates `[SO-4]`
- `[VC-5]` Given remote progress 4 with state `plan_to_read`, when chapter 4.5's last page is shown,
  then the stub receives `PATCH /v1/my/library/{id}` with `progress_chapter: 4.5` and
  `state: "reading"`. Given no entry, it receives `POST` instead. When chapter 3 is finished
  instead, the stub receives no write. When the stub is offline, the write arrives on the next
  launch. — demonstrates `[SO-4]`
- ~~`[VC-10]` Given a binding and an outbox row for series `5`, when the stub answers series `5`
  with `state: merged` and `merged_with: 9`, then both rows move to `9` and the next progress write
  targets `/v1/my/library/9`.~~
- `[VC-11]` Given stub series A whose details' `tags` hold tag 93, B a manhwa with no such tag and
  no collection, C a manhwa whose only collection reads `rtl`, and D a manga with no collection,
  when each opens in the reader, then A is a long strip, B is paged left to right, and C and D are
  paged right to left. After the user switches B to long strip, B reopens as a long strip. When the
  collections endpoint answers 500, C opens paged left to right. — demonstrates `[SO-7]`
- `[VC-29]` Given a chapter of three stubbed pages, when the user leaves it on its second page and
  reopens it, then it opens on its second page. After its last page has been shown, it reopens on
  its first page. — demonstrates `[SO-21]`
- `[VC-30]` Given stubbed chapters 2 and 1, listed newest first, of two pages each, when chapter 1
  opens and the user pages past its last page, then chapter 2's first image loads from its stub
  URL. Paging back past chapter 2's first page shows chapter 1's last page. — demonstrates
  `[SO-21]`
- `[VC-31]` Given an entry `completed` at progress 3, when chapter 4's last page is shown, then the
  stub receives no write, then or on the next launch. Given a queued write and the stub offline,
  when the user signs out and back in, then the stub receives no write. — demonstrates `[SO-4]`
- `[VC-32]` Given a page list stub answering 500, when the chapter opens, then the reader shows
  Retry, and Retry after the stub recovers shows the pages. Given a chapter whose last image fails,
  when the user reaches it, then the stub receives no progress write. — demonstrates `[SO-12]`

## Caveats

- `[C-13]` MangaBaka's series collections are beta (`[KD-26]`). Their failure only loses the
  edition direction in `[CT-9.2]`.
- `[C-16]` `ChapterRowTest` already proves `[CT-5.1]`'s exact comparison and unnumbered chapters.
  `[VC-4.1]` adds only the queued write.
- `[C-17]` `GET /v1/my/library/{id}` needs a session, so its reply shape and its 404 for a missing
  entry are unverified.
