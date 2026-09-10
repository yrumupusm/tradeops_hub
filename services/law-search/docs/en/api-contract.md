# API Contract

[한국어](../ko/api-contract.md) | [English](../en/api-contract.md)

All `/api/...` endpoints are also exposed under `/api/v1/...` for compatibility with the original `law_research_assistant` API shape. `/api/...` keeps the Spring UI camelCase response contract, while `/api/v1/...` returns original-style snake_case field names. Examples include `candidate_laws`, `cited_articles`, `follow_up_questions`, `effective_basis`, `retrieval_stats`, `law_id`, `article_id`, `index_status`, `recent_failures`, and `reindex_enabled`.

## POST /api/ask

Request:

```json
{
  "question": "해외 업체에 기술자료를 제공해도 되나요?",
  "asOf": null,
  "researchAreas": ["STRATEGIC_GOODS"]
}
```

When `asOf` is `null`, search currently effective articles. When a date is provided, retrieve only articles within `effectiveFrom <= asOf <= effectiveTo`. Legacy articles with neither effective date are excluded because they cannot be compared against the requested date.
`researchAreas` is optional and accepts multiple values from `STRATEGIC_GOODS` and `DEFENSE_MATERIALS`. These are retrieval priorities, not legal classifications. `question` must not be whitespace-only and must be no longer than 4000 characters. Strict JSON validation rejects unknown fields other than `question`, `asOf`, and `researchAreas`, invalid date formats, and invalid selections with `400 Bad Request` and `{"error":"invalid request"}`. The aliases `as_of` and `research_areas` are accepted for original `/api/v1` client compatibility.

Response:

```json
{
  "status": "OK",
  "interpretation": {
    "action": "제공",
    "object": "기술자료",
    "domainCandidates": ["수출통제", "기술이전"],
    "uncertainties": ["대상 품목 유형", "제공받는 기관", "목적지 국가", "기준 시점"],
    "generatedQueries": ["제공 기술자료", "수출통제 기술자료"],
    "questionType": "CONFIRMATORY"
  },
  "candidateLaws": [
    {
      "lawId": 1,
      "title": "대외무역법",
      "lawType": "LAW",
      "relevanceReason": "키워드 일치",
      "score": 0.86
    }
  ],
  "citedArticles": [
    {
      "articleId": 1,
      "lawTitle": "대외무역법",
      "articleNumber": "제19조",
      "articleTitle": "전략물자의 고시 및 수출허가",
      "effectiveFrom": "2026-01-01",
      "effectiveTo": null,
      "amendmentKind": "개정",
      "historicalEntries": []
    }
  ],
  "reasoning": "인용 조문을 기준으로 제공 대상, 목적지 국가, 최종 사용자를 추가 확인해야 합니다.",
  "followUpQuestions": ["제공받는 기관 또는 최종 사용자를 확인해 주세요."],
  "effectiveBasis": {
    "snapshotVersion": "law-domain-2026-001",
    "indexedAt": "2026-06-16T00:00:00",
    "sourcePath": "public-reference-data",
    "asOf": null
  },
  "confidence": 0.72,
  "diagnostics": {
    "requestId": "2f7bb8a8-2ed6-4f42-9420-4d090fdc91a8",
    "generatedQueries": ["제공 기술자료", "수출통제 기술자료"],
    "retrievalStats": {
      "retrieved": 2,
      "hydrated": 2,
      "cited": 2,
      "keywordHits": 2,
      "vectorHits": 5,
      "mergedHits": 5,
      "evidenceTopScoreBp": 940,
      "weakEvidence": 0
    },
    "latencyMs": {
      "analyze": 1,
      "retrieve": 12,
      "synthesize": 35
    }
  },
  "disclaimer": "이 답변은 인용 조문에 근거한 법령 조사 보조 결과이며, 최종 판단 전 담당 검토가 필요합니다.",
  "errorMessage": null
}
```

