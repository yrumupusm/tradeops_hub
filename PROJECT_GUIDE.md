# TradeOps Hub Project Guide

TradeOps Hub collects the public BIS Denied Persons List (DPL) and Entity List (EL), retains source versions and supports name search and CSV downloads. The Korean console is an operational workspace. Matches and removals do not constitute legal or compliance decisions.

## Scope and boundaries

- Login, persistent server sessions, personal password changes, fixed-owner account administration and actor-attributed audit.
- Manual and weekly BIS collection, download-link rediscovery, original files, validated rows, immutable snapshots and run history.
- Normalized name/alias/similar-name search, private search history, pinned versions and CSV export.
- Transaction processing, screening decisions and editable roles are excluded. Historical migrations/tables remain for data preservation; their HTTP endpoints are removed.

The web calls a same-origin Next.js API proxy. Spring Boot owns authorization and transactions; services own business rules and repositories own SQL. PostgreSQL stores sessions, accounts, audit, runs, raw rows and search indexes. The API process runs one scheduler/worker instance. Local file storage holds original public files.

## Invariants

1. Each row retains its source, original file/run, row number, raw values, validation state and timestamp.
2. Public BIS files do not supply reliable universal entity IDs. Identity within a version is a raw-row hash plus duplicate occurrence; names never identify companies.
3. New snapshots become current atomically after validation. Failures and held runs preserve the prior current version.
4. A completed run's aggregate counts are immutable. Identical bytes reuse a snapshot. Changed bytes with identical parsed rows can create a new provenance version.
5. A decline greater than 30% is held for owner approval. Old held runs cannot overwrite a newer current version.
6. Source downloads must use HTTPS and approved official hosts, including redirects. Missing/ambiguous links fail visibly.
7. Searches, paging and exports carry the same source snapshot IDs. Search history belongs only to its actor, without automatic expiry.
8. Mutations are validated and audited with a correlation ID. Search audit does not contain raw queries; password/session values never enter audit.
9. Ordinary users share workflow permissions. Only the configured owner administers accounts, reads audit and approves held runs.
10. All committed fixtures are fictional and deterministic. Real public source files, verification evidence and local configuration remain ignored.

Public BIS URLs and public runtime data are explicitly authorized. Company code, company data, internal URLs/names and secrets must not be copied.

## Working agreement

Read this file and [tasks](tasks/todo.md) before behavioral edits. Update the checklist first for substantial work. Keep [API](docs/api-contract.md), [scope](docs/product-scope.md), [C4](docs/architecture/c4.md), [ADRs](docs/adr/README.md) and [arc42](docs/arc42.md) aligned. Run focused tests, then the applicable verification gate. Report unfinished checks accurately. Check Git status before/after; never stage ignored data, credentials or generated output.
