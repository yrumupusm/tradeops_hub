# ADR 0001: Separate web/API and relational storage

[한국어](../../ko/adr/0001-web-api-relational-storage.md) · [English](0001-web-api-relational-storage.md)

> Historical decision record. See [ADR 0005](0005-bis-sessions-and-provenance.md) and [C4](../architecture/c4.md) for active BIS behavior. The web/API separation principle remains; historical implementation details are not the current contract.

Status: Accepted · Recorded: 2026-09-08

## Context

The list-update UI and transaction APIs share users, runs and snapshots. Reference integrity and write order must be controlled while allowing UI and business rules to change separately.

## Alternatives

- Next.js business logic reduces process count but couples web and business changes.
- Separate services allow independent deployment but add distributed transactions and operational costs at this scale.
- Document storage is flexible but requires separate run/snapshot/row integrity management.

## Decision

Next.js serves UI; one Spring Boot API handles authentication, validation and business processing. PostgreSQL persists data and Flyway manages schemas. At this decision's date, list persistence uses JPA; transaction import/query and review use JDBC. Transaction queries and review writes have separate repositories. H2 supports server-free integration tests.

## Consequences

Multiple list-update writes can share one DB transaction. At the time, the browser calls the API directly, requiring API address/CORS configuration. Moving direct JDBC from transaction import services into repositories remains work at that time.

H2 success alone does not prove PostgreSQL compatibility. Monthly totals use year/month EXTRACT; the final gate checks import/search/monthly totals on real PostgreSQL.

## References

[C4](../architecture/c4.md), [docker-compose.yml](../../../docker-compose.yml), [pom.xml](../../../backend/pom.xml), [migrations](../../../backend/src/main/resources/db/migration).
