# Verification harness

## Layers

- `scripts/verify-local.ps1 -MavenPath <mvn.cmd>`: server-free unit/parser tests, evidence-tool tests and TypeScript checks. PostgreSQL-only tests are explicitly skipped here.
- `BisPostgresIntegrationTest`: real PostgreSQL migrations, atomic publication, decline approval, retained failures, discovery pipeline/idempotency, source concurrency/restart, pinned/private search and performance.
- `scripts/verify-runtime.ps1`: started API/web, persisted sessions/CSRF, forced password changes, owner authorization, history isolation, CSV contract, audit redaction and Korean served HTML. Requires a fresh isolated application DB and owner credentials through `VERIFY_OWNER_USERNAME`/`VERIFY_LOCAL_PASSWORD`.
- `scripts/verify-final.ps1`: local gate, builds, temporary PostgreSQL, PostgreSQL tests, isolated API/web, runtime scenarios, evidence and persistence assertions. Cleans up only the resources it created; `-KeepRuntime` retains them only after success.
- Browser acceptance: actual login → search/filter/page/detail/back/CSV; source collection → run detail/file/version; accounts/forced change; errors and desktop/mobile navigation. HTTP HTML checks alone are not browser acceptance.

## PostgreSQL tests

Run only against a disposable database **named tradeops_fixtures**, owned by an isolated verification user. The test verifies the JDBC database name before clearing BIS tables. Set `TRADEOPS_TEST_PG=true`, `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`; then run `mvn -Dtest=BisPostgresIntegrationTest test` from backend. Never point it at an application database.

Fixtures are generated in the test from a deterministic sequence (seed identifier `BIS-FIXTURE-20260909`): fictional Northline Beacon and numbered Fictional Meridian records. Raw fixture files/evidence go to ignored artifacts. No network is used: the BIS HTTP client is mocked, while parsing, discovery, storage and PostgreSQL are real. The 10,000-row benchmark warms up, executes 100 requests through five threads, asserts p95 <1000 ms and CSV <10000 ms and writes aggregate timings only.

## Evidence

`harness/scenarios.json` fixes HTTP scenario IDs and assertions. Evidence contains status/check names, allowlisted request paths/status/correlation IDs, source/scenario hashes and timestamp. Cookies, credentials, request/response bodies and raw queries are excluded. The validator rejects missing/failed/skipped checks, stale output, unknown fields and changed source hashes. Evidence paths must remain under ignored artifacts.

Real BIS connectivity is a separate live acceptance check: run DPL/EL against their official guidance links and assert completed runs, original downloads and searchable snapshots. Exact public row counts change over time and are not fixed fixture assertions. Live data and browser captures of it must remain ignored.

## Latest delivery status

See [tasks](../tasks/todo.md) for which gates and browser checks have actually been executed for this revision. Do not reuse the earlier transaction/JWT suite's successful result for the BIS implementation.
