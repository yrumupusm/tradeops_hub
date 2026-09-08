# Evaluation Harness

## Scope

The harness fixes operational journeys in [scenarios.json](../harness/scenarios.json). Each scenario declares its required assertion names. A successful result must contain every scenario and every assertion in order; skipped or missing checks cannot count as success.

## Verification layers

| Layer | Command | Purpose |
| --- | --- | --- |
| Local | `scripts/verify-local.ps1` | Backend tests, frontend type checking, verification-tool tests and Git exclusion checks |
| Runtime | `scripts/verify-runtime.ps1` | HTTP scenarios against a running, empty local API/DB and served web page |
| Evidence | `node scripts/verify-evidence.mjs --evidence <artifact> --verification-id <id>` | Match the current source, scenario manifest, execution ID, timestamp and complete assertion list |
| Final | `scripts/verify-final.ps1` | Local gate, builds, isolated PostgreSQL/API/web, runtime gate, direct persistence counts and cleanup |

The final gate uses a private temporary PostgreSQL container with a tmpfs data directory. It never reads or removes the application's existing database volume. Ports must be unused and all runtime HTTP targets must be loopback addresses. Runtime-only verification refuses a database with existing source runs or transactions before mutating it.

## Fixed scenarios

1. Health and safe authentication errors, including correlation IDs.
2. First source A snapshot: four added records.
3. Source B comparison: one added, one changed, one removed and two unchanged.
4. Same B/CSV reuses the snapshot without duplicate changes or altered prior counts.
5. Missing source returns a reviewable failed run and preserves current entities and changes.
6. Viewer can read but cannot start a source run.
7. Normalized name, country, provider, status and pagination filters.
8. Mixed transaction import, duplicate-file rejection, search and monthly totals.
9. Operator/admin screening writes; viewer/anonymous denial and invalid-input rejection.
10. Unknown body fields, invalid formats and invalid page parameters.
11. Served console HTML keeps the fictional-data indicator and functional workspace copy.

The transaction scenario reads the committed three-row fictional fixture, appends its first data row as a duplicate, and appends a fixed fictional row with a negative amount. Expected counts are three accepted, one rejected and one duplicate. No real data is used.

The final gate additionally checks PostgreSQL counts for snapshots (2), snapshot rows (8), changes (7), source runs (4), import runs (1), transactions (3), rejection records (2), reviews (2) and linked review/audit pairs (2).

## Evidence format and handling

`artifacts/final/<verification-id>/runtime.json` contains only:

- schema version, execution ID and UTC generation time;
- source fingerprint and scenario-manifest SHA-256;
- scenario IDs, status, assertion names and boolean results;
- request method, fixed endpoint path, HTTP status and validated correlation ID.

No response body, request body, query string, token, password, cookie, raw CSV/XML row or authorization header is serialized. A failed scenario marks the report failed, leaves later scenarios skipped and returns a nonzero exit code. Failure messages use bounded local codes.

The evidence validator rejects unknown fields, mismatched execution IDs, stale/future timestamps, changed sources, missing scenarios/assertions, invalid request metadata and failed/skipped outcomes. The source fingerprint includes Git-listed backend/frontend/scripts/harness/data files and excludes generated build output. It identifies working-tree content even before a commit. These are local consistency checks, not signed or tamper-proof attestations.

The final gate writes `summary.json` for its run and replaces `artifacts/final/latest.json` on both success and failure. The summary records each gate outcome and resource cleanup. Debug logs are separate ignored artifacts and must not be published as safe evidence.

## Browser verification

The runtime HTML check does not execute JavaScript. Browser verification is a separate step using Playwright: sign in, inspect the latest list and changes, re-run B, and request a missing fictional version to verify the failed-run message while retaining four entities and three changes. The request can be adjusted in the browser to select the missing version; the response must come from the actual API.

Browser screenshots and snapshots are local fictional-data artifacts. They are not automatically included in published evidence. `-KeepRuntime` retains a successful final-gate stack for browser inspection and records resource IDs in `runtime-info.json`; stop only those resources afterward.

## Remaining coverage

Concurrency, database-outage run history, database-enforced snapshot immutability, large/quoted CSV input, import/rejection read APIs and external-source failures are not covered by this gate. Unit/integration tests prove audit rollback using an injected persistence failure. Runtime tests verify successful persisted reviews and denied writes without injecting database faults.

Update the closest focused test, scenario, contract and design document when behavior changes. See [runbook](runbook.md) for commands and configuration.
