# ADR 0005: BIS collection, server sessions and row provenance

Status: Accepted, 2026-09-09. Supersedes the live-source exclusion in ADR 0003, the role/JWT model in ADR 0004 and stable external-ID assumptions in ADR 0002 for the active BIS module.

## Context

The application now operates on actual public BIS DPL/EL files. Their download URLs and page wording may change. EL entity numbers may be empty; repeated names and exact duplicate rows exist. Users need private search history, revocable sessions and a complete collection-to-search workflow.

## Decision

Use Spring Session JDBC with HttpOnly cookies, CSRF, 30-minute idle timeout and session revocation. Configure one owner; ordinary users share workflow permissions. Password resets force a change.

Rediscover official download links each run. Accept new official HTTPS URLs only after schema validation. Record guidance/download/final URLs and content hashes. Keep original bytes in ignored persistent storage. Retry transient failures with bounds and preserve old current data on failure.

Identify rows within snapshots using raw hash and occurrence, preserving row numbers and duplicates. Use separate normalized names and pg_trgm indexes. Never merge companies by name. Publish whole source versions atomically; hold declines greater than 30% for owner approval. Keep complete run aggregate counts immutable.

Store the next weekly reservation in PostgreSQL. A single API scheduler catches up one missed reservation. Search pages, details, history replay and CSV use pinned snapshot IDs. History is visible only to its actor, including against owner access. Audit exports with counts/IDs, not search strings.

## Consequences

A changed name appears as removed/added; the system does not infer stable real-world identity. Source changes fail visibly. Single-instance scheduling must be replaced before horizontal API scaling. Snapshot and raw-file retention consumes storage. Public runtime data never becomes committed fixture data. Historical migrations/tables remain without exposed transaction/review APIs.
