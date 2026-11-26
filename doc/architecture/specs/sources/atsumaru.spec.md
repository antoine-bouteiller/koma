---
title: Atsumaru source
status: review
author: Antoine Bouteiller
date: 2026-11-26
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

## Outcome

- `[SO-9]` A user can read a title from Atsumaru without searching when Atsumaru carries its
  MangaBaka id. Each chapter number appears once. — demonstrated by `[VC-12.1]`, `[VC-13]`,
  `doc/architecture/specs/reader.spec.md` `[VC-3]`

## Contracts

### [CT-17] Atsumaru finder

Kind: api · Anchor: null

Defined for MangaBaka media only. It searches each title variant through `getSearchManga`, reads
`GET https://atsu.moe/api/manga/page?id={SManga.url}` through the source's `client` for the top
three hits not yet read, and answers the first whose `mangaBakaId` equals `media.ref.id`.

## Acceptance

Tests stub Atsumaru's API with `FakeNetwork`, through the client sources share
(`doc/architecture/specs/sources/sources.spec.md` `[CT-2.3]`).

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
  breaks, the fix is a fresh copy of `src/en/atsumaru/` from `keiyoushi/extensions-source`. The
  finder's manga page read is Koma's and follows the extension's. On 2026-11-18 the API answered
  plain HTTPS without a challenge.
- `[C-11.1]` The Atsumaru finder misses a title whose Atsumaru page lacks `mangaBakaId`, or whose
  title variants rank it below the top three hits. Such a title is bound by hand from the
  candidates, which may include novels: the extension's search decides what it answers.
