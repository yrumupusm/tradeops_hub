# API contract

[한국어](../ko/api-contract.md) · [English](api-contract.md)

Base: `/api/v1`. The browser uses the Next.js same-origin proxy. JSON DTOs use camelCase; database-backed row objects explicitly use snake_case fields (below). Unknown request fields fail with 400. Errors contain a bounded `code`, safe `message`, `correlationId` and optionally `timestamp`. Every API response carries `X-Correlation-Id`.

## Authentication

1. GET `/auth/csrf` → `{token, headerName}`, session cookie.
2. POST `/auth/login` with `{username,password}` and `X-CSRF-TOKEN` → `{id,username,owner,mustChangePassword}`; rotates session ID.
3. Send the HttpOnly `SESSION` cookie for subsequent calls. All POST/PATCH/DELETE requests, including login/logout/search/export, require CSRF.
4. GET `/auth/me` → current user. POST `/auth/logout` invalidates the session.

Idle session timeout defaults to 30 minutes. Five failed attempts for a username/IP in ten minutes cause 429. Missing authentication: 401; missing/stale CSRF: 403 `CSRF_INVALID`; forbidden operation: 403 `ACCESS_DENIED`. Forced-change accounts can only access me/csrf/logout/password. Credentials never return in responses.

## Accounts and audit

| Method / route | Input / result | Access |
| --- | --- | --- |
| GET /users | Array: id, username, enabled, must_change_password, created_at | Owner |
| POST /users | {username,password}; temporary password, empty 200 | Owner |
| PATCH /users/{id} | {enabled:boolean}; required field, empty 200 | Owner |
| POST /users/{id}/reset-password | {password}; revoke all sessions, force change | Owner |
| POST /account/password | {currentPassword,newPassword}; revoke all sessions | Self |
| GET /audit-events | actor,event,from,to,page,size; paginated events | Owner |

Owner is configured, protected from deactivate/reset endpoints. Passwords: at least 8 characters, at most 72 UTF-8 bytes; new password must differ from current. Username is 3–120 ASCII letters/digits or `@._+-`. Audit dates are ISO instants; from inclusive/to exclusive. Audit rows include event_type, actor_username, entity_type, entity_id, correlation_id, occurred_at and JSON text detail. Raw search terms and credentials are omitted.

## Collection

| Method / route | Contract |
| --- | --- |
| GET /watchlist/sources | {items,settings}; source code/current_snapshot_id/data_updated_at/active_run_id/URLs/last_checked/last_success/next_scheduled/alert_code; settings guideUrl, scheduleEnabled, cron, zone |
| POST /watchlist/runs | {source:"DPL"\|"EL"\|"ALL"} → 202 {status:"RUNNING",runIds:{DPL?:id,EL?:id}} |
| GET /watchlist/runs | page,size → {items,totalElements,page,size} |
| GET /watchlist/runs/{id} | {run,issues}; run details omit server file_path |
| GET /watchlist/runs/{id}/changes | page,size → array of added/removed row summaries |
| POST /watchlist/runs/{id}/approve | Owner only; no body; held run → current |
| GET /watchlist/files | page,size → paginated runs having files |
| GET /watchlist/files/{runId}/download | Original file attachment; auditable access |

`data_updated_at` is the current snapshot creation timestamp (null before first publication), displayed as 최근 데이터 변경일. Rechecking an unchanged file reuses the snapshot and leaves this timestamp unchanged; a new snapshot, including parser-upgrade reprocessing, changes it. This is a local data-version timestamp, not the publisher's Last-Modified date.

States: RUNNING, COMPLETED, FAILED, HELD. Stages: DISCOVERING, DOWNLOADING, VALIDATING, SAVING, FINISHED. Run fields include source_code, trigger_type, actor, correlation_id, timestamps, guide/download/final/previous URLs, page/file hash, file_size, row counts, snapshot_id, previous_snapshot_id, parser_version, idempotent and safe error_code. Issues have row_number/code/severity. Source rows remain recoverable through the original file even if parsing fails.

