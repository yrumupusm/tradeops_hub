# ADR 0003: Ingest Laws from Versioned Local Sources

[한국어](../../ko/adr/0003-versioned-local-source-ingestion.md) | [English](../../en/adr/0003-versioned-local-source-ingestion.md)

**Status:** Accepted

## Context

Legal source texts change over time. Even with citations, results are difficult to reproduce or review without knowing which source file and revision supplied the content.

## Decision

Use a local `legalize-kr` Git working tree as the legal source and store the following during ingestion:

- `snapshotVersion`
- `indexedAt`
- `sourcePath`
- `sourceVersion` (source Git commit)

Administrative synchronization safely updates the source before ingestion. If synchronization or ingestion fails, return a failure without deleting the existing index.

## Consequences

- Answer citations can be traced to a specific source file and commit.
- Ingestion depends on the source repository state, so source paths and Git permissions must be managed in operational environments.
- Source-format changes, Git conflicts, and synchronization failures must be explicitly observed and handled.
