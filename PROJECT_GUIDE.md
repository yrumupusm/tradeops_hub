# TradeOps Hub Project Guide

## Product statement

TradeOps Hub manages source updates, version comparisons, and trade-data review. An operator runs an update, the API validates and stores a snapshot, and the console exposes the latest list, changes, and execution history.

Describe the product through its functionality and setup. Do not claim production adoption or capabilities that are not implemented. The bundled source and transactions are fictional; the application is not connected to a company, customer, government feed, or real compliance list and never makes automated compliance decisions.

## Workflow

```text
source version -> validation -> snapshot + run record
               -> compare with previous snapshot
               -> current list + change review -> human follow-up
```

## Current scope

- Spring Boot authentication and administrator, operator, and viewer roles.
- Two deterministic source versions in XML and CSV.
- Manually triggered source runs, checksum-based idempotency, snapshots and comparison.
- Latest-list search APIs, change APIs, run history, and a web operations console.
- Transaction CSV import validation, duplicate handling, stored rejection metadata, transaction search and monthly aggregation APIs.
- A screening-review write API with actor, score, disposition, timestamp and correlation ID.

These capabilities have implementation gaps. The monthly aggregation currently uses H2-specific SQL; screening writes still need role restrictions and a service transaction. Import/rejection history and audit read APIs are not implemented. See [arc42](docs/arc42.md) and [tasks](tasks/todo.md) for the current status.

## Architecture boundaries

- The web console owns presentation and client state. The browser directly calls the REST API.
- The API owns authentication, authorization, validation, transactions and request tracing.
- The current concrete `FictionalWatchlistSource` reads classpath fixtures. A common source interface and scheduled execution remain future work.
- `WatchlistComparator` compares stable keys and canonical hashes.
- PostgreSQL stores runs, snapshots, changes, transactions, reviews and audit events. The current view reads the latest snapshot; it is not a separate mutable table.
- Controllers should delegate business rules to application services and data access to repositories. Existing direct JDBC paths are tracked as technical debt.

See [C4](docs/architecture/c4.md), [ADRs](docs/adr/README.md), and [API contract](docs/api-contract.md).

## Required invariants

- Source runs identify the source/version, format, actor, correlation ID, timestamps, status, row counts and safe failure code.
- Completed snapshots and their counts must remain immutable.
- Stable identity is `provider + externalId`, never a display name.
- Repeating the same provider/version/checksum must not create duplicate snapshots or changes.
- Changed records expose only allowlisted field differences.
- Removed means absent from a newer snapshot, not proof of a change in real-world restrictions.
- Current search must derive from completed snapshots.
- Imports retain source-file and run linkage, validation outcomes and creation timestamps.
- Review decisions must record the actor, timestamp, target, score and disposition.
- Authorization must be enforced by the backend. Every mutation must be validated and traceable.
- Failure responses and logs must not expose secrets or raw uploaded data.

These are acceptance requirements, not a claim that every failure path or concurrency case is already verified. Known gaps are recorded in the delivery checklist.

## API and data policy

Use `/api/v1`, camelCase JSON and bounded safe error codes. Successful mutations return `X-Correlation-Id`. Reject unknown JSON fields and invalid enum values. Document actual pagination and filter behavior per endpoint rather than assuming all lists have the same shape.

Only independently created fictional fixtures may be committed. Keep reproducible generators and field dictionaries with the data. Never commit real company data, internal names, source URLs, credentials, screenshots, or external commit history. Environment configuration belongs in environment variables; keep local `.env` ignored and document variable names in `.env.example`.

## Exclusions and future work

Live external collection and scheduled updates are not part of the current implementation. Before adding a source, verify its public access conditions and format, then isolate it behind configuration and safe failure handling. No legal or compliance conclusion may be inferred automatically from a match or removal.

## Completion standard

Before behavior changes, read this guide and `tasks/todo.md`; update the checklist for non-trivial work. Keep the nearest contract/design document and focused harness scenario in sync. Run relevant tests and the appropriate verification gate. Report commands and results, including verification not performed. Check Git status before and after changes and exclude generated output, secrets and real data from commits.
