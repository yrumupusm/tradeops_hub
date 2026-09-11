# Scenario Test Guide

[한국어](../ko/scenario-test-guide.md) | [English](../en/scenario-test-guide.md)

This document defines manual scenario testing after connecting OpenRouter.

## Prerequisites

- `OPENROUTER_API_KEY` is set in `.env`.
- To verify Qdrant or PostgreSQL integration, first start local infrastructure with `scripts/infra.ps1 up`.
- Configure `.env`:

```properties
LLM_PROVIDER=openrouter
EMBEDDING_PROVIDER=openrouter
RERANKER_PROVIDER=mock
VECTOR_PROVIDER=inmemory
```

- The server is running at `http://localhost:8080`.

## Quick Checks

Check `.env` before starting the server:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\preflight.ps1
```

To strictly validate external-provider configuration:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\preflight.ps1 -Strict
```

`preflight.ps1` checks required OpenRouter/Cohere/Qdrant configuration and audit-log safety flags without printing API key values.
It also checks numeric settings such as `SERVER_PORT`, `LLM_TEMPERATURE`, and `RERANKER_TOP_K`.

Check the UI/API contracts of an already-running server together:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-runtime.ps1
```

Unless `-BaseUrl` is supplied, `verify-runtime.ps1` and `run-scenarios.ps1` derive the server URL using the environment's `SERVER_PORT`, then `.env`'s `SERVER_PORT`, then `8080`.

Default checks:

- Korean wording and forbidden copy on `/` and `/admin.html`
- `/healthz` and `/api/v1/health` status
- Index status from `/api/admin/status` and `/api/v1/admin/status`
- Default law list from `/api/laws`
- `/api/ask` camelCase response contract
- `/api/v1/ask` snake_case response contract
- No camelCase fields mixed into `/api/v1` responses

Verification outputs:

```text
target/runtime-evidence/healthz.json
target/runtime-evidence/v1-health.json
target/runtime-evidence/admin-status.json
target/runtime-evidence/v1-admin-status.json
target/runtime-evidence/ask-response.json
target/runtime-evidence/v1-ask-response.json
target/runtime-evidence/runtime-summary.json
```

`runtime-summary.json` summarizes the snapshot, index status, ask/v1 ask requestIds, citation counts, and whether provider/reindex options were used.

Provider smoke test:

```powershell
curl.exe -s -X POST http://localhost:8080/api/admin/provider-smoke-test
```

The admin screen provides the same check through `관리 > Provider 점검 > 연결 점검`.

Check:

- `llmProvider=openrouter`
- `embeddingProvider=openrouter`
- `rerankerProvider=mock` or `cohere`
- `llmStatus=ok`
- `embeddingStatus=ok`
- `rerankerStatus=ok`
- `embeddingDimensions=1024`
- `rerankedCount=1`

If some providers fail, the smoke API does not fail the entire request. It returns the failed provider's `failed` status and a safe error code.

To include providers in runtime verification:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-runtime.ps1 -IncludeProviderSmoke
```

This option actually calls the configured LLM, embedding, and reranker APIs at least once.

To also check the `202 Accepted` contract of `POST /api/v1/admin/reindex`:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-runtime.ps1 -IncludeReindexContract
```

Use this only with `ADMIN_REINDEX_ENABLED=true`. It may perform actual reindexing and embedding calls, so it is excluded from default verification.

## Run Scenarios

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\run-scenarios.ps1
```

Input data:

```text
harness/scenario-requests.json
```

Outputs:

```text
target/scenario-responses/{scenario-id}.json
target/scenario-responses/scenario-summary.json
```

`scenario-summary.json` summarizes each scenario's status, citation count, vector hits, requestId, search-log linkage, trace-order verification, and forbidden-copy checks. Full questions are not stored in the summary.

After runtime and scenario verification complete, generate the submission evidence report:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\export-evidence-report.ps1
```

The result is saved to `target/evidence-report.md`. Based on `runtime-summary.json` and `scenario-summary.json`, it reports smoke results, scenario pass/fail, requestId linkage, search-log checks, and agent-trace checks without API keys or full questions.

Checks:

- Technical-data sharing questions return `OK` with citations.
- Strategic-goods export questions return `OK` with citations.
- Korean technical-data questions produce normalized queries and cited articles.
- Questions with insufficient information return `INSUFFICIENT_INFO`.

## Manual Swagger Testing

```text
http://localhost:8080/swagger-ui/index.html
```

`POST /api/ask` request:

```json
{
  "question": "해외 업체에 기술자료를 제공해도 되나요?",
  "asOf": null
}
```

Check:

- `status` is `OK`.
- `citedArticles` is not empty.
- `reasoning` is grounded in cited articles.
- `diagnostics.requestId` links the response, search log, and agent trace; `diagnostics.generatedQueries` exposes the retrieval queries.

Inspect administrative APIs:

```text
http://localhost:8080/api/admin/search-logs
http://localhost:8080/api/admin/agent-traces
http://localhost:8080/api/admin/ingestion-runs
```

## Runtime Audit Checks

For each `POST /api/ask` response, `scripts/run-scenarios.ps1` also uses `diagnostics.requestId` to check that:

- A search log with the same `requestId` exists in `GET /api/admin/search-logs`.
- Traces with the same `requestId` are returned by `GET /api/admin/agent-traces?requestId=...`.
- Trace stages are stored in the order `QueryAnalyzerAgent -> RetrievalAgent -> EvidenceValidatorAgent -> AnswerWriterAndCritic`.

Confirm that search logs, agent traces, and reindex run histories are created.

`scripts/verify-runtime.ps1` is a shorter smoke test than the scenario harness above. Without restarting the running server, it checks that:

- Root/admin static screens respond successfully.
- Liveness/readiness respond successfully.
- The admin index status is `healthy`.
- `/api/ask` retains the camelCase contract used by the Spring UI.
- `/api/v1/ask` and `/api/v1/admin/status` retain the original-compatible snake_case contract.
- Responses do not expose demonstration-only wording such as demo, sample, or mini project.
