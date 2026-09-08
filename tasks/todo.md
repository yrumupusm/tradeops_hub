# TradeOps Hub Delivery Checklist

## Foundation

- [x] Create an independent repository without company source or data.
- [x] Define product scope, safety boundaries, working agreement, and harness.
- [x] Create Spring Boot backend skeleton and baseline health endpoint.
- [x] Create Next.js TypeScript frontend skeleton and accessible app shell.
- [x] Add Docker Compose PostgreSQL and environment examples.
- [x] Add server-free local verification gate.

## MVP — watchlist update workflow

- [x] Implement authentication and backend-enforced roles.
- [x] Define PostgreSQL schema and migration baseline.
- [x] Define the fictional watchlist source contract, data dictionary, and deterministic XML/CSV two-version fixtures.
- [x] Implement source-run history and immutable snapshots with safe run errors.
- [x] Implement snapshot comparison: added, changed, removed, and unchanged counts.
- [x] Implement operator-triggered update endpoint with a concrete local source adapter.
- [x] Implement watchlist search, source/country filters, and change-review APIs.
- [x] Implement trade-transaction CSV import validation, duplicate detection, and persisted import history.
- [x] Implement transaction search and PostgreSQL-compatible monthly aggregate API.
- [x] Add operator/admin screening-review and atomic audit writes.
- [x] Implement runtime scenario/evidence gates.

### Completed delivery — runtime verification

- [x] Correct PostgreSQL transaction search, monthly aggregation and generated import IDs; add focused integration tests.
- [x] Enforce screening roles and atomically persist review/audit records through a service and repository.
- [x] Correct console failure messages and remove unused aggregate-request coupling.
- [x] Implement fixed runtime scenarios and allowlisted JSON evidence; reject stale/incomplete evidence.
- [x] Add a final gate that starts an isolated PostgreSQL/API/web stack, verifies it, and cleans up its own resources.
- [x] Verify failure propagation, run the local and isolated runtime gates, and exercise the browser workflow.
- [x] Update API/architecture/runbook documents and prepare verified commits for GitHub.

### Runtime verification — 2026-09-08

- Focused transaction, screening, authentication and watchlist tests passed.
- Final local gate: 30 backend tests, 8 verification-tool tests and frontend type checking passed.
- API packaging and frontend production build passed.
- Isolated PostgreSQL: 11 HTTP scenarios, evidence validation and persisted row/audit counts passed.
- Default final gate cleaned up its own API, web and temporary database; existing application volumes were not used.
- Playwright: login, current data, idempotent rerun, real missing-source failure with list preservation, and zero console errors confirmed.
- Evidence and browser captures remain ignored under artifacts/.

## Optional external-source adapter — defer until MVP is complete

- [ ] Jointly verify the current BIS DPL landing/download path and terms before any implementation.
- [ ] Add a separately configured adapter only if the source is stable and publicly permitted.
- [ ] Treat URL, format, rate-limit, download failure, and upstream changes as safe failed runs; never block the core demo release.

## Repository and documentation release

- [x] Prepare product-focused README and console copy for the public repository.
- [x] Document the implemented architecture with C4, ADRs, and arc42; reconcile the API contract with code.
- [x] Exclude generated files and verify the initial commit contents for `yrumupusm/tradeops_hub`.
- [ ] Add CI for frontend, backend, and local verification.
- [ ] Add screenshots using fictional data only.
- [x] Document setup, local sample accounts, and operational checks.

### Initial publication verification — 2026-09-08

- `scripts/verify-local.ps1`: 18 backend tests passed and frontend type check passed.
- `npm run build` in `frontend`: passed; generated HTML retains the fictional-data indicator and uses workspace/product copy.
- Markdown file links and candidate-file credential patterns checked before the initial publication.
- PostgreSQL runtime/browser workflows were not reverified in this documentation release; open gaps remain below.

## Implementation gaps found during architecture review

- [x] Replace H2-specific monthly aggregation SQL and verify against PostgreSQL.
- [x] Move screening writes into a transactional service/repository and enforce operator/admin roles.
- [ ] Add import history/rejection read APIs and complete audit coverage for mutations.
- [ ] Implement a source interface and scheduled execution (the current adapter is concrete and manually triggered).
- [ ] Verify concurrent source updates, database-failure run tracking, and snapshot immutability enforcement.
- [ ] Complete general login and list filter/paging controls in the console.
- [ ] Extend CSV parsing/input limits and database-exception coverage.
- [ ] Validate screening target references against stored transactions and source records.

## Korean console localization

- [x] Translate navigation, actions, metadata, states, and safe errors into Korean.
- [x] Render field changes in Korean and preserve source identifiers.
- [x] Update the console acceptance scenario and runbook.
- [x] Verify and rebuild the running web console; include the verified change in the repository release.

- Verification: local gate with -SkipBackend passed (8 verification-tool tests and frontend type checking); production build passed.
- Served Korean HTML and real operator login/empty states passed. Playwright mocked responses verified translated field differences, success, duplicate, source failure, authorization, unknown/network/login errors, and desktop/mobile layout.
- Browser checks did not create watchlist records. Backend tests and the full PostgreSQL runtime suite were not repeated for this presentation-only change.
