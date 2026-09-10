# Operations guide

[한국어](../ko/runbook.md) · [English](runbook.md)

## Configuration and startup

Follow the [README](../../README.md) to start PostgreSQL, API and web. Only the local API startup script reads the root `.env`. Pass frontend `API_PROXY_TARGET` and deployment variables to their respective processes. See [.env.example](../../.env.example).

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
