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
- [x] Implement transaction search and monthly aggregate API (PostgreSQL compatibility remains below).
- [x] Add screening-review and audit writes (authorization and atomicity remain below).
- [ ] **In progress:** Implement runtime scenario/evidence gates.

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

### Verification — 2026-09-08

- `scripts/verify-local.ps1`: 18 backend tests passed and frontend type check passed.
- `npm run build` in `frontend`: passed; generated HTML retains the fictional-data indicator and uses workspace/product copy.
- Markdown file links and candidate-file credential patterns checked before the initial publication.
- PostgreSQL runtime/browser workflows were not reverified in this documentation release; open gaps remain below.

## Implementation gaps found during architecture review

- [ ] Replace H2-specific monthly aggregation SQL and verify against PostgreSQL.
- [ ] Move screening writes into a transactional service/repository and enforce operator/admin roles.
- [ ] Add import history/rejection read APIs and complete audit coverage for mutations.
- [ ] Implement a source interface and scheduled execution (the current adapter is concrete and manually triggered).
- [ ] Verify concurrent source updates, database-failure run tracking, and snapshot immutability enforcement.
- [ ] Complete general login, list filter/paging controls, and safe failed-run messages in the console.
- [ ] Extend CSV parsing/input limits and database-exception coverage.
