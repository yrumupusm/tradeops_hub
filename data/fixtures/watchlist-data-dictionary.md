# Fictional Watchlist Data Dictionary

All records in this directory and generated resources are fictional. They are
deliberately small so a reviewer can see every result of a version comparison.
The generator is
`backend/src/main/java/io/tradeops/watchlist/fixture/FictionalWatchlistFixtureGenerator.java`.

## Transport formats

The application will normalize every supported input into the same watchlist
record before validation or comparison.

- XML is the fictional provider fixture format. It represents an external feed
  whose transport can evolve independently of the comparison model.
- CSV is the fictional manual-download/import format. It represents the
  manual source-update workflow.
- A future adapter may support another documented format, but it must produce
  the same canonical fields and validation outcomes.

## Canonical fields

| Field | XML location | CSV column | Required | Validation / comparison rule |
|---|---|---|---:|---|
| `provider` | `/watchlist/@provider` | `provider` | yes | Must equal `FICTIONAL_WATCHLIST_PROVIDER`; part of stable identity. |
| `version` | `/watchlist/@version` | `version` | yes | Fixture version in `YYYY-MM-X` form; fixed for a single payload. |
| `externalId` | `/watchlist/entity/@externalId` | `externalId` | yes | Unique within a provider snapshot; part of stable identity, never inferred from name. |
| `entityName` | `/watchlist/entity/entityName` | `entityName` | yes | Uppercase fictional display name; normalized whitespace contributes to canonical hash. |
| `aliases` | `/watchlist/entity/aliases/alias` | `aliases` | no | CSV aliases use `|`; values are trimmed, case-normalized, sorted, then hashed. |
| `countryCode` | `/watchlist/entity/@countryCode` | `countryCode` | no | Two uppercase letters; included in canonical hash. |
| `listingReason` | `/watchlist/entity/@listingReason` | `listingReason` | yes | `TRADE_REVIEW`, `DOCUMENT_REVIEW`, or `OWNERSHIP_REVIEW`; included in canonical hash. |
| `status` | `/watchlist/entity/@status` | `status` | yes | `ACTIVE` or `INACTIVE`; included in canonical hash. |
| `dataOrigin` | `/watchlist/@dataOrigin` | `dataOrigin` | yes | Must equal `FICTIONAL`; prevents accidental non-demo rows. |

## Expected difference from A to B

- `FCP-1005` is `ADDED`.
- `FCP-1002` is `CHANGED`: aliases, country code, and listing reason differ.
- `FCP-1003` is `REMOVED` because it is absent from version B.
- `FCP-1001` and `FCP-1004` are `UNCHANGED`.

A later source adapter validates the transport and canonical fields before it
creates a snapshot. A failed payload produces a safe failed run and leaves the
current view untouched.