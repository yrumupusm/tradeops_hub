# TradeOps Hub Law Search Integration Handoff

[한국어](../ko/tradeops-integration-handoff.md) | [English](../en/tradeops-integration-handoff.md)

Written on 2026-09-10. This is an implementation handoff, not a report that integration is complete.

## Agreed Direction and Scope

- Keep Git repositories separate. Present a single service through TradeOps Hub and connect the RAG server by API.
- Place an independent ‘법령 검색’ menu immediately below Hub's ‘우려거래자 조회/수집’ menu group. Do not make it a collection subfeature.
- Follow TradeOps Hub UI/UX. Reuse shared navigation, colors, typography, spacing, inputs, buttons, and error presentation.
- Do not embed the RAG page in an iframe or copy its standalone CSS wholesale. Render response data through Hub components.
- The first integration covers authenticated users' questions, search results, cited articles, and article history/comparison. Integrating the RAG admin UI is a separate follow-up.
- Do not reingest or re-embed laws just to connect the services. Reuse existing PostgreSQL and Qdrant data.

## Repositories and Ownership

| Repository | Responsibility | Suggested branch |
| --- | --- | --- |
| `C:\dev\tradeops_hub` | Lead integration: navigation, UI, session authentication, authorization, API relay, operational connectivity | `feat/law-search` |
| `C:\dev\law-research-assistant-spring` | Question analysis, retrieval, evidence validation, answers, legal ingestion, indexing | `feat/tradeops-integration` if RAG changes are needed |

Before implementation, inspect each repository's `AGENTS.md`, `PROJECT_GUIDE.md`, and working changes. Do not overwrite another AI's work. Verify on feature branches and manage PRs/commits separately per repository.

Code baselines inspected when this document was written:

- RAG: `47d0d94afdf69c3beff9210664c42eb45ad78f17`
- Hub: `7445fbf2951a4a6736d1a998fa52ffb6a18c5910`

These are investigation baselines. Recheck branches and uncommitted changes before starting. On completion, record both jointly verified commits and runtime configuration in Hub's deployment/verification documentation.

## Recommended Call Structure

```text
Browser: TradeOps Hub law search screen
  -> Hub same-origin /api/v1/law-search/...
  -> Hub Spring Boot: session/CSRF/authorization checks, RAG client call
  -> RAG /api/ask or allowlisted article-read API
  -> Existing RAG PostgreSQL, Qdrant, and configured model providers
```

Hub's `/api/v1/law-search/...` and screen path `/law-search` are proposals, not existing routes. Finalize them according to Hub routing conventions.

Hub integration points inspected:

- `frontend/src/components/console.tsx`: menu configuration and screen selection
- `frontend/src/app/[...path]/page.tsx`: allowed screen paths
- `frontend/src/lib/api.ts`: `/api/v1` requests, `X-CSRF-TOKEN`, shared error handling
- `frontend/next.config.ts`: relays `/api/v1/:path*` to `API_PROXY_TARGET`
- `backend/src/main/java/io/tradeops/auth/SecurityConfig.java`: login, CSRF, mandatory temporary-password change

Add a RAG client and dedicated endpoints to the Hub server. Preserve the existing authentication path; do not bypass Hub authentication with direct Next.js-to-RAG rewrites. Configure the actual RAG base URL through a Hub server environment variable (for example, a newly defined `LAW_RAG_BASE_URL`), not a browser-exposed setting.

RAG currently does not validate Hub sessions or user permissions. Its admin APIs are not protected by Hub authorization either. For integrated deployment, restrict RAG and its stores to an internal network or required local interfaces, and explicitly relay only allowlisted search/article-read routes through Hub. Do not build a general-purpose proxy accepting arbitrary URLs or paths. There is no need to forward Hub cookies, CSRF values, or arbitrary user-supplied headers directly to RAG.

## RAG APIs to Use

Even though Hub uses `/api/v1`, standardize outbound RAG calls on the **`/api` camelCase contract** below. RAG `/api/v1` provides separate snake_case/lower-case-enum compatibility responses; do not mix the formats.

| RAG API | Use |
| --- | --- |
| `POST /api/ask` | Questions, answers, and cited articles |
| `GET /api/articles/{id}/history` | Cited article history |
| `GET /api/articles/{id}/diff?compareWith={previousArticleId}` | Compare with a previous article |
| `GET /api/articles/{id}` | Retrieve individual articles when needed |
| `GET /api/laws?q={keyword}&page=1&size=20` | Retrieve law lists when needed; pagination is required |
| `GET /healthz` | Server liveness |
| `GET /api/health` | Database/index state; check both HTTP code and response body |

Do not relay all administrative `/api/admin/*` routes to user screens. See [API contract](api-contract.md) for specifications and `src/main/java/com/example/lawassistant/dto/` for actual fields. JSON examples in the API document omit some fields.

### Question Request

```json
{
  "question": "탱크를 수출하고 싶은데 관련된 법령에는 어떤 게 있어?",
  "asOf": null,
  "researchAreas": ["DEFENSE_MATERIALS"]
}
```

