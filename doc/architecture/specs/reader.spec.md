---
title: Koma reader
status: review
author: Antoine Bouteiller
date: 2026-11-26
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

A bound title lists its source's chapters, and the reader shows their pages. Reading a chapter
updates the backend without a manual step, and the chapter list shows as read what the backend's
progress covers.

- `[G-3]` Mark chapters read automatically, in both directions, with the backend as the source of
  truth for progress.
- `[NG-4]` Offline chapter downloads and background library updates with notifications.

## Design

- `[PI-3]` The backend owns progress. Local state may cache or queue progress, never contradict it.

| Decision | Choice | Rationale |
| --- | --- | --- |
| `[KD-10]` Merged series | A series answering `state: merged` has its id replaced by `merged_with` in every local row that stores it | MangaBaka merges duplicate series; an old id stops receiving updates. |
| `[KD-13]` Read state | Derived, never stored: see `[CT-5.1]` | `[PI-3]`. A stored flag would contradict the backend after an edit on mangabaka.org. |
| `[KD-14]` Progress writes | Queued in a SQLite outbox, coalesced per media to the max, flushed immediately and on every launch or foreground | Reading must not block on the network. |
| `[KD-15.2]` Page images | Coil loads each page through the source's `HttpSource.getImage(page)`, after `getImageUrl(page)` when the page has no `imageUrl`. The source's client, headers and interceptors apply. | Mihon's extensions put headers, rate limits and descrambling in their OkHttp client, so loading through it keeps them working unchanged. Coil caches pages on disk. |
| `[KD-16.1]` Reader | Three modes: a vertical long strip, paged left to right, and paged right to left. The strip is a `LazyColumn`. Paged modes are a `HorizontalPager`, with `reverseLayout` for right to left. The reader opens at `[CT-9.2]`'s mode, and the user can change it per title. | The series type alone misplaces titles: The Breaker is manhwa but paged. Both layouts virtualize, and the pager snaps one page at a time. |
| `[KD-26]` Collections endpoint | Reading direction comes from MangaBaka's beta `v1` series collections. A failed collections read falls back to `[CT-9.2]`'s kind default. | No stable endpoint gives it. |

## Outcome

- `[SO-4]` Finishing a chapter raises MangaBaka progress without user action, and a chapter list
  shows as read every chapter the progress covers, decimal chapters included. — demonstrated by
  `[VC-4]`, `[VC-5]`
- `[SO-7]` The reader opens a long-strip title as a vertical strip and any other title paged, in the
  direction the backend gives, else right to left for manga and left to right otherwise. The user
  can override the mode per title. — demonstrated by `[VC-11]`
- `[SO-12]` The reader shows a chapter's pages through its source's own client.
  — demonstrated by `[VC-3]`

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
| `entry` | `GET /v1/my/library/{id}` |
| `Media.longStrip` | `tags_v2` holds tag 93 (`Work Info > Page Layout > Longstrip`) |
| `MediaDetails.chapters` | `total_chapters` |
| `readingDirection` | `GET /v1/series/{id}/collections`: the `reading` of the first collection whose language is the series' `original_language`, else of the first collection, else `null` |

### [CT-14] Reader store

Kind: schema · Anchor: null

