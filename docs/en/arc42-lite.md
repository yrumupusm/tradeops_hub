# arc42 Lite

[한국어](../ko/arc42-lite.md) | [English](../en/arc42-lite.md)

This document selects the relevant parts of the arc42 template to help even novice developers quickly understand the system's purpose, boundaries, structure, and quality criteria.

## 1. Goals and Quality Requirements

The law research assistant finds articles relevant to questions within a selected set of Korean laws and provides research assistance with verifiable citations.

The most important quality criteria are:

- Do not invent ungrounded law names, article numbers, or legal conclusions.
- `OK` responses must contain cited articles.
- When evidence is insufficient, return `INSUFFICIENT_INFO` or follow-up questions rather than fabricate an answer.
- Answer evidence is traceable through the source file path, indexing time, snapshot version, and source commit.
- Request logs and agent traces are linked by the same `requestId`.

## 2. Constraints and Boundaries

- The service does not provide legal advice or legal determinations.
- The supported scope is eight laws selected from the `legalize-kr` source.
- Default data is a small development/demo seed; using actual articles requires ingesting the full articles from the source repository.
- External AI, embeddings, vector search, and reranking are behind provider interfaces and are optionally selected through configuration.
- API requests reject unknown JSON fields, blank questions, questions exceeding 4,000 characters, and invalid `asOf` dates with `400 Bad Request`.

## 3. Solution Strategy

Questions pass through a fixed pipeline.

```text
AskController
-> AskOrchestratorService
-> QueryAnalyzerAgent
-> RetrievalAgent
-> EvidenceValidatorAgent
-> AnswerWriterAgent
-> CriticAgent
-> SearchLogAgent
```

Retrieved results must pass evidence validation before reaching answer writing. The critic stage checks missing citations, forbidden copy, and response quality issues. Results that fail these checks are not returned as successful answers.

## 4. Runtime Structure

The system consists of the Spring Boot API, research pipeline, relational database, optional Qdrant vector store, and local legal sources. See the [C4 architecture overview](architecture/c4.md) for details.

Ingestion records the file path and Git commit, allowing each citation to be traced to the source file and revision used.

## 5. Deployment and Operations

- H2 supports quick development startup.
- Environments requiring persistent data can run PostgreSQL and optional Qdrant with Docker Compose.
- `/api/admin/status` shows law, article, and vector counts and the last indexing time.
- `/api/admin/sync-source` synchronizes and ingests the source repository. Synchronization failures are returned explicitly without deleting the previous index.
- `/api/admin/reindex` refreshes the index of already ingested articles.

See the [runbook](runbook.md) for startup and inspection procedures.

## 6. Risks and Mitigations

| Risk | Mitigation |
| --- | --- |
| Ungrounded answers | Require citations, evidence validation, and critic checks |
| Results cannot be reproduced after source changes | Record snapshot version, indexing time, file path, and source commit |
| AI provider failures hidden as empty results | Return safe error codes or an explicit insufficient-information state |
| Poor retrieval quality from too little data | Ingest full source articles and run fixed-question regressions |
| Requests cannot be correlated in audit logs | Share one `requestId` across responses, search logs, and traces |

## 7. Key Decisions

[Architecture Decision Records (ADR)](adr/README.md) capture important choices and their rationale. The initial set retains three decisions:

1. Limit scope to citation-based research assistance for selected laws.
2. Validate evidence and citations before answering.
3. Record local legal source versions in ingestion results and answer evidence.
