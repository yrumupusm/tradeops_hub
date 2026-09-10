# ADR 0001: Limit Scope to Citation-Based Research Support in Selected Legal Domains

[한국어](../../ko/adr/0001-scope-and-research-boundary.md) | [English](../../en/adr/0001-scope-and-research-boundary.md)

**Status:** Accepted

## Context

Plausible but ungrounded answers are especially risky for legal questions. Attempting to cover all laws and every legal-advice problem at once makes it difficult to control source freshness, jurisdiction, facts, and interpretive limits.

## Decision

Limit the service to finding articles in selected legal domains and providing research assistance with citations. Do not provide legal advice or final determinations on legality, permission, or compliance.

When facts or retrieved evidence are insufficient, return `INSUFFICIENT_INFO` or a follow-up question.

## Consequences

- The product boundary becomes clear and the answer contract becomes testable.
- Answers are starting points for user research, not substitutes for professional legal review.
- Adding a legal domain requires corresponding sources, ingestion, evaluation questions, and quality criteria.
