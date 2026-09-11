# Law search integration

Current repository policy: since 2026-09-11 the law service lives in `services/law-search/` in this monorepo. Runtime/data remain separate. See [monorepo ownership](monorepo.md) for Git boundaries.

[한국어](../ko/law-integration.md) · [English](law-integration.md)

## Boundary and API

Hub `/law-search` uses the existing Korean shell and native components. The law service in `services/law-search/` owns analysis, retrieval, generation, PostgreSQL and Qdrant. No iframe, copied UI, shared DB or combined export-permission decision is introduced. The standalone RAG UI remains a development tool.

All routes require existing Hub session/active-account/password-change checks; POST also requires CSRF. Ordinary authenticated users may use them. Only these fixed routes are forwarded:

| Hub prefix /api/v1/law-search | RAG |
| --- | --- |
| POST /ask | POST /api/ask |
| GET /articles/{id}/history | GET /api/articles/{id}/history |
| GET /articles/{id}/diff?compareWith={id} | GET /api/articles/{id}/diff?compareWith={id} |

Ask accepts question (nonblank, <=4000 characters), asOf (null or valid YYYY-MM-DD), researchAreas (absent/null/empty, STRATEGIC_GOODS, DEFENSE_MATERIALS, or both without duplicates). Unknown fields and nonpositive article IDs fail. No cookies, CSRF tokens or arbitrary browser headers are forwarded. The separate owner-only administration API is described below.

Responses retain status, reasoning, followUpQuestions, citedArticles, disclaimer, effectiveBasis.asOf, RAG requestId and Hub correlationId. Nullable article fields, content, previousArticleId and historicalEntries are preserved. Interpretation, scores, candidate laws, raw errorMessage and source paths are omitted. Server audit records actor, Hub correlation, RAG requestId and full effectiveBasis, without question/answer/article text. This maps to RAG SearchLog/AgentTrace; those logs are not personal history. Questions are never stored in URLs or watchlist history.

OK requires citations; LOW_CONFIDENCE with retrieved/hydrated evidence also requires citations. Missing/malformed required fields produce LAW_INVALID_RESPONSE (502). LOW_CONFIDENCE/INSUFFICIENT_INFO remain distinct 200 states. FAILED is a 200 state with safe copy and no untrusted partial answer. Connection/HTTP/redirect failures map to LAW_UNAVAILABLE (503), deadline to LAW_TIMEOUT (504), malformed JSON to LAW_INVALID_RESPONSE (502), upstream 400 to LAW_REQUEST_REJECTED (502), missing articles to LAW_ARTICLE_NOT_FOUND (404). Provider exception text is never exposed. RAG availability does not gate BIS or Hub health.

## Korean interaction

Separate legal-research navigation sits below the watchlist group. The form shows question, optional fields 전략물자 / 방산물자·국방과학기술, and optional 기준일. Areas affect priority/context, not legal classification or a strict law allowlist. Scope is eight core trade-security/defense/technology-protection laws and collected subordinate legislation, not all Korean laws or live official synchronization.

Answers are text. Citation cards initially show law/article/title; keyboard-accessible details expand/collapse content, dates, history and comparison. Missing previousArticleId disables previous comparison. Embedded historicalEntries are retained; fetched history can be compared too. Diff shows both original texts and equality, without generating amendments. Only literal ** display markers are removed; HTML is never injected.

Submission is disabled while loading; no invented stages/percentages. Navigation aborts browser requests and discards late responses, without promising cancellation of provider work. Inputs remain for manual retry. No automatic POST retry.

## Deployment and preservation

