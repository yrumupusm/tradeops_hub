# ADR 0002: Source-version snapshots and checksum deduplication

[한국어](../../ko/adr/0002-versioned-snapshots.md) · [English](0002-versioned-snapshots.md)

> Historical decision record. See [ADR 0005](0005-bis-sessions-and-provenance.md) and [C4](../architecture/c4.md) for active BIS behavior. The web/API separation principle remains; historical implementation details are not the current contract.

Status: Accepted at recording time · Recorded: 2026-09-08

## Context

Overwriting the current list loses prior values and reasons for change. Repeated input must not duplicate change records.

## Alternatives

- Overwrite current tables: simple but loses versions.
- Store only events: saves space but requires reconstruction and validation.
- Compare display names: renames may appear as removal/addition of the same item.

## Decision

Separate runs and complete snapshots. Identify items by `provider + externalId` and compare hashes of normalized business fields. Store ADDED/CHANGED/REMOVED rows and include UNCHANGED in aggregates.

Enforce uniqueness on `provider + sourceVersion + payloadChecksum`. Repeated keys create only a new run and reuse the snapshot. Checksums derive from original strings, so equivalent XML and CSV may create different snapshots.

Read current rows from the highest snapshot ID. Application rules keep completed snapshot contents/counts immutable. Replaying an older snapshot does not make it current again.

## Consequences

Run outcomes and field differences are traceable and failed inputs cannot overwrite the list. Full snapshots require capacity and retention policies.

The unique key prevents duplicate keys but does not serialize comparison order for concurrent runs. DB triggers do not enforce immutability. Persistence failure may roll back the whole transaction, so not every DB failure is retained as a failed run. Concurrency, failure-history retention and DB immutability verification remain follow-up work at this decision's date.

## References

[WatchlistUpdateService](../../../backend/src/main/java/io/tradeops/watchlist/service/WatchlistUpdateService.java), [V2 schema](../../../backend/src/main/resources/db/migration/V2__create_watchlist_run_snapshot_tables.sql). The former WatchlistRunIntegrationTest was removed; consult Git history for that test.