`effectiveBasis.sourcePath` identifies the public legal source or local ingestion path that produced the snapshot.
Snapshots ingested after Git synchronization also return the source commit in `effectiveBasis.sourceVersion`. This can be `null` for direct local-directory ingestion.
`effectiveBasis.asOf` is the user-requested basis date; `null` means currently effective articles.
`diagnostics.requestId` is the audit key linking the response, search log, and agent trace. `hydrated` is the number of articles passed as actual article bodies to answer synthesis. `weakEvidence=1` means articles were retrieved but relevance is low; such responses are limited to `LOW_CONFIDENCE` rather than `OK`. `evidenceTopScoreBp` represents the top retrieval score as an integer from 0 to 1000.
Failed responses return `status="FAILED"` and one of `no_snapshot`, `query_analysis_failed`, `retrieval_failed`, `evidence_validation_failed`, `answer_generation_failed`, `missing_citation`, or `response_quality_failed` in `errorMessage`. Raw provider exceptions and full user questions are not exposed in API responses.

## POST /api/v1/ask

Original-compatible ask endpoint. Request validation is the same as `/api/ask`, including `as_of` and `research_areas` alias support, but the response uses snake_case field names and lower-case enum values.

Response shape excerpt:

```json
{
  "status": "ok",
  "candidate_laws": [],
  "cited_articles": [{"article_id": 1, "law_title": "대외무역법", "article_number": "제19조"}],
  "follow_up_questions": [],
  "effective_basis": {
    "snapshot_version": "law-domain-2026-001",
    "indexed_at": "2026-06-16T00:00:00",
    "source_path": "public-reference-data",
    "as_of": null
  },
  "diagnostics": {
    "request_id": "2f7bb8a8-2ed6-4f42-9420-4d090fdc91a8",
    "generated_queries": [],
    "retrieval_stats": {},
    "latency_ms": {}
  },
  "error_message": null
}
```

## GET /api/admin/status

Returns index state, data counts, and the latest legal-source synchronization state.
`indexStatus` is `healthy`, `missing`, or `stale` based on completed snapshots and indexed article counts. `stale` means stored database article counts differ from vector-index counts and require inspection. Diagnose missing or excess vectors before taking action; it does not automatically require reindexing.

Response:

```json
{
  "lastSnapshotVersion": "law-domain-2026-001",
  "lastIndexedAt": "2026-06-16T00:00:00",
  "indexStatus": "healthy",
  "lawsCount": 8,
  "articlesCount": 21,
  "indexedArticlesCount": 21,
  "unindexedArticlesCount": 0,
  "searchLogCount": 12,
  "recentFailures": [],
  "reindexEnabled": false,
  "syncState": {
    "lastSyncedCommitSha": "abc123",
    "lastSyncAt": "2026-06-16T02:40:00",
    "lastForcePushDetectedAt": null
  }
}
```

## GET /api/health

`/api/health` and `/api/v1/health` return readiness-style checks for the database and retrieval index.

Response:

```json
{
  "status": "ok",
  "checks": {
    "db": "ok",
    "index": {
      "status": "healthy",
      "articles": 21,
      "indexedArticles": 21,
      "unindexedArticles": 0
    }
  }
}
```

`status` is `degraded` when the DB check fails or the article index is missing/stale.

## GET /healthz

Root liveness endpoint compatible with the original service shape.

```json
{
  "status": "ok"
}
```

## GET /api/articles/{id}/history

Returns the change history for the same article number within the same law.

For a `REVISION_COMPARE` question, `POST /api/ask` may include `historicalEntries` in the cited article being compared. This structured data allows users to immediately inspect the earlier revision body used in the answer without a separate history API call.

## GET /api/laws/{id}/revisions

Returns law-level revision groups. This reduced Spring implementation groups revisions by the articles' stored `effectiveFrom` and `amendmentKind`.

Response:

```json
{
  "lawId": 1,
  "lawTitle": "대외무역법",
  "revisions": [
    {
      "effectiveFrom": "2026-01-01",
      "amendmentKind": "개정",
      "articleCount": 1
    }
  ]
}
```

## GET /api/laws/{id}

Returns law metadata and the list of current articles.

Response:

