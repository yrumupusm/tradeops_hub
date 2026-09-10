# ADR 0004: Server authorization and request traceability

[한국어](../../ko/adr/0004-authorization-and-traceability.md) · [English](0004-authorization-and-traceability.md)

> Historical decision record. See [ADR 0005](0005-bis-sessions-and-provenance.md) and [C4](../architecture/c4.md) for active BIS behavior. The web/API separation principle remains; historical implementation details are not the current contract.

Status: Accepted at recording time · Recorded: 2026-09-08

## Context

Hidden UI buttons do not prevent direct API requests. Accountability requires connecting the authenticated actor/request to persisted results.

## Alternatives

- UI-only authorization cannot protect direct API calls.
- Server sessions simplify revocation but need session storage/lifecycle management.
- Logging complete originals helps investigation but spreads sensitive inputs.

## Decision

Spring Security validates Bearer JWT and backend roles. ADMIN/OPERATOR may update lists, upload transactions and record reviews; ADMIN alone accesses administration status. X-Correlation-Id accepts a bounded format and links run records/responses. Logs/errors contain identifiers and safe bounded codes rather than raw source content.

Reviews record actor, creation time, target ID, score, disposition and correlation ID. They are human-entered decisions, not automatic conclusions.

## Consequences and scope

Non-web API clients share the same access policy. Tokens remain valid until expiration; no refresh/revocation API exists at that time. The local account seeder is configuration-enabled and is not public deployment account management.

ScreeningReviewService stores reviews/audit in one transaction through ScreeningReviewRepository. Reader writes are rejected and failed audit persistence rolls back the review. CorrelationIdFilter runs before security filters so authentication/authorization errors have request IDs. List updates/imports store actors in runs but do not write audit_events separately; extending audit to every mutation remains future work at that time.

## References

[SecurityConfig](../../../backend/src/main/java/io/tradeops/auth/SecurityConfig.java), [CorrelationIdFilter](../../../backend/src/main/java/io/tradeops/web/CorrelationIdFilter.java), [tasks](../../../tasks/todo.md). The former ScreeningReviewController was removed; consult Git history for its implementation.
