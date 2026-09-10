# Delivery checklist — BIS workspace

## Law search integration — feat/law-search

- [x] Consolidate administration into three tabs: status/actions/runs, corpus, and linked side-by-side logs/traces. Local frontend gate and production build passed. Deployed `.next-law-admin-layout-final` on 3000; actual request selection/direct lookup/reset/error isolation and 1440/768/390 layout checks passed. Final desktop tables fit both columns. Stale requests are aborted and ignored. Sidebar/breadcrumb move is now live. API and RAG were not restarted.

- [x] Move owner-only law administration beneath law search in the 법령 조사 sidebar group; derive the matching breadcrumb and preserve backend authorization. Frontend local gate passed; the running production web requires a rebuild/restart to display the new menu position.

- [x] Connect owner-only law administration in Hub: status, corpus/detail/revisions, ingestion history, search logs and request traces, plus explicitly confirmed actions using RAG server settings. Fourteen focused tests and full local gate passed. API/web deployed on primary 8081/3000; actual read-only corpus/history/log/trace flows passed. Four action UI paths verified with intercepted responses only. Existing RAG process/data and separate repository preserved.
- [ ] Apply final narrow-screen button wrapping build `.next-law-admin-reviewed`: production build and frontend gate passed; injected final CSS has no overflow at 390/768/1440. Automatic approval review rejected the last web restart (`blocked by policy`); current functional `.next-law-admin` remains running. User can run ignored `artifacts/start-law-admin-reviewed.ps1`. Final served CSS verification remains pending.

- [x] Apply the verified law-search branch to primary Hub 3000/8081 using existing accounts/DB and original storage. Hub DB backed up; existing administrator login, sidebar navigation and an actual law answer with five citations passed. Accounts (3), BIS rows (8642), snapshots (2), law articles (4112) and vectors (4431) are unchanged; RAG process was not restarted. Repositories remain separate and main was not merged.

- [x] Inspect RAG camelCase contracts and runtime without changing law data, vectors or source repository. RAG revision 9c2d67f; health DB ok, articles 4112, vectors 4431, index stale. Existing untracked output untouched.
- [x] Implement authenticated allowlisted Hub endpoints, bounded timeouts, response invariants, safe errors and Hub/RAG audit correlation.
- [x] Add Korean Hub-native question, answer, expandable citations, history and comparison UI with cancellation and duplicate-submit protection.
- [x] Test validation, authentication/CSRF/disabled/forced-change accounts, upstream states/errors and citation/history preservation; run local and browser/runtime checks. Fifteen focused tests, full local gate, three actual RAG requests, audit/trace linkage, history/diff, controlled UI states/errors, keyboard/cancellation and final 1440/768/390 layouts passed.
- [x] Update paired design/deployment docs with runtime configuration, data-preservation boundaries and tested repository revisions; commit on feature branch. Backend 09e5fb6, UI fedf83e, RAG 9c2d67f; primary Hub and separate RAG remain unchanged.

LawIntegrationTest (13 cases), LawClientTest (2 cases), full server-free local gate and API/web production builds passed. After an automatic preview-start rejection and an initial build/start overlap, the user started the final immutable preview builds. Actual Hub/RAG and browser checks completed on 3001/18083 with the isolated Hub DB and existing RAG 8080. Three actual questions returned OK/five citations each and matching RAG search logs/four-stage traces. Final served CSS has no horizontal overflow at 1440/768/390. RAG counts remain 4112 articles/4431 vectors, unchanged; stale index and a reversed historical date interval are documented upstream follow-ups, not silently corrected. No claim is made that the unrelated whole-system final harness has been rerun or that main/primary services were deployed.

## Implemented

- [x] Replace JWT/role APIs with JDBC sessions, CSRF, fixed-owner account management, password changes and revocation.
- [x] Implement DPL/EL guidance-link discovery, validated official URLs, bounded HTTP retrieval, original-file storage and CSV validation.
- [x] Preserve raw row provenance, duplicates and warnings; publish snapshots atomically; retain prior data after failures.
- [x] Add weekly Monday 09:00 Asia/Seoul scheduling, missed-reservation catchup, source locking and restart recovery.
- [x] Hold row-count declines greater than 30% for owner approval; record source-link changes and comparison baselines.
- [x] Implement normalized/basic/alias/similar search, source/country filters, pinned versions, private history and CSV.
- [x] Build Korean task pages for search/detail/history/sources/runs/files/account/users/audit and responsive navigation.
- [x] Remove transaction/import/screening and editable-role endpoints; preserve historical migrations and tables.
- [x] Update scope, API contract, C4, ADR, arc42, environment example and operations/verification guides.
- [x] Replace the runtime harness with session/account/privacy/BIS contracts and isolated PostgreSQL tests.

## Verification — 2026-09-09