```json
{
  "lawId": 1,
  "slug": "foreign-trade-act",
  "title": "대외무역법",
  "lawType": "LAW",
  "lawNumber": "LAW-001",
  "snapshotVersion": "law-domain-2026-001",
  "effectiveBasis": {
    "snapshotVersion": "law-domain-2026-001",
    "indexedAt": "2026-06-16T00:00:00",
    "sourcePath": "public-reference-data",
    "asOf": null
  },
  "articles": [
    {
      "articleId": 1,
      "articleNumber": "제19조",
      "articleTitle": "전략물자의 고시 및 수출허가",
      "effectiveFrom": "2026-01-01",
      "effectiveTo": null,
      "effectiveBasis": {
        "snapshotVersion": "law-domain-2026-001",
        "indexedAt": "2026-06-16T00:00:00",
        "sourcePath": "public-reference-data",
        "asOf": null
      }
    }
  ]
}
```

The `effectiveBasis` in law and article detail responses identifies the snapshot and source from which the item was retrieved.

## GET /api/laws

Returns laws from the most recently completed (`INDEXED`) snapshot only. Older snapshots remain available through article history and comparison endpoints, but are not duplicated in the default law list.

Query:

```text
q={keyword}&page=1&size=20
```

The `keyword` parameter is also supported for backward compatibility.

Response:

```json
{
  "total": 1,
  "page": 1,
  "size": 20,
  "items": [
    {
      "lawId": 1,
      "title": "대외무역법",
      "lawType": "LAW",
      "lawNumber": "LAW-001",
      "articleCount": 3,
      "revisionCount": 3
    }
  ]
}
```

## GET /api/articles/{id}/diff

Query:

```text
compareWith={previousArticleId}
```

Compares the body hashes and content of two article versions.

## POST /api/admin/reindex

Re-embeds currently stored articles and refreshes the vector index. The result is recorded as an ingestion run.
If any articles still fail after a failed batch is retried individually, `status` becomes `PARTIAL_FAILED`, and `failedArticles` and `errorMessage` include the failure summary.
This endpoint is disabled unless `ADMIN_REINDEX_ENABLED=true` is configured. When disabled, it returns `403 Forbidden` with an `error` message.
`/api/admin/reindex` keeps the Spring admin UI synchronous response contract. `/api/v1/admin/reindex` follows the original service shape more closely: it creates a `RUNNING` ingestion run, schedules the reindex job in the background, and immediately returns `202 Accepted`.

`/api/admin/reindex` response:

```json
{
  "ingestionRunId": 1,
  "status": "SUCCEEDED",
  "indexedArticles": 21,
  "failedArticles": 0,
  "errorMessage": null,
  "snapshotVersion": "law-domain-2026-001",
  "startedAt": "2026-06-16T06:00:00",
  "finishedAt": "2026-06-16T06:00:01"
}
```

`/api/v1/admin/reindex` response:

```json
{
  "status": "accepted",
  "message": "reindex scheduled",
  "ingestion_run_id": 1,
  "snapshot_version": "law-domain-2026-001",
  "started_at": "2026-06-16T06:00:00"
}
```

## POST /api/admin/ingest-local

Parses local Markdown law files, stores a new snapshot, and reindexes the stored articles.
When the same law and article number are ingested again, the previous current article receives an end date, and the new article links to it through `previousArticleId`.
If some files or article-indexing operations fail, the run status is `PARTIAL_FAILED`. The failure summary is recorded in the response and the ingestion run's `errorMessage`.

Request:

```json
{
  "sourceDir": "C:\\dev\\legal-data",
  "snapshotPrefix": "law-local"
}
```

Response:

```json
{
  "ingestionRunId": 2,
  "status": "SUCCEEDED",
  "snapshotVersion": "law-local-20260616060000",
  "filesProcessed": 8,
  "filesFailed": 0,
  "lawsImported": 8,
  "articlesImported": 21,
  "indexedArticles": 21,
  "errorMessage": null,
  "startedAt": "2026-06-16T06:00:00",
  "finishedAt": "2026-06-16T06:00:02"
}
```

