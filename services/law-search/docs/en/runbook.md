# Runbook

[한국어](../ko/runbook.md) | [English](../en/runbook.md)

The [README](../../README.md) introduces startup and the overall structure. This operational reference collects the environment, server management, and data-operation details from the previous README.

## Environment Configuration

Spring Boot reads the project-root `.env` as a configuration file. For a new environment, use variable names from [.env.example](../../.env.example) and preserve any existing `.env` and data.

| Setting | Purpose |
| --- | --- |
| `SERVER_PORT` | Application port; default 8080 |
| `SPRING_DATASOURCE_*` | DB URL, driver, username, and password |
| `SPRING_JPA_HIBERNATE_DDL_AUTO` | DB schema handling; never use `create-drop` for existing data |
| `POSTGRES_PORT`, `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | Compose PostgreSQL settings |
| `LLM_PROVIDER`, `EMBEDDING_PROVIDER` | `mock` or `openrouter` |
| `OPENROUTER_API_KEY` | Shared key; can be separated as `LLM_API_KEY` and `EMBEDDING_API_KEY` |
| `LLM_MODEL`, `EMBEDDING_MODEL`, `EMBEDDING_DIMENSIONS` | Models and embedding dimensions |
| `VECTOR_PROVIDER`, `VECTOR_COLLECTION` | `inmemory` or `qdrant`, and target collection |
| `QDRANT_BASE_URL`, `QDRANT_PORT`, `QDRANT_API_KEY` | Qdrant connection settings |
| `RERANKER_PROVIDER`, `RERANKER_MODEL`, `RERANKER_API_KEY`, `RERANKER_BASE_URL` | `mock` or `cohere` reranking settings |
| `HTTP_TIMEOUT_SECONDS` | External HTTP connection/read timeout; falls back to `LLM_TIMEOUT_SECONDS` |
| `EMBEDDING_MAX_CHARS`, `EMBEDDING_BATCH_SIZE`, `EMBEDDING_BATCH_DELAY_MILLIS` | Embedding input length, batch size, and call interval |
| `EMBEDDING_RATE_LIMIT_RETRY_DELAY_MILLIS`, `EMBEDDING_RATE_LIMIT_MAX_RETRIES` | Retry delay and count for rate limiting |
| `LAW_SOURCE_DIR`, `LAW_SOURCE_REPO_URL`, `LAW_SOURCE_BRANCH` | Source path, repository, and branch |
| `LAW_SOURCE_INCLUDE_DIRS`, `LAW_SOURCE_INCLUDE_FILES` | Scope of law directories and files to ingest |
| `REFERENCE_DATA_ENABLED` | Whether to load example articles for initial verification |
| `ADMIN_REINDEX_ENABLED` | Administrative reindex permission; default false |

For actual legal data, first check the PostgreSQL connection, persistent volumes, and Qdrant collection. `.env.example` is a starting example mixing PostgreSQL settings and mock providers; copying it alone does not produce a real-model search environment. Distinguish the application's H2/in-memory defaults from persistent operation.

The embedding model and dimensions must match the existing collection. Changing models is a data operation, not a simple server restart. Articles exceeding `EMBEDDING_MAX_CHARS` are truncated to their leading portion for embedding requests. Failed batches are retried individually, and final failures are recorded as partial failures.

## Start, Stop, and Restart

Run commands from the project root. If Maven is not on PATH, pass `-MavenPath <path to mvn.cmd>` to `server.ps1`.

```powershell
powershell -File scripts/preflight.ps1
powershell -File scripts/infra.ps1 up
powershell -File scripts/server.ps1 start
powershell -File scripts/server.ps1 status
```

Use `preflight.ps1 -Strict` to strictly check required external-provider configuration. It checks whether keys exist without printing their values. To restart already-created containers, `docker compose start postgres qdrant` is also available.

```powershell
powershell -File scripts/server.ps1 stop
powershell -File scripts/server.ps1 restart
```

`restart` terminates processes identified by the PID file and configured port, then starts the server. First verify that another project is not using that port. For simple inspection, use `status` and health APIs instead of restarting. Runtime logs are `target/bootrun.out.log` and `target/bootrun.err.log`.

Use `scripts/infra.ps1 status` to inspect only the database and vector store. `infra.ps1 down` stops/removes containers and is distinct from stopping the application. Do not add volume-deletion options; preserve data.

## Law Ingestion and Indexing

In a new environment, synchronize the source Git repository or ingest an existing Markdown path. Confirm the working path and ingestion scope before using the admin screen or [admin APIs](api-contract.md).

- `POST /api/admin/sync-source`: synchronize source Git; `ingestAfterSync=true` also ingests and indexes.
- `POST /api/admin/ingest-local`: ingest and index a new snapshot from the specified local Markdown.
- `POST /api/admin/reindex`: re-embed stored articles; requires `ADMIN_REINDEX_ENABLED=true`.
- `GET /api/admin/ingestion-runs`: inspect successful, partially failed, failed, and running executions.

Synchronization checks the previous commit and remote history and permits only fast-forward updates. It does not forcibly overwrite history conflicts or local changes. Ingested articles link to previous versions, while body hashes normalize BOM, line endings, and trailing whitespace. See the API contract for supported law types and Markdown formats.

With Qdrant, server startup restores the point count from the existing collection. Starting the server or editing screens does not require reindexing. Reindexing and ingestion may incur external embedding calls and costs. `/api/admin/reindex` returns synchronously; `/api/v1/admin/reindex` schedules background work and returns HTTP 202.

## Status and Error Inspection

| Path | Purpose |
| --- | --- |
| `/healthz` | Server liveness |
| `/api/health`, `/api/v1/health` | Database and index readiness |
| `/api/admin/status` | Total data counts, index state, recent failures, synchronization state |
| `/api/admin/search-logs` | Request IDs, statuses, safe question previews |
| `/api/admin/agent-traces?requestId={requestId}` | Processing stages for a specific request |
| `/swagger-ui/index.html` | API shape inspection and manual calls |

A health API can return HTTP 200 with a `degraded` body. An index state of `stale` means DB article and vector counts differ. Distinguish missing vectors from excess vectors; `unindexedArticlesCount=0` alone does not prove health. Do not automatically reindex without diagnosis.

`POST /api/admin/provider-smoke-test` actually calls the configured LLM, embedding, and reranking providers. Partial provider failure can still return HTTP 200, so inspect each result for `failed` and safe error codes. For the answer API, check body `status` together with `errorMessage`.

Correlate response `diagnostics.requestId`, SearchLog, and AgentTrace. `ask_complete` logs hashes, lengths, elapsed times, and retrieval metrics; `http_request` logs method, path, HTTP status, and elapsed time. Full questions, query strings, and request bodies are excluded from operational logs.

## Verification and Output Files

- Server-free checks: `scripts/verify-local.ps1`
- Live-server checks: `scripts/verify-runtime.ps1`
- Include provider connectivity: `scripts/verify-runtime.ps1 -IncludeProviderSmoke`
- Fixed-question scenarios: `scripts/run-scenarios.ps1`
- Readiness through evidence verification: `scripts/verify-final.ps1`

See [runtime readiness](runtime-readiness.md) for Qdrant/Cohere requirements and step-by-step commands, [evaluation harness](evaluation-harness.md) for question/citation/log criteria, and [scenario guide](scenario-test-guide.md) for scenario execution. `-IncludeReindexContract` can trigger actual reindexing and must be distinguished from ordinary inspection.

Results are written to `target/readiness-summary.json`, `target/runtime-evidence/`, `target/scenario-responses/`, and `target/evidence-report.md`. After running individual stages, aggregate reports with `scripts/export-evidence-report.ps1`. Do not commit verification output or ingested data.

### Isolate Tests from Real Databases

Some integration tests currently do not pin their datasource and can connect to the real database configured in local `.env`. Do not run bare `mvn test` or `verify-local.ps1` from a working directory configured for a real database. “Server-independent” does not mean “automatically isolated from the database.” Automatic isolation in the default test configuration remains follow-up work.

For the full Maven suite, explicitly select temporary H2 and mock providers as below. Replace `mvn.cmd` with its actual path if Maven is not on PATH. The `create-drop` setting applies only to the explicitly selected temporary H2 database; never omit the datasource overrides. Also inspect any existing process environment overrides before running.

```powershell
& mvn.cmd test `
  '-Dspring.config.import=optional:classpath:/no-local-env.properties' `
  '-Dspring.datasource.url=jdbc:h2:mem:docverification;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1' `
  '-Dspring.datasource.driver-class-name=org.h2.Driver' `
  '-Dspring.datasource.username=sa' `
  '-Dspring.datasource.password=' `
  '-Dspring.jpa.hibernate.ddl-auto=create-drop' `
  '-Dapp.llm.provider=mock' `
  '-Dapp.embedding.provider=mock' `
  '-Dapp.embedding.dimensions=8' `
  '-Dapp.vector.provider=inmemory' `
  '-Dapp.reranker.provider=mock' `
  '-Dapp.embedding.batch-delay-millis=0' `
  '-Dapp.reference-data.enabled=true'
```

Confirm a `jdbc:h2:mem:` connection and mock providers in the execution logs. Stop verification and inspect configuration if real PostgreSQL or external providers are selected. Do not make tests pass by deleting or reindexing real data.

## TradeOps Hub Connection

This repository owns the RAG API and independent screens. Hub owns the user-facing UI and authentication integration. Both repositories may default to the same PostgreSQL host port, so compare actual configuration and preserve separate databases and volumes. Follow the [integration handoff](tradeops-integration-handoff.md) for detailed procedures and authentication boundaries.