```sql
create table progress_outbox (
  backend text not null, media_id text not null,
  progress real not null, status text, queued_at integer not null,
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

The tables extend `doc/architecture/specs/app.spec.md` `[CT-4.5]` through an additive migration.
`status` and `mode` hold enum names; `source` is Mihon's `Source.id`. The store holds no read flags
(`[KD-13]`). `[KD-10]` rewrites `media_id` in `library_entry`, `progress_outbox`, `reading_mode`
and `doc/architecture/specs/sources/sources.spec.md` `[CT-13.1]`'s `binding`.

### [CT-5.1] Read state

Kind: invariant · Anchor: null

```kotlin
val effectiveProgress = maxOf(entry?.progress ?: 0.0, outbox?.progress ?: 0.0)
fun number(c: SChapter): Double? = c.chapter_number.takeIf { it >= 0f }?.toString()?.toDouble()
fun isRead(c: SChapter): Boolean = number(c)?.let { it <= effectiveProgress } ?: false
```

Chapter numbers compare exactly: progress 4 leaves chapter 4.5 unread. Mihon gives `-1` for a
chapter with no number, which is never shown read and never moves progress. The `Float` goes
through its decimal string, so chapter `10.1f` equals progress `10.1`.

### [CT-6.1] Progress push

Kind: invariant · Anchor: null

A chapter is finished when its last page has been on screen. Finishing chapter `c` with
`n = number(c)` and `n > effectiveProgress` upserts the outbox row to `n`. Flushing a row:

1. Read `backend.entry(ref)`. If its progress ≥ queued progress, delete the row and stop.
2. Status: no entry, `Planning`, `Considering`, `Paused` or `Dropped` becomes `Reading`; `Reading`
   and `Rereading` stay. When `MediaDetails.chapters` is known and `n ≥ chapters`, a `Reading`
   status becomes `Completed`. An entry already `Completed` is never written.
3. `addEntry(ref, status, progress = n)` when there was no entry, else
   `updateEntry(ref, status, progress = n)`, then delete the row.

Progress never decreases through the app. A failed flush keeps the row for the next flush.

### [CT-9.2] Reading mode

Kind: invariant · Anchor: null

```kotlin
enum class ReadingMode { LongStrip, PagedLtr, PagedRtl }

val direction = try { backend.readingDirection(media.ref) } catch (e: IOException) { null }
val mode = readingMode?.mode
    ?: when {
        media.longStrip -> ReadingMode.LongStrip
        (direction ?: if (media.kind == Kind.Manga) ReadingDirection.Rtl else ReadingDirection.Ltr) ==
            ReadingDirection.Rtl -> ReadingMode.PagedRtl
        else -> ReadingMode.PagedLtr
    }
```

`readingMode` is the title's `reading_mode` row, written only by the user's choice. `direction` is
read when a title that is not a long strip first opens, and kept in
`doc/architecture/specs/discover.spec.md` `[CT-15]`'s `cache`. Paged right to left shows the first
page at the right and advances leftward. Changing the mode keeps the current page.

## Acceptance

Tests run on the JVM under Robolectric. They stub MangaBaka's API and Atsumaru's with
`FakeNetwork`. The harness records image URLs and normally returns image failures.

- `[VC-3]` Given a title bound to the Atsumaru stub, when a chapter whose stubbed page list holds
  three images opens in the reader, then the reader shows those three images, loaded from the
  stub's `/static/pages/…` URLs. — demonstrates `[SO-12]`
- `[VC-4]` Given remote progress 4 and stubbed chapters 1, 2, 4, 4.5 and one without a number,
  when the chapter list opens, then 1, 2 and 4 show read, and 4.5 and the unnumbered one show
  unread. — demonstrates `[SO-4]`
- `[VC-5]` Given remote progress 4 with state `plan_to_read`, when chapter 4.5's last page is shown,
  then the stub receives `PATCH /v1/my/library/{id}` with `progress_chapter: 4.5` and
  `state: "reading"`; given no entry, it receives `POST` instead; when chapter 3 is finished
  instead, the stub receives no write; when the stub is offline, the write arrives on the next
  launch. — demonstrates `[SO-4]`
- `[VC-10]` Given a binding and an outbox row for series `5`, when the stub answers series `5` with
  `state: merged` and `merged_with: 9`, then both rows move to `9` and the next progress write
  targets `/v1/my/library/9`.
- `[VC-11]` Given stub series A tagged Longstrip, B a manhwa with no tag and no collection, C a
  manhwa whose only collection reads `rtl`, and D a manga with no collection, when each opens in
  the reader, then A is a long strip, B is paged left to right, and C and D are paged right to
  left; after the user switches B to long strip, B reopens as a long strip; when the collections
  endpoint answers 500, C opens paged left to right. — demonstrates `[SO-7]`

## Caveats

- `[C-13]` MangaBaka's series collections are beta (`[KD-26]`); their failure only loses the
  edition direction in `[CT-9.2]`.