Supported Markdown format:

```markdown
---
제목: 대외무역법
법령구분: 법률
법령MST: LAW-001
공포일자: 2026-01-01
시행일자: 2026-01-01
---

##### 제19조 (전략물자의 고시 및 수출허가)
전략물자를 수출하려는 경우에는 허가 요건을 확인해야 한다.
```

If upstream data repeats `제N조` headers within one file, the parser normalizes later duplicate article numbers to unused suffixes such as `제N조의2` and `제N조의3`. Explicit numbers such as `제N조의2` are retained.

Supported `법령구분` values are `법률`, `대통령령`, `시행령`, `총리령`, `시행규칙`, and the `*부령` pattern. Out-of-scope values such as `헌법`, `조약`, and `중앙선거관리위원회규칙` are excluded from ingestion.

The ingested article body's `contentHash` is computed after normalizing BOM, line endings, trailing whitespace, and consecutive blank lines. Reingestion with only formatting differences is treated as the same content; only actual wording differences change the hash.

## POST /api/admin/sync-source

Synchronizes a legal Markdown repository to a local path. With `ingestAfterSync=true`, ingestion and reindexing follow immediately.
If the previously synchronized commit is not an ancestor of the remote branch, synchronization stops as a history conflict and records the detection time in `syncState.lastForcePushDetectedAt`.

Request:

```json
{
  "repoUrl": "https://example.com/legal-data.git",
  "localDir": "C:\\dev\\legal-data",
  "branch": "main",
  "ingestAfterSync": true,
  "snapshotPrefix": "law-source"
}
```

Response:

```json
{
  "action": "PULLED",
  "repoUrl": "https://example.com/legal-data.git",
  "localDir": "C:\\dev\\legal-data",
  "branch": "main",
  "commitHash": "abc123",
  "ingestion": {
    "status": "SUCCEEDED",
    "lawsImported": 8,
    "articlesImported": 16,
    "indexedArticles": 16
  }
}
```

## GET /api/admin/ingestion-runs

Returns recent reindex run history.
`status` is one of `RUNNING`, `SUCCEEDED`, `PARTIAL_FAILED`, or `FAILED`.

## GET /api/admin/search-logs

Returns recent search logs. Full original questions are not stored. The response provides `requestId`, `questionHash`, `questionLength`, and an incomplete `questionPreview` with sensitive patterns replaced. For date-based requests, `asOf` stores the requested basis date; for currently effective articles, it is `null`.

`requestId` is the audit key linking the agent traces and search log generated while processing the same question.

## GET /api/admin/agent-traces

Returns recent agent traces. Provide the `requestId` query parameter to filter a particular question-processing flow.

```text
GET /api/admin/agent-traces?requestId={requestId}
```

## POST /api/admin/provider-smoke-test

Calls each configured LLM, embedding, and reranker provider once to check connectivity. Even if some providers fail, the overall request returns 200; failed providers are marked `failed` with safe error codes.

Response:

```json
{
  "llmProvider": "openrouter",
  "embeddingProvider": "openrouter",
  "rerankerProvider": "cohere",
  "llmStatus": "ok",
  "embeddingStatus": "ok",
  "rerankerStatus": "ok",
  "embeddingDimensions": 1024,
  "rerankedCount": 1,
  "topRerankedId": "a",
  "llmResult": {
    "status": "OK",
    "message": "provider ready"
  },
  "llmError": null,
  "embeddingError": null,
  "rerankerError": null
}
```

Partial failure example:

```json
{
  "llmProvider": "openrouter",
  "embeddingProvider": "openrouter",
  "rerankerProvider": "cohere",
  "llmStatus": "failed",
  "embeddingStatus": "ok",
  "rerankerStatus": "failed",
  "embeddingDimensions": 1024,
  "rerankedCount": 0,
  "topRerankedId": null,
  "llmResult": {},
  "llmError": "llm_response_format_failed",
  "embeddingError": null,
  "rerankerError": "reranker_provider_failed"
}
```
