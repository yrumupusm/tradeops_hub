# Architecture

[한국어](../ko/architecture.md) | [English](../en/architecture.md)

## Pipeline

```text
AskController
→ AskOrchestratorService
→ QueryAnalyzerAgent
→ RetrievalAgent
→ EvidenceValidatorAgent
→ AnswerWriterAgent
→ CriticAgent
→ SearchLogAgent
```

## Layers

- API: HTTP request/response handling
- Service: agent orchestration and business workflows
- Repository: JPA-based database access
- Domain: Law, Article, SnapshotVersion, SearchLog, AgentTrace
- DTO: API contracts

## Runtime Profiles

Default execution uses an H2 in-memory database and an in-memory vector store. For production-like local verification, start PostgreSQL and Qdrant with `docker-compose.yml` and switch the datasource and vector provider in `.env`.

```properties
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/law_research
SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.postgresql.Driver
SPRING_JPA_HIBERNATE_DDL_AUTO=update
VECTOR_PROVIDER=qdrant
QDRANT_BASE_URL=http://localhost:6333
```

## Current Retrieval Strategy

Retrieval merges JPA-based keyword search and embedding-based vector search, then orders candidates using lexical ranking, reciprocal rank fusion, and `RerankerClient`. `VectorSearchClient` offers default in-memory and Qdrant HTTP implementations.

Lexical ranking weights matches and token frequencies across law titles, article numbers, article titles, and bodies. Keyword and vector candidates receive reciprocal-rank scores based on their respective ranks so raw scores from one retrieval path do not dominate. For example, when a question includes both a law title and an article number, such as `관세법 제241조 수출신고`, scoring prioritizes that law/article over general export-control articles.

Question analysis expands spacing variants such as `대외 무역법` and practical aliases such as `산업기술보호법` and `외환거래법` into canonical law-title search terms. Scoring also compares compact strings with spaces and certain separators removed, improving recall for explicit law-title/article-number questions.

Date-based filtering uses the same policy for keyword search and vector-search postprocessing. Without `asOf`, search current articles (`effectiveTo is null`); with `asOf`, use only articles within `effectiveFrom <= asOf <= effectiveTo`. The end date is inclusive because local ingestion stores the previous article's `effectiveTo` as the day before the next effective date. Legacy articles with neither effective date are excluded from date-specific searches. The requested date is returned as `EffectiveBasisDto.asOf` so search conditions and display dates can be traced. Basis data also includes `snapshotVersion`, `indexedAt`, and `sourcePath`, identifying the snapshot and legal source used.

## Source Synchronization

Legal sources are ingested from a local Markdown directory. Administrative operations accept a Git repository URL, local destination, and branch and run the following flow.

```text
SourceSyncService
→ git clone or git fetch/checkout/pull --ff-only
→ record HEAD commit hash
→ update SyncState
→ LocalLawIngestionService
→ MarkdownLawParser
→ store SnapshotVersion/Law/Article
→ VectorIndexService
```

For an existing Git repository, synchronization verifies that the previously synchronized commit is an ancestor of the remote branch and permits only fast-forward pulls. Otherwise it records a history conflict and stops. A nonempty path that is not a Git repository is rejected. No forced reset overwrites local file changes.

`MarkdownLawParser` separates articles with headers in the `##### 제N조 (제목)` format. Repeated bare article numbers within one law file are normalized to unused `제N조의K` numbers to prevent duplicates. Explicit `제N조의K` numbers are preserved.
Legal types `법률`, `대통령령/시행령`, and `총리령/시행규칙/*부령` map to internal `LawType` values. Types outside the current scope are explicitly skipped.
`Article.contentHash` is a SHA-256 hash after normalizing BOM, CRLF/LF/CR, trailing whitespace, and consecutive blank lines. Repeated ingestion uses this hash to classify `amendmentKind` as `유지` or `개정`.

## Provider Abstraction

LLM, embedding, vector search, and reranking are separated behind these interfaces.

```text
ChatModelClient
EmbeddingClient
VectorSearchClient
RerankerClient
```

Implementations listed here:

- `MockChatModelClient`
- `MockEmbeddingClient`
- `InMemoryVectorSearchClient`
- `QdrantVectorSearchClient`
- `MockRerankerClient`
- `CohereRerankerClient`

The service layer depends only on interfaces, not provider implementations.

`RestClientTimeoutConfig` applies `HTTP_TIMEOUT_SECONDS` as the connection/read timeout for all `RestClient`-based external provider calls. When unset, it falls back to `LLM_TIMEOUT_SECONDS` so OpenRouter calls do not wait indefinitely.

Before each request, the OpenRouter embedding provider limits input to `EMBEDDING_MAX_CHARS`. It fails if returned vector dimensions differ from `EMBEDDING_DIMENSIONS`. A 200 response with empty `data` is also treated as an exception, not successful indexing.

`VectorIndexService` indexes articles in batches of `EMBEDDING_BATCH_SIZE`. If a batch fails, it retries that batch one article at a time so a single article or transient provider response does not halt all indexing. IDs that still fail are recorded as partial failures in ingestion history.

## Vector Provider

```properties
VECTOR_PROVIDER=inmemory
```

To use a production-style vector store, switch to Qdrant.

```properties
VECTOR_PROVIDER=qdrant
QDRANT_BASE_URL=http://localhost:6333
QDRANT_API_KEY=
QDRANT_DISTANCE=Cosine
```

The Qdrant provider creates the collection if absent, upserts article vectors as points, and searches through `/points/search`.

## Reranker Provider

```properties
RERANKER_PROVIDER=mock
```

To use an external reranker, switch to Cohere.

```properties
RERANKER_PROVIDER=cohere
RERANKER_BASE_URL=https://api.cohere.com
RERANKER_API_KEY=
RERANKER_MODEL=rerank-v3.5
```

The Cohere provider sends the query and candidate article text to `/v2/rerank`, then applies the response's `index` and `relevance_score` to the final `RetrievalHit` ordering and scores.

## Observability

Administrative APIs:

```text
GET /api/admin/status
GET /api/admin/search-logs
GET /api/admin/agent-traces
POST /api/admin/reindex
POST /api/admin/sync-source
POST /api/admin/ingest-local
GET /api/admin/ingestion-runs
```

`GET /api/admin/status` returns both total stored articles and vector-indexed articles. It reports `missing` when there is no completed snapshot, `stale` when the counts differ, and `healthy` when they match, allowing operators to identify ingestion/indexing discrepancies.

`SearchLog` stores requestId, question hash and length, question type, status, citation count, and latency instead of full questions. The display `questionPreview` replaces sensitive patterns and is truncated so it never contains the entire original question.

`AgentTrace` stores agent steps, input/output summaries, status, and latency by requestId. Ask response `diagnostics.requestId`, `SearchLog.requestId`, and `AgentTrace.requestId` are identical. The administrative API supports `GET /api/admin/agent-traces?requestId=...` to reconstruct a particular answer's processing stages. The admin screen filters traces by selecting a search-log requestId or entering it directly.

`AskAuditLogger` emits a JSON payload in the format `event=ask_complete payload={...}` at completion. It excludes the full question and retains only the first 12 characters of `questionHash`, question length, status, snapshot version, basis date, citation count, candidate count, latency, and retrieval statistics: the minimum information needed for operational analysis and incident reproduction.

`HttpRequestAuditFilter` emits `event=http_request payload={...}` after every HTTP request. The payload includes only method, path, status, and elapsedMs, not the query string or request body.

`SyncState` stores the last synchronized commit, synchronization time, and history-conflict detection time.
