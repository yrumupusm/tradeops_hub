# Operations guide

[한국어](../ko/runbook.md) · [English](runbook.md)

## Configuration and startup

Java 17, Maven, Node.js 22 or later and Docker are required.

1. Copy root `.env.example` to `.env` and configure the DB password and initial owner password.
2. Run `docker compose up -d` to start PostgreSQL.
3. Run `powershell -File scripts/start-api-local.ps1 -MavenPath <mvn.cmd path>` to start the API.
4. In another terminal, run `npm ci` and `npm run dev` in `frontend`. The default API URL is `http://127.0.0.1:8081`; set the web process `API_PROXY_TARGET` if it differs.
5. Open `http://localhost:3000`, sign in with the configured owner account and perform the first collection in **우려거래자 수집 관리**.

In a separate terminal, enter `services/law-search`, prepare its service-specific `.env` from `.env.example`, then run `powershell -File scripts/server.ps1 start -MavenPath <mvn.cmd path>`. Follow the [law runbook](../../services/law-search/docs/en/runbook.md) for PostgreSQL, Qdrant and real provider settings. Set Hub API `LAW_RAG_BASE_URL` to the law server (local default `http://127.0.0.1:8080`).

Preserve existing DB/vector collections without reingestion or reembedding during repository migration. The law Compose project name is pinned. Hub DB defaults to port 5433 and law DB to 5432; explicit environment settings take precedence.

The initial owner password only applies on first creation; environment changes do not overwrite an existing DB account password. Use HTTPS and `SESSION_COOKIE_SECURE=true` in deployment. Persist original files and back them up alongside PostgreSQL.

Only the local API startup script reads root `.env`. Pass frontend `API_PROXY_TARGET` and deployment variables to their respective processes. See [.env.example](../../.env.example).

Bootstrap the owner with `OWNER_USERNAME` and `OWNER_INITIAL_PASSWORD`; an existing password is not overwritten. There is no role editor. JWT is no longer used and existing installations must log in again. Preserve Flyway V1–V4 and apply V5–V8. The PostgreSQL account needs permission to create pg_trgm.

The default original-file path is relative to the API process. Set `BIS_STORAGE_PATH` to an absolute persistent volume in deployment. Serve the public web over HTTPS with `SESSION_COOKIE_SECURE=true`. Restrict API/database network access to required networks.

## Initial collection

After login, use **우려거래자 수집 관리 → 전체 수집**. Check DPL and EL completion separately, row counts and last success. Use name/source/country filters in **우려거래자 검색**, inspect original details and download originals. CSV contains all matching results from the displayed source versions.

## Scheduling

Default: Monday 09:00, Asia/Seoul. The next reservation is persisted. Initial setup reserves next Monday, so collect manually when data is needed immediately. Restart catches up one missed reservation. Interrupted runs end with PROCESS_INTERRUPTED and preserve current data.

Only one API instance is supported per database. Disable scheduling with `BIS_SCHEDULE_ENABLED=false`.

## Troubleshooting

| State / code | Response |
| --- | --- |
| DOWNLOAD_LINK_MISSING / AMBIGUOUS | Inspect official page sections/links; update and test discovery rules. |
| SOURCE_URL_NOT_ALLOWED | Verify official ownership before narrowly changing configuration; do not broadly allow arbitrary hosts. |
| SCHEMA_CHANGED / CSV_INVALID | Inspect original columns/format and update parser contracts and fictional regression fixtures together. |
| ROW_VALIDATION_FAILED | Compare issue row numbers with originals; never hide errors to publish. |
| ROW_COUNT_DROP / HELD | Inspect added/removed rows against the prior version; the owner approves a verified legitimate decline. |
| SOURCE_RETRY_LATER / TEMPORARILY_UNAVAILABLE | Retry manually later; prior data remains searchable. |
| COLLECTION_STORAGE_FAILED | Check permissions, disk space and database state; back up files and DB together. |
| CSRF_INVALID | Authenticate again from login; do not automatically replay expired requests. |

Validated official download URL changes are accepted automatically and recorded as source alerts and previous/new run URLs. If the guidance page moves outside official domains, an operator must verify and change configuration.

## Accounts and audit

The owner creates accounts with temporary passwords; users change them after first login. Deactivation, reset and password changes revoke all sessions. The owner changes their own password via My Account. Filter audit by operation, actor and period. Search terms remain only in personal history until the user deletes them.

## Backups and verification

Back up DB and originals together. Test restores using a separate database/directory. There is no automatic original-file deletion. Never commit `storage/`, `artifacts/`, `.env` or builds, and never use public BIS records as repository fixtures.

Distinguish server-free, PostgreSQL and runtime/browser checks in the [verification guide](evaluation-harness.md). Evidence contains only status, counts and safe codes, never original names or session information.


Law search uses a separate RAG service. See [law integration](law-integration.md) for questions, citations, history/diff, authorization and data preservation.

## Monorepo verification

From the root, run `powershell -File scripts/verify-monorepo.ps1 -MavenPath <mvn.cmd path>`. This runs Hub checks, law JavaScript syntax checks and the full law Maven suite with temporary H2/mock configuration, without real DB or model use. `scripts/verify-local.ps1` runs Hub unit tests, verification-tool tests and frontend type checks without a server. Follow the [verification guide](evaluation-harness.md) for actual PostgreSQL/runtime checks.
