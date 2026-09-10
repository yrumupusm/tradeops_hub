# ADR 0002: Validate Evidence and Citations Before Answering

[한국어](../../ko/adr/0002-evidence-and-citation-gate.md) | [English](../../en/adr/0002-evidence-and-citation-gate.md)

**Status:** Accepted

## Context

Even with retrieval results, an answer may fail to cite actual articles or make stronger legal conclusions than its evidence supports. Such failures may be more dangerous to users than an empty answer.

## Decision

Answers must pass `EvidenceValidatorAgent` and `CriticAgent` checks.

- `status=OK` requires at least one `citedArticles` item.
- `LOW_CONFIDENCE` with retrieved or hydrated evidence also requires at least one `citedArticles` item.
- Ungrounded law names, article numbers, and legal conclusions are not allowed.
- Missing citations or failed quality checks return safe error codes such as `missing_citation` and `response_quality_failed`.

## Consequences

- Successful responses have an explicit minimum quality standard.
- Answer generation paths cannot bypass the critic stage.
- Provider, retrieval, and parsing failures must be handled explicitly, not hidden as empty results.