- `question`: required, not whitespace-only, maximum 4000 characters.
- `asOf`: optional, `YYYY-MM-DD` or `null`; `null` means currently effective articles.
- `researchAreas`: optional; no selection is `[]`. Multiple selections of `STRATEGIC_GOODS` and `DEFENSE_MATERIALS` are allowed.
- Selected areas provide retrieval priorities and answer context. Do not present them as legal classifications or hard filters admitting only certain laws.
- Do not append new request-body fields such as `userId` or `requestId`. Strict validation returns HTTP 400 for unsupported fields.
- Invalid input returns HTTP 400 and `{"error":"invalid request"}`. Map it to Hub's shared error format.

### Response-to-Screen Mapping

| Response field | Hub display/handling |
| --- | --- |
| `reasoning` | Answer body in search results; Hub must not generate extra legal explanations |
| `followUpQuestions` | Follow-up questions |
| `citedArticles` | Cited article list |
| `effectiveBasis.asOf` | User-selected basis date or currently effective articles |
| `disclaimer` | Server-provided verification notice, which may vary by area |
| `status`, `errorMessage` | Result/error branching; do not display internal failure codes as user copy |
| `diagnostics.requestId` | RAG request ID for operational tracing |
| Full `effectiveBasis` | Preserve evidence traceability; hide source paths/snapshots from ordinary screens |
| `interpretation`, `candidateLaws`, `confidence`, remaining `diagnostics` | Internal inspection data, not displayed in ordinary results |

`effectiveBasis` contains `snapshotVersion`, `indexedAt`, `sourcePath`, `sourceVersion`, and `asOf`. Some source fields can be `null`. Retain them in the original response/server-side trace. If the browser response is reduced, ensure source and request correlation are not lost on the server.

Each `citedArticles` item has:

```text
articleId, lawId, lawTitle, articleNumber, articleTitle,
content, reason, contentHash, effectiveFrom, effectiveTo,
amendmentKind, previousArticleId, historicalEntries
```

Initially, a citation card shows only the law title, article number/title, and ‘전체 보기’. Expansion shows the body, effective period, and history/comparison controls, and must support collapsing again. Do not display retrieval scores from `reason`. Disable previous-article comparison when `previousArticleId` is absent. Preserve earlier revisions in `historicalEntries`, because they are also revision-comparison evidence.

History responses have `{lawId, lawTitle, articleNumber, entries}`; comparison responses include `contentA`, `contentB`, and `contentHashEqual`. Safely render comparison bodies as text. The display layer may clean `**` markers in `content`, as the existing UI does, but must not arbitrarily alter legal wording or article numbers. Do not inject the content directly as HTML.

### Status and Failure Handling

| `status` | Handling |
| --- | --- |
| `OK` | Show answer and citations; at least one citation is required |
| `LOW_CONFIDENCE` | Show a weak-evidence notice and the supplied answer/citations |
| `INSUFFICIENT_INFO` | Explain missing information and show follow-up questions |
| `FAILED` | Show a safe failure notice and a retry action |

An HTTP 200 body can still be `FAILED`. Conversely, do not turn every non-OK status into a transport error. For `LOW_CONFIDENCE`, if `diagnostics.retrievalStats.retrieved > 0` or `hydrated > 0`, at least one citation is required. Do not display contract-violating responses as successful answers.

Current failure codes are `no_snapshot`, `query_analysis_failed`, `retrieval_failed`, `evidence_validation_failed`, `answer_generation_failed`, `missing_citation`, and `response_quality_failed`. Hub must separately handle RAG connection failures, timeouts, and malformed responses without exposing raw provider exceptions.

`/api/ask` currently returns one synchronous JSON response. There is no SSE or progress-stage query API. Show a loading indicator, but do not invent processing stages or percentages. Prevent duplicate clicks and stale earlier responses from overwriting newer results.

Per-provider timeouts are not whole-question deadlines. Account for `HTTP_TIMEOUT_SECONDS`, `LLM_TIMEOUT_SECONDS`, multiple retrieval/model calls, and retries when designing and testing timeouts across the RAG client, Hub, Next proxy, and browser. Avoid both prematurely short defaults and unlimited waits. Question POSTs incur costs and logs, so do not automatically resend them. Do not assume canceling a browser request stops internal RAG processing.

## Screen Details

- Title: ‘법령 검색’. Search button: ‘검색’.
- Provide question input, supported-scope guidance, optional area selections, and a basis date.
- Area labels: ‘전략물자’, ‘방산물자·국방과학기술’. Support neither, either, or both.
- Search covers eight core trade-security/defense/technology-protection laws and ingested subordinate legislation. Do not advertise all Korean laws or real-time official law synchronization.
- Hide retrieval scores, question interpretation, retrieval process, candidate-law cards, index status, and source server paths from ordinary screens.
- Keep long answers/articles readable within the Hub layout; test collapse/scroll, mobile, and keyboard use.
- Automatically forwarding law questions to concern-party screening or combining both results to determine export permission is outside this scope.
- Keep the existing RAG static UI for independent development/inspection. Record Hub user-facing UI standards in the Hub repository.

