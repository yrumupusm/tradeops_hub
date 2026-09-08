# Watchlist Update Workflow Design

## Source and identity

The current `FictionalWatchlistSource` reads one bundled XML or CSV version and returns a parsed payload and SHA-256 checksum. Supported versions are `2026-01-A` and `2026-02-B`. The source is a concrete component, not yet a provider interface.

Fields and validation rules are defined in the [watchlist data dictionary](../data/fixtures/watchlist-data-dictionary.md). All committed records use `FICTIONAL_WATCHLIST_PROVIDER` and `dataOrigin=FICTIONAL`. Stable identity is `provider + externalId`.

## Version comparison

| Condition | Result | Latest-view effect |
| --- | --- | --- |
| Only in candidate | ADDED | Present in new snapshot |
| In both, canonical hash differs | CHANGED | New fields in new snapshot |
| Only in previous | REMOVED | Absent from new snapshot |
| In both, canonical hash matches | UNCHANGED | Same fields in new snapshot |

The comparator uses a fixed allowlist of business fields, normalized whitespace and sorted aliases. Only ADDED, CHANGED and REMOVED create change rows. UNCHANGED contributes to totals. Current entities and changes are queried from the provider's highest snapshot ID, with no separate current-view table.

## Run and transaction behavior

A new run loads and validates the input, checks for an existing provider/version/checksum, compares to the preceding snapshot and saves all new records inside the service transaction.

The same checksum key creates a new completed run referencing the existing snapshot without duplicate snapshot or change rows. XML and CSV with equivalent records can have different payload checksums. Re-running an older existing snapshot does not make it current again.

Payload exceptions produce a failed run with a bounded code and no new snapshot. Unexpected database or serialization failures can roll back the run record as well; durable failure recording for those cases remains work to do.

## Access and review

All authenticated roles can read current entities, changes and runs. ADMIN and OPERATOR can trigger updates. Screening records are separate human-entered dispositions. Their role restriction and transactional audit write are unfinished; see the [API contract](api-contract.md).

## Known boundaries

- Completed snapshots are treated as immutable by application convention; DB-level enforcement is pending.
- Unique constraints prevent duplicate checksum keys but do not serialize concurrent updates.
- Only the latest snapshot is exposed by the entity/change list APIs.
- Source configuration, a general provider interface and scheduled execution are not implemented.
- No external source is accessed. Any future adapter requires separate configuration, verified access conditions and safe failure handling.

[C4 model](architecture/c4.md) · [Snapshot ADR](adr/0002-versioned-snapshots.md) · [arc42](arc42.md)