Server-only LAW_RAG_BASE_URL points to the existing internal origin (local example http://127.0.0.1:8080). Blank means law connectivity unavailable. Only http/https origins without credentials/query/fragment/additional path are accepted. RAG does not validate Hub sessions: restrict RAG/DB/Qdrant network access. Containers require service DNS. Inspected DB host ports: Hub 5433, RAG 5432.

LAW_RAG_TIMEOUT_SECONDS defaults to 180, allowed 1–180; connect timeout 5 seconds. The total deadline includes response reading, capped at 4 MiB. Next.js proxy is 195 seconds, browser 205 seconds. These finite budgets may expire across multiple provider calls/retries (provider defaults are 30 seconds each). Tune providers separately using measurements; never silently retry paid questions.

Start existing RAG with its own server script and existing settings, then Hub with LAW_RAG_BASE_URL. Do not recreate databases, remove volumes/collections, reingest or reindex to connect. Hub has no RAG DB credentials and runs no law migration/vector writes. Hub-only restarts cause no RAG data work. Git history is shared; runtime processes, configuration and volumes remain separate.

## Verification

LawIntegrationTest covers input, response states, citation invariants, projection, correlation, history/diff and session/CSRF/forced-password-change/deactivation. LawClientTest covers controlled HTTP failures, redirects, malformed JSON, deadlines and no retries. LawAdminIntegrationTest covers the owner boundary and read/action allowlists. Browser checks supplement these contracts; production builds alone are not acceptance.

Current completion evidence and remaining deployment prerequisites are recorded in the [verification guide](evaluation-harness.md). Earlier preview/deployment results remain in Git history and the historical task record.

## Owner administration

Action notices use red dots and 긴급 for failed runs/missing index, orange dots and 확인 필요 for stale index/source-history changes, and slate dots with 참고 when no action is needed. Text labels accompany colors.

Status also preserves syncState.lastSyncedCommitSha, lastSyncAt and lastForcePushDetectedAt without source paths. The UI restores sync metadata and notices for failed runs, missing/stale indexes and historical source rewrites. At widths >1100px and heights >=760px the base screen fits the viewport: summary above actions/runs, with internal list scrolling. Selecting a law shortens the corpus list to reveal the start of its details and permits document scrolling. Smaller viewports retain document scrolling. No data/index work runs automatically.

`/law-admin` appears under 법령 조사, immediately after 법령 검색, for the fixed owner. The breadcrumb is 법령 조사 / 법령 검색 관리. Every `/api/v1/law-admin` endpoint also enforces backend owner authorization and the existing account/session restrictions.

GET `/{section}` allows only status, laws, ingestion-runs, search-logs and agent-traces. Laws accept q (<=200 characters) and page (1..10000), size is fixed at 20. Traces accept an empty or canonical UUID requestId. GET `/laws/{id}` and `/laws/{id}/revisions` require positive IDs. Lists preserve upstream limits: 20 ingestion runs, 50 search logs, 100 traces. Logs link to filtered traces. Response projection excludes question previews/hashes, trace input/output, raw errors, source paths and repository URLs.

POST `/actions/{action}` accepts only an empty JSON object and requires CSRF. Actions are sync-source (with ingestAfterSync=true), ingest-local, reindex and provider-smoke-test. RAG's configured source URL/directory/branch are used; the browser cannot supply paths or URLs. Reindex also checks RAG's enabled flag. The UI asks for confirmation before execution. These operations may update law data/vectors or invoke billed providers; no operation is performed automatically by mounting the page or deploying. A single Hub process permits one concurrent administration call. Hub audit records actor, action, correlation and start/return/failure, without raw upstream content. A returned response is not necessarily operation success: inspect reported status/counts.

The existing bounded client does not retry. A timeout or browser navigation does not promise upstream cancellation; inspect status and run history before manually retrying. This is not a durable cross-process job queue. Source settings must already exist in the RAG environment; missing settings produce a safe failure. Hub does not access law databases or write embeddings; law application code resides in the monorepo service directory.

Administration has three tabs: operating status (summary above side-by-side actions and ingestion history), corpus, and search logs. Search logs and the selected request trace share equal-width columns, stacked at widths <=1100px. Clicking a request ID highlights its row, fills the trace input and loads that request without leaving the list. Direct UUID lookup, reset, independent loading/errors and cancellation of stale requests are supported. Before selection no unfiltered trace request is sent. Existing APIs and owner authorization are unchanged.

On desktop widths above 1100px and heights of at least 760px, administration fits the available viewport with internal list scrolling. Corpus details may extend the page after selection. Smaller screens fall back to page scrolling. Corpus and trace forms bottom-align their 40px inputs and query/reset buttons. Status includes synchronization revision/time, historical source-history-change detection and severity-labelled colored notices. Historical detection is not silently cleared by index maintenance.
