# Delivery checklist

## Review findings — 2026-09-13

- [x] Record today's unresolved service and presentation findings in paired [issue documents](../docs/en/known-issues.md).
- [ ] RAG-04: establish a reproducible quality evaluation baseline.
- [ ] RAG-01/RAG-02/RAG-03: improve long-article coverage, candidate relevance and answer/evidence verification against the baseline.
- [ ] SEARCH-01: define and verify general English abbreviation expansion separately from registered aliases.
- [ ] VIS-01/VIS-02/VIS-03: correct generated labels, replace the law screenshot and refine explanatory diagrams.

## Pre-publication completion review

- [x] Audit root and law-service documentation; all ko/en filenames are paired and local document links resolve. Update integration, product scope, index maintenance and repository retirement descriptions in both languages.
- [x] Correct final-gate PostgreSQL readiness to wait for TCP rather than the temporary initialization socket. Isolated final gate passed local/build/PostgreSQL/runtime/evidence/persistence/cleanup checks; evidence 8e3ffe12-fc38-4a47-bf23-eb22c457396b.
- [x] Run isolated monorepo regression: Hub gate and all 194 law tests passed with temporary H2/mock settings.
- [x] Diagnose 319 orphan vectors with zero missing current article IDs. Snapshot original collection, copy 4112 current vectors and verify vector/payload hashes. Switch law configuration; health healthy, DB unchanged. Original 4431-point collection and snapshot retained; no embedding requests.
- [x] Archive former law checkout under ignored artifacts, verify Git objects and remaining file hashes, remove former active path. Existing source Markdown directory and DB volumes unchanged; former remote remains private.
- [x] Verify corpus and trace input/button height and bottom alignment in browser (40px). Linked trace shows four stages; no document scroll at 1440x900/1366x768, no horizontal overflow at 390px.
- [x] Complete current browser login/search/CSV, actual DPL/EL collection (1982/6660), run-detail original download, real law answer OK/five citations, citation expansion and linked trace smoke checks. Bounded evidence saved under ignored artifacts.
- [x] Commit verified UI correction and TCP-readiness fix separately; complete paired documentation review and source publication without exposing the site.

## External publication (not enabled)

- Select hosting machine and stable URL; configure HTTPS, secure cookies, automatic startup/recovery and backups.
- Define visitor permissions and model usage limits before issuing shared access. Ordinary users currently have collection/download privileges.
- Keep law API, PostgreSQL and Qdrant internal. No tunnel/domain/firewall exposure was enabled during this review.

## Known limits

- Single Hub scheduler instance; no distributed scheduling or automatic source-file retention.
- Law vector reconciliation is manual. ID/count parity does not prove content freshness; automatic stale-vector cleanup/content-hash freshness tracking remains future work.
- Historical source-history-change detection remains visible until reviewed; source dates are preserved rather than silently corrected.
- Published screenshots require suitable non-sensitive fixture content; none are committed.

[Historical delivery record](history-before-prepublish.md) preserves previous milestones and superseded pending statements.
