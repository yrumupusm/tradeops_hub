# BIS collection and search design

[한국어](../ko/watchlist-update-design.md) · [English](watchlist-update-design.md)

The active design is [ADR 0005](adr/0005-bis-sessions-and-provenance.md), [API contract](api-contract.md) and [product scope](product-scope.md).

```text
weekly reservation or manual request
  -> lock source / create RUNNING record
  -> guidance page -> section link -> official HTTPS validation
  -> conditional download -> original bytes + hash
  -> schema and row validation -> raw snapshot + normalized search names
  -> compare raw hash + occurrence with current snapshot
  -> COMPLETED (publish atomically) or HELD (>30% decline)
  -> version-pinned search -> record detail / CSV

failure at any step -> FAILED + safe code; retain prior current version
```

DPL TXT is quoted comma-separated data. EL is CSV. Raw field values are preserved separately from searchable/parsed values. Column order and extra fields are accepted; missing required headers fail. Blank names prevent publication. Validation covers file structure and required names only. Optional date/country conversions do not emit issues; original values remain preserved. Repeated exact rows retain occurrence numbers.

A file byte hash allows snapshot reuse; row hashes ignore CSV formatting and field order but retain field values. A name change cannot be identified as the same company: differences show added/removed rows. Source URL history is independent from file/data changes.

Every download resolves the guidance link again. The current approved official host allowlist is `bis.gov,www.bis.gov,media.bis.gov`. A moved guidance page must redirect within that allowlist or be updated through configuration. If wording/markup makes discovery ambiguous, the run fails for operator review rather than guessing.
