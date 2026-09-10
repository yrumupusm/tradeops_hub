# ADR 0003: Reproducible local sources and collection boundary

[한국어](../../ko/adr/0003-local-source-boundary.md) · [English](0003-local-source-boundary.md)

> Historical decision record. See [ADR 0005](0005-bis-sessions-and-provenance.md) and [C4](../architecture/c4.md) for active BIS behavior. The web/API separation principle remains; historical implementation details are not the current contract.

Status: Accepted at recording time · Recorded: 2026-09-08

## Context

Repeatable added/changed/removed and failure checks need fixed source contents/versions. External downloads couple functional verification to data changes and network failures.

## Alternatives

- Direct external collection connects real updates but requires access conditions, format-change and rate-limit handling.
- Ad hoc test data is quick but makes shared UI/API/comparison inputs difficult.

## Decision

Manage two seeded fictional provider versions as XML/CSV resources. Validate `dataOrigin=FICTIONAL`; FictionalWatchlistSource loads the requested version and supplies the parser. The data dictionary defines identifiers/fields.

External adapters are excluded at that time. Later adapters must verify access conditions and format, be enabled through configuration and record safe failed runs for download failures.

## Consequences

Identical changes are reproducible offline, but updates only reread repository resources and are not live external refreshes.

At that time the service depends directly on FictionalWatchlistSource. A shared source interface and scheduler are not implemented and should be separated for a new provider.

## References

[Source loader](../../../backend/src/main/java/io/tradeops/watchlist/service/FictionalWatchlistSource.java), [generator](../../../backend/src/main/java/io/tradeops/watchlist/fixture/FictionalWatchlistFixtureGenerator.java), [data dictionary](../../../data/fixtures/watchlist-data-dictionary.md).