## Authentication and Request Tracing

Preserve Hub session, CSRF, account-deactivation, and mandatory-password-change checks. Separate ordinary question/article-read features from operator administration. Hiding UI alone is not authorization.

RAG generates its own UUID request ID in `AskOrchestratorService`; it does not automatically match Hub's `correlationId`. Store the mapping between user identifier, Hub correlation ID, and response `diagnostics.requestId` on the Hub server. RAG responses, SearchLog, and AgentTrace must retain one shared RAG request ID.

If Hub adds search history, design per-user access separately. Existing RAG admin logs are not per-user history and must not be exposed directly to ordinary users. Do not record full questions in operational logs, URL queries, or analytics events. Do not casually copy the existing RAG UI's URL-sharing behavior. Follow the hash, length, and masked short-preview rules.

## Runtime and Data Preservation

Development defaults: Hub web `http://localhost:3000`, Hub API `http://127.0.0.1:8081`, RAG `http://localhost:8080`, Qdrant `http://localhost:6333`. Actual environment configuration takes precedence. Inside a container, `localhost` refers to that container; configure service DNS/networking separately for deployment.

If existing containers are available in the RAG repository:

```powershell
docker compose ps -a
docker compose start postgres qdrant
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\server.ps1 start -MavenPath C:\dev\apache-maven-3.9.9\bin\mvn.cmd
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\server.ps1 status
```

For a new environment, prepare `.env`, providers, and the database according to the README. Empty defaults can use H2, mock providers, and reference data; do not mistake that for an existing real-data connection. Do not copy `.env` secrets into handoff documents or Git.

Both Compose configurations default to PostgreSQL host port `5432`. Check actual running ports first; if necessary, adjust one `POSTGRES_PORT` and its corresponding JDBC URL together. Keep databases/volumes separate. Verify deployment settings so an existing database is not started with the default `ddl-auto=create-drop`. Do not resolve conflicts by using `docker compose down -v` or deleting a Qdrant collection.

A script/Compose setup to operate both services with one command remains follow-up work; no completed integrated startup configuration exists yet. Preserve existing data paths, volumes, embedding models, dimensions, and collections. Restarting Hub alone must not trigger RAG data operations.

## Known State and Interpretation Cautions

- An earlier admin screen showed 4,112 DB articles / 4,431 vectors and `stale`. This is a historical observation; query current state again. No reindexing or cleanup was performed while writing this document.
- `stale` signals count disagreement. Determine whether the cause is excess or missing vectors; do not automatically reindex. `unindexedArticlesCount=0` does not imply there are no excess vectors.
- Health APIs may return HTTP 200 with `status=degraded`. Distinguish liveness from search readiness. A temporary RAG outage should not block concern-party screening.
- The 21 seed articles described in [law scope](law-scope.md) are initial verification data, not the live database's ingested count.
- Do not arbitrarily patch RAG quality issues such as date validity, citation accuracy, or supplementary-provision separation in the Hub UI. Provide reproduction details and the RAG request ID to the RAG owner.

## Completion Verification and Handoff Results

1. Verify login/logout, permissions, CSRF, and mandatory-password-change rules also apply to law search.
2. Verify rejection of over-4000-character, blank, invalid-date, and invalid-area input, and transmission of zero, one, or multiple areas.
3. Use fixed inputs: tank-export law questions with defense selected, strategic-goods questions with strategic selected, and general export questions with no selection. Prioritize API contracts and evidence preservation over exact external-model wording.
4. Use controlled responses to test success, weak evidence, insufficient information, failure, HTTP errors, timeouts, and RAG downtime. Do not turn failures into empty search results.
5. Verify citation bodies, expansion/collapse, history, previous-article comparison, nullable fields, and `historicalEntries` display.
6. Check long questions/answers, small screens, keyboard use, duplicate clicks, rapid searches, and delayed responses after navigation.
7. Verify Hub correlation ID/RAG request ID linkage, privacy of other users' logs, and exclusion of full questions from logs.
8. Run regressions for existing concern-party screening/collection and authentication, plus Hub's prescribed repository verification. If RAG changes, run its relevant tests and `mvn test`.
9. Use real provider calls only for the small number of end-to-end checks needed, accounting for cost and latency. Do not reingest or reindex operational data for tests.
10. Record finalized routes, environment variables, startup procedures, failure handling, and both verified repository commits in Hub docs. Explicitly coordinate required API contract changes with the RAG owner.

## Request to Pass to the Hub AI

> Integrate law search on a separate branch using this document. Keep Git repositories separate and add an independent ‘법령 검색’ menu below the ‘우려거래자 조회/수집’ group. Follow TradeOps Hub's existing UI components and styles. Call RAG APIs through Hub login and authorization, reusing the existing law database and embeddings. Leave RAG admin integration for later; implement user search, answers, citations, and history/comparison first. If API changes are needed, document the rationale and required contract. Preserve existing work, verify features and error/authorization handling, and record startup instructions and the two repository versions verified together.
