# Project Guide

## Documentation Entry Points

- [Korean public documentation](docs/ko/README.md)
- [English implementation and verification references](docs/en/README.md)
- [Bilingual correspondence index](docs/README.md)
- [Documentation language and Korean UX policy](docs/en/documentation-language.md)

Read the English references for implementation; keep public README navigation in Korean. Both language versions describe the same design and must be updated in the same commit. Preserve constraints, examples, implementation status, and historical ADR context. Resolve discrepancies against code, tests, and the user's latest authorized requirements, not language preference.

## Core Principles

- The LLM does not invent laws.
- A `status=OK` answer must include at least one `citedArticles` item.
- A `LOW_CONFIDENCE` answer with retrieved or hydrated articles must also include at least one `citedArticles` item.
- When evidence is insufficient, do not draw a conclusion; return `INSUFFICIENT_INFO` and follow-up questions.
- Every answer includes `snapshotVersion`, `indexedAt`, and `sourcePath` in its basis data.
- Failure responses expose only safe stage-specific or quality-gate-specific codes in `errorMessage`, not raw provider exceptions.
- Ask requests accept `question`, `asOf`, and optional `researchAreas`; aliases `as_of` and `research_areas` are supported. Reject unknown fields, blank or over-4000-character questions, invalid dates, and invalid area values with 400.
- Do not use real company data, internal URLs, account information, or real API keys in code or documentation.

## Working Rules

- Controllers handle only HTTP requests and responses.
- Services separate question analysis, retrieval, answer synthesis, validation, and log storage.
- Keep LLM, embedding, and vector search behind interfaces.
- RetrievalAgent applies reranking after merging keyword/vector candidates.
- Manage provider URLs, model names, and API keys through environment variables.
- Manage legal sources through Git synchronization followed by Markdown ingestion.
- Tests verify status, citations, and retrieval diagnostics together.
- Some integration tests can inherit a real datasource from local `.env`. Before running the full suite, explicitly select temporary H2 and mock providers as documented in `docs/en/runbook.md`. Do not run bare `mvn test` or `verify-local.ps1` against a real-data configuration; automatic default isolation remains follow-up work.

## Prohibited Actions

- Returning `status=OK` with empty `citedArticles`
- Inventing law names or articles absent from the context
- Storing full questions or sensitive information verbatim in operational logs
- Hardcoding API keys
- Expanding excessively beyond the current legal research scope
- Forcibly overwriting local changes during Git synchronization

## Current Design Intent

- LLM provider: OpenRouter or mock
- Embedding provider: OpenRouter or mock
- Vector provider: in-memory by default; configurable Qdrant
- Reranker provider: Cohere or mock
- Database: H2 in-memory by default; configurable persistent PostgreSQL

This configuration implements a Spring Boot AX/RAG research workflow within a limited legal scope. Development defaults are distinct from a persistent runtime; preserve existing database and vector data. See the [runbook](docs/en/runbook.md) for configuration and operations.

## Runtime Audit And Scenario Rules

- Ask response, search log, and agent trace must share the same `requestId`.
- Agent trace must preserve the successful request order: `QueryAnalyzerAgent -> RetrievalAgent -> EvidenceValidatorAgent -> AnswerWriterAndCritic`.
- `scripts/run-scenarios.ps1` verifies status, citation count, expected citations, forbidden copy, requestId, search log correlation, and agent trace order.
- `GET /api/admin/agent-traces?requestId=...` and the admin page requestId filter are part of the audit workflow.
- Behavior changes that affect answers, trace order, logs, or runtime audit fields should update docs and tests together.

## Version Control Rules

- Treat Git history as part of the project evidence: each commit should describe one verified, meaningful unit of work.
- Check `git status --short` before editing, before committing, and before pushing.
- Commit behavior changes with the focused tests or harness updates that prove them.
- Keep documentation-only updates separate from runtime behavior changes unless the documentation is part of the same contract change.
- Do not stage secrets, `.env`, `target/`, local handoff notes, imported law-source repositories, or private portfolio/application drafts unless the user explicitly asks for them.
- Push after a tested milestone or when remote review/backup is needed; avoid saving many unrelated completed changes for one final push.
- Do not rewrite remote history or force-push for this application repository unless the user explicitly requests it.
