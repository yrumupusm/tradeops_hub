# arc42 — TradeOps Hub

[한국어](../ko/arc42.md) · [English](arc42.md)

## 1. Introduction and goals

Collect public BIS DPL/EL files reliably, preserve provenance and offer a usable Korean search/download workspace. Quality priorities: correct source/version linkage, safe failure recovery, backend authorization, predictable search and clear operational state. No automatic compliance conclusion.

## 2. Constraints

Independent implementation; no company source or data. Actual public BIS runtime data is allowed; committed fixtures are fictional. Java 17/Spring Boot, Next.js/TypeScript, PostgreSQL with pg_trgm, filesystem originals. One API instance. Configuration uses environment variables.

## 3. Context and scope

Users collect/search/export; the fixed owner additionally manages accounts, audit and held-run approval. External dependency: official BIS guidance and media downloads. Transactions, role editing and screening decisions are excluded. See [scope](product-scope.md).

Law search adds the internal RAG process, law PostgreSQL, Qdrant, configured Markdown Git sources and external LLM/embedding/reranking providers. Authenticated users ask questions and inspect citations/history; only the owner performs law administration. Models assist research rather than issue final legal decisions.

## 4. Solution strategy

Persist server sessions instead of browser JWTs. Discover links each collection, validate host and schema, preserve raw rows and build separate normalized search names. Publish snapshots atomically. Pin source versions across pages and export. Use trigram similarity with explicit Korean expansion and display candidate type.

## 5. Building blocks

[C4](architecture/c4.md) shows containers/components. Controllers delegate to account, collection, audit and search services; repositories own SQL. Parser/discovery/HTTP components are independently testable. PostgreSQL owns sessions, audit and workflow state; original bytes reside on disk.

## 6. Runtime views

- Login: CSRF bootstrap → credentials check/rate limit → session ID rotation → persisted principal. Forced password change blocks workspace APIs.
- Collection: claim source → fetch guidance → discover and validate URL → conditional GET → store bytes/hash → parse and retain issues → compare rows → publish or hold. Any failure preserves current data.
- Search: resolve current source IDs once or accept pinned IDs → normalize query → exact/alias/partial/similar ranking → group exact duplicate rows → return page and IDs. Personal history is opt-in on explicit search.
- Export: repeat the query with fixed IDs, CSV-escape/formula-protect fields, audit row count and snapshot IDs.
- Restart: mark interrupted runs failed, clear active markers, execute one overdue schedule and advance next reservation.

- Law question: Hub session/CSRF and input checks → allowlisted RAG request → analysis/retrieval/reranking → evidence and answer validation → projected citations and request correlation. Logs link to traces in owner administration.

## 7. Deployment

See [runbook](runbook.md). Next.js forwards same-origin requests to the API. PostgreSQL and original storage need coordinated backups. API migrations are forward-only. Runtime evidence and real originals are ignored. HTTPS deployments require Secure cookies. The owner initial password is only a bootstrap value.

## 8. Cross-cutting concepts

- Provenance: source + run + file hash + row number + raw hash + occurrence + creation/validation.
- Identity: no stable BIS entity ID is assumed; name changes produce removed/added rows. No inferred company merge.
- Authorization: enabled account required on backend; fixed-owner checks for sensitive administration. 30-minute idle timeout; revoke all sessions on password change/reset/deactivation.
- Audit/privacy: correlations and bounded event metadata; no passwords, session values or search strings. History access is always scoped to the current user.
- Failure: safe codes, immutable successful versions, owner approval for >30% decline. File structure and required names define validation. Optional date/country conversion failures do not emit warnings; original values remain preserved.
- Resource limits: 32 MiB download, bounded HTTP timeouts/redirects/retries, 100-row query pages. Current export is buffered; imports are not capped at 10,000 rows.

## 9. Architecture decisions

[ADR index](adr/README.md), notably server sessions and BIS row-level provenance/link discovery.

## 10. Quality scenarios

Parser tests cover quoted CSV, multiline values, duplicate rows, format changes, ambiguous links and normalization. PostgreSQL tests cover atomic replacement, failure retention, held approval, concurrency/restart, version pinning, history isolation and performance. Runtime HTTP scenarios cover session/CSRF/account/audit contracts. Browser verification must check navigation, search/details/downloads, errors and mobile behavior; builds alone do not prove it.

Measured on a local isolated PostgreSQL fixture: 10,000 rows, 5 concurrent clients, 100 searches; p95 122 ms, CSV 398 ms on the latest run. These are machine-specific measurements, not production SLAs. Acceptance budgets: p95 <1 s and CSV <10 s. Results are reproducible through the integration test.

## 11. Risks and technical debt

BIS may change page structure, file schema or availability. Such changes are surfaced for review rather than guessed. Korean dictionary coverage is intentionally limited. No distributed worker lease, database-enforced immutable-row trigger, retention/purge policy or external alert delivery is provided. CSV buffering and offset pagination should be revisited for much larger sources. Legacy fixture-domain code remains solely for historical regression tests and has no public route.

## 12. Glossary

DPL: Denied Persons List. EL: Entity List. Snapshot: immutable imported source version. Held: validated snapshot awaiting approval before publication. Similar candidate: name similarity result, not entity identity or legal determination.


Law search uses a separate RAG service. See [law integration](law-integration.md) for questions, citations, history/diff, authorization and data preservation.