No source contains a fixed 10,000-row cap. Source HTTP maximum is 32 MiB, timeout 30 s, connect timeout 10 s, redirect cap 4, attempts cap 3. Long Retry-After yields a safe failure rather than blocking the worker indefinitely. Failed or ambiguous downloads preserve the current snapshot. Declines >30% are HELD. Only a held version not superseded by a newer current version may be approved.

## Search, detail and CSV

GET `/search-history/recent` returns up to five `{id,q}` entries from the authenticated actor's history, newest first, excluding empty terms and grouping terms case-insensitively after trimming. DELETE `/search-history/recent/{id}` removes all of that actor's history entries with the selected term (including different filters/versions); another actor's ID returns 404. Deletion requires CSRF and records an audit event without query text. The search form renders these as quiet text actions with individual delete buttons; clicking a term runs a new search with current form filters and current source snapshots. Existing history replay still uses its pinned snapshots.

POST `/watchlist/search` and POST `/watchlist/exports` accept:

```json
{"q":"FICTIONAL BEACON","mode":"HYBRID","source":"","country":"","sort":"score","page":0,"size":20,"snapshots":{"DPL":1,"EL":2},"saveHistory":true}
```

All fields optional. Defaults: empty query/source/country; HYBRID; score sort; page 0; size 20; history off. Modes BASIC, SIMILAR, HYBRID; sorts score,name,country. Query max 250 characters. Country empty or two-letter code. General page bounds: 0–100000; size 1–100. Query source empty/DPL/EL.

Omitting snapshots resolves current versions. An explicit empty object pins an empty result. Supplied IDs must belong to the indicated source. A response returns items,totalElements,page,size,snapshots,elapsedMs,variants. Items include id,snapshot_id,source_code,row_number,name,country,address,matched_name,priority,score,duplicate_count. Priority: 0 name equality, 1 alias equality, 2 substring, 3 similar candidate (trigram threshold 0.42). Normalized name, compact spelling and sorted tokens are compared. Exact duplicate original rows are grouped; different rows are retained separately. CSV exports **all matching rows**, ignoring page/size while keeping filters/versions; UTF-8 BOM, quoted fields, formula protection.

CSV columns use Korean labels: source followed by original name, alternate name, country, address, city, region, postal code, effective date, Federal Register notice and standard order; then DPL/EL-specific original fields (expiration/update/action, list/entity details, lifted/waived/expired date, licensing, vessel/address/alternate details and web link). Values come from stored original fields, including original date strings and country labels, with unavailable fields left blank. DPL expiration and EL lifted/waived/expired dates remain separate. Search match type, similarity, duplicate count and internal snapshot/row identifiers are omitted. Formula protection still applies to every exported value. The schema is fixed across source filters. Original-file downloads remain unchanged.

GET `/watchlist/records/{id}` returns the stored row, raw_json, aliases_json, dates_json, validation, source/run/snapshot linkage. GET `/watchlist/countries` returns available current country codes/counts. A detail record belongs to its immutable version; the UI retains the search back-link.

## Personal history

GET `/search-history?page=0&size=20` returns the actor's entries including query_json, total_results, elapsed_ms and created_at. DELETE `/search-history/{id}` deletes an owned entry (other users' IDs return 404); DELETE `/search-history` clears the actor's entries. No expiry. Owner access does not override history ownership. Explicit search saves history and audits actor/count/version IDs with the request correlation ID, excluding the query text. Paging/export do not create another history entry.

## Removed routes and health

Transaction/import/screening and role-management endpoints are absent. Historical tables are preserved. GET `/health` is public and contains status/service/checkedAt/correlationId. GET `/actuator/health` exposes no sensitive details.

Validation policy bis-csv-2: only structural and required-name failures create row issues. Optional country/date conversions never create warnings; raw values are preserved. Prior completed counts remain unchanged. Snapshot reuse requires matching parser version as well as file hash, so the next collection after a parser upgrade reprocesses unchanged bytes.


Law search uses a separate RAG service. See [law integration](law-integration.md) for questions, citations, history/diff, authorization and data preservation.