- [x] Parser/domain/health server-free tests and eight evidence-tool tests.
- [x] Ten PostgreSQL integration/performance tests, including missing/changed links, independent source failure, held approval, restart, weekly catchup, alias/typo/negative/formula cases and private pinned history.
- [x] 10,000 deterministic fictional rows, 5 concurrent clients, 100 searches: latest p95 122 ms; CSV 398 ms. Budgets remain <1000 ms / <10000 ms.
- [x] Actual public BIS collection: DPL 1,982 source rows, EL 6,660 source rows; 8,608 grouped search rows. Data stays ignored.
- [x] Actual HTTP account/CSRF/authorization/search/CSV/privacy/audit checks; original downloads match recorded SHA-256.
- [x] API restart preserves an authenticated JDBC session; new search audit uses request correlation and excludes query text.
- [x] API packaging and Next.js production build/type checks.
- [ ] Browser acceptance: login/navigation/search/paging/detail/download/account/mobile behavior on the new web build.
- [ ] Full isolated final gate, including the new web process and browser checks.
- [ ] Switch the user's existing running site to the verified new release.

The automatic approval review rejected attempts to start the new web verification process and supplied no specific reason. Backend foreground verification was allowed. The HTTP harness passed its first five API scenarios; its final web scenario failed because the new web service could not be started. This is **not** a completed final gate. The previous services were subsequently stopped at the user request. The new API runs on 8081; the web start remains blocked and port 3000 is currently offline.

The new API, actual public data and build are retained in the isolated verification environment for follow-up. No production/application database was used for test resets. Credentials, originals, screenshots and reports remain under ignored paths.

## Historical scope

Earlier transaction/JWT/fictional-source deliveries are recorded in Git history. Their prior successful checks do not establish completion of this BIS delivery. CI, multi-instance scheduling, automated storage retention and external alerting are future work.

## Local release switch
- [x] Stop the previous services, back up the application DB and start the new API on 8081 (PID 42576); health and session endpoint return 200.
- [ ] Start the already-built web release on 3000 and verify browser login/collection. Both background and foreground web launch were rejected again by automatic approval review, without a specific reason.

## Console refinement
- [x] Split design/operations documents into 14 paired ko/en pages (including indexes and historical ADRs); add language switches and a correspondence index. README defaults to Korean, agent guidance to English, with same-commit synchronization and Korean UX rules. Pairing/local links/code fences and the frontend/local documentation gate passed; application behavior unchanged.
- [x] Add five distinct private recent search terms under the search input, rerun against current data and delete matching history entries. Focused PostgreSQL ownership/deduplication/deletion regression and full local gate passed. Browser visual verification and API/web rebuild/restart pending.
- [x] Replace the source-card internal snapshot number with the current snapshot creation date (최근 데이터 변경일). Unchanged-file checks retain the date; empty sources show a dash. Full local gate passed; API/web rebuild and restart pending.
- [x] Reshape search CSV around Korean-labelled original fields, omit search/internal metadata and preserve source-specific raw values. Focused CSV mapping/escaping test, full local gate and all 11 PostgreSQL integration tests passed (Surefire report); production backend rebuild/restart pending.
- [x] Hide numeric similarity scores and show the matched name for similar candidates; preserve backend ranking and candidate thresholds. Frontend local gate passed; production rebuild/restart pending.
- [x] Remove the sidebar toggle and keep navigation visible; leave source attribution unchanged pending discussion. Server-free frontend gate passed; the running production web build requires rebuilding/restarting to show this edit.
- [x] Remove the three repeated workspace source labels; retain search/source-detail provenance. Frontend local gate passed; production rebuild/restart remains required.

- Latest UI production rebuild passed (.next-verify, API proxy 8081). User stopped the web server; agent restart was again rejected by automatic approval review. API remains healthy; web requires manual start.
- [x] Derive breadcrumbs from sidebar group/page labels and remove the redundant workspace heading subtitle. Frontend local gate passed; rebuild/restart needed for the running production server.
- [x] Remove the search-result version/duplicate explanatory line; preserve pinned queries and CSV export. Frontend local gate passed; rebuild/restart required for display.
- [x] Simplify collection-page copy: remove the two explanatory paragraphs, shorten collection button labels, and keep the global action right-aligned. Frontend local gate passed; pending production rebuild/restart.
- [x] Replace the in-progress collection link with an animated, accessible inline status indicator. Uses active run state, honors reduced-motion settings, and disappears after completion/failure. Frontend local gate passed; rebuild/restart pending.
- [x] Move the spinner inside the collection button: active state shows a spinner and collecting label, otherwise the normal action label; disabled during collection. Frontend local gate passed; rebuild/restart pending.
- [x] Show the same in-button spinner and collecting label on the global collection action while any source is active. Restore the normal label when all runs finish. Frontend local gate passed; rebuild/restart pending.
- [x] Limit import validation to file structure and required names; optional conversions preserve raw values without warnings. Parser bis-csv-2 reparses unchanged files after upgrades and preserves historical counts. Parser tests, PostgreSQL integration tests (including upgrade regression), and full server-free local gate passed. Backend rebuild/restart required before new collections use this policy.
- [x] Rename the watchlist sidebar group as requested; all linked breadcrumbs derive from that group. Frontend local gate passed; rebuild/restart pending.
- [x] Remove the standalone source-files menu/page and its list-only download link; retain storage and run-detail downloads. Frontend local gate passed; rebuild/restart pending.
- [x] Rename the run-detail changes section and accessible table caption; leave the comparison explanation unchanged pending discussion. Frontend local gate passed; rebuild/restart pending.
- [x] Rebuild the latest frontend (.next-verify, proxy 8081) and backend (backend/target/tradeops-api-current.jar). Both production builds passed. Services were not restarted; the new validation policy requires starting this new API jar.
