# Product scope

[한국어](../ko/product-scope.md) · [English](product-scope.md)

An operational tool that periodically collects public BIS watchlist data, searches names and downloads originals/search results. Only the user-authorized public DPL and Entity List are active sources.

## Screens and users

The sidebar stays visible without a collapse button or automatic hiding. Breadcrumbs use the sidebar group and page label (for example 운영 관리 / 사용자 관리). No repeated common subtitle above page headings. Source attribution is not repeated beside headings or in sidebar/content footers; source badges, collection/detail agency information, original links and collection times provide provenance.

| Menu | Capability |
| --- | --- |
| 우려거래자 검색 | Name/alias/similar candidates, source/country/sort/paging, detail and CSV |
| 내 검색 이력 | Replay query/conditions/pinned versions; individual/all deletion |
| 우려거래자 수집 관리 | Per-source checks/success/schedule/row count/URL/alerts and manual collection |
| 수집 이력 | Status/stage/added/removed/unchanged/issues/held approval; result detail provides original download and version search |
| 내 계정 | Password change |
| 사용자 관리 | Owner-only creation, activation/deactivation and temporary passwords |
| 감사 이력 | Owner-only filtering by actor/action/period |

The owner is fixed by environment configuration. There is no role editor. Ordinary users share collection/search/download permissions. Temporary passwords require changing on first access; change/reset/deactivation revoke all sessions. Even the owner cannot access others' search terms.

## Collection policy

During collection, the individual button is disabled and shows a spinner with 수집 중; afterwards it returns to 데이터 수집. Run details remain accessible through collection history.

Check every Monday 09:00 Asia/Seoul. Persist next reservation and catch up one missed reservation after restart. Manual collection is also supported. Rediscover each section's download link each run and record guidance/download/final redirect URLs and hashes.

New URLs passing official HTTPS host and schema checks are accepted automatically. Missing/ambiguous URLs or schema mismatch leave failed history. 304/checksums avoid duplicate publication and originals are retained. Per-source locks prevent concurrent collection. DPL and EL complete/fail independently.

Validation covers file structure and required names only. Blank-name rows remain inspectable through safe error codes, row numbers and original files; the entire file is not published. Optional country/date conversion failures are not errors/warnings and raw values remain intact. A decline greater than 30% is a separate publication safeguard requiring owner approval.

Historical validation results remain unchanged. The first run under a new parser policy reprocesses even identical bytes; subsequent identical files with the same parser version skip duplicate publication.

## Search policy

Normalize separate search names without changing originals: handle whitespace, case, punctuation, accents and word order; find similar candidates through PostgreSQL trigrams. Basic and similar results can be combined or separated. Korean expansion is limited to an explicit dictionary; arbitrary company-name transliteration is unavailable.

Never merge original rows by company name. Only exact duplicate rows are grouped with a duplicate count. Scores represent name similarity, not identity or a sanctions conclusion. Search/detail/download use pinned original versions. History persists until deleted by its owner.

Recent searches show at most five distinct nonempty terms in muted gray text below the input. A click reruns against current data with current form filters. The × action removes that user's history for the term, including repeated entries; empty history hides the area. Numeric similarity is hidden in results, while ranking still uses it. Source cards show current snapshot creation time as 최근 데이터 변경일, not an internal version number. CSV uses Korean-labelled original fields rather than search/internal metadata; see the API contract.

## Exclusions

Transaction upload/aggregation, counterparty screening/decisions, role management, other institutions' lists and automatic legal decisions are excluded. Preserve existing DB/migrations but remove those public APIs. Multiple API instances, external alerts and automatic original deletion remain outside current scope.


Law search uses a separate RAG service. See [law integration](law-integration.md) for questions, citations, history/diff, authorization and data preservation.
