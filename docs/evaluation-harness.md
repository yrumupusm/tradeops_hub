# Evaluation Harness

## Goal

The harness fixes the watchlist update user journeys so future changes do not
quietly break data integrity, permissions, idempotency, or auditability.

## Layers

| Layer | Purpose | Gate |
|---|---|---|
| Unit | Canonicalization, validation, comparison, idempotency, authorization | backend test task |
| Integration | HTTP contract, persistence, transactions, safe errors | backend integration-test task |
| Scenario | Fixed operational journeys against a running service | Planned: `scripts/verify-runtime.ps1` |
| Evidence | Compact, secret-free summary of runtime checks | Planned; no evidence exporter implemented |

The current server-free gate is `scripts/verify-local.ps1`. Runtime and final gates are not yet implemented. Scenario definitions describe acceptance criteria, not recorded execution results.

## Required watchlist scenarios

1. An operator runs fictional version A; the first immutable snapshot contains
   only `ADDED` records and becomes the current view.
2. The same operator runs fictional version B; the system reports at least one
   `ADDED`, `CHANGED`, and `REMOVED` record with accurate immutable totals.
3. Re-running the exact version B payload is idempotent: it does not duplicate
   snapshots or change records.
4. A malformed or duplicate-external-ID row creates a safe failed run and
   leaves the current view unchanged.
5. A viewer can read the current list and changes but cannot trigger a run.
6. List search filters by normalized name, source, country, and status.
7. Health and error responses never expose a password, database URL, provider
   URL, raw CSV row, or stack trace.

## Evidence rules

- Runtime evidence is written under ignored `artifacts/`.
- Include endpoint, HTTP status, correlation ID, run ID, snapshot version,
  aggregate counts, and scenario result only.
- Never include cookies, authorization headers, passwords, connection strings,
  raw CSV rows, provider URLs, or browser storage.

## Change rule

When a user-visible workflow, API contract, audit field, or validation rule
changes, update the closest focused test and scenario before declaring the
change complete.
