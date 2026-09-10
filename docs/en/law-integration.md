# Law search integration

[한국어](../ko/law-integration.md) · [English](law-integration.md)

## Boundary and API

Hub `/law-search` uses the existing Korean shell and native components. The law RAG repository owns analysis, retrieval, generation, PostgreSQL and Qdrant. No iframe, copied UI, admin proxy, shared DB or combined export-permission decision is introduced. The standalone RAG UI remains a development tool.

All routes require existing Hub session/active-account/password-change checks; POST also requires CSRF. Ordinary authenticated users may use them. Only these fixed routes are forwarded:

| Hub prefix /api/v1/law-search | RAG |
| --- | --- |
| POST /ask | POST /api/ask |
| GET /articles/{id}/history | GET /api/articles/{id}/history |
| GET /articles/{id}/diff?compareWith={id} | GET /api/articles/{id}/diff?compareWith={id} |

Ask accepts question (nonblank, <=4000 characters), asOf (null or valid YYYY-MM-DD), researchAreas (absent/null/empty, STRATEGIC_GOODS, DEFENSE_MATERIALS, or both without duplicates). Unknown fields and nonpositive article IDs fail. No cookies, CSRF tokens or arbitrary browser headers are forwarded. Management/log/ingestion/index routes are unavailable.

Responses retain status, reasoning, followUpQuestions, citedArticles, disclaimer, effectiveBasis.asOf, RAG requestId and Hub correlationId. Nullable article fields, content, previousArticleId and historicalEntries are preserved. Interpretation, scores, candidate laws, raw errorMessage and source paths are omitted. Server audit records actor, Hub correlation, RAG requestId and full effectiveBasis, without question/answer/article text. This maps to RAG SearchLog/AgentTrace; those logs are not personal history. Questions are never stored in URLs or watchlist history.

OK requires citations; LOW_CONFIDENCE with retrieved/hydrated evidence also requires citations. Missing/malformed required fields produce LAW_INVALID_RESPONSE (502). LOW_CONFIDENCE/INSUFFICIENT_INFO remain distinct 200 states. FAILED is a 200 state with safe copy and no untrusted partial answer. Connection/HTTP/redirect failures map to LAW_UNAVAILABLE (503), deadline to LAW_TIMEOUT (504), malformed JSON to LAW_INVALID_RESPONSE (502), upstream 400 to LAW_REQUEST_REJECTED (502), missing articles to LAW_ARTICLE_NOT_FOUND (404). Provider exception text is never exposed. RAG availability does not gate BIS or Hub health.

## Korean interaction

Separate legal-research navigation sits below the watchlist group. The form shows question, optional fields 전략물자 / 방산물자·국방과학기술, and optional 기준일. Areas affect priority/context, not legal classification or a strict law allowlist. Scope is eight core trade-security/defense/technology-protection laws and collected subordinate legislation, not all Korean laws or live official synchronization.

Answers are text. Citation cards initially show law/article/title; keyboard-accessible details expand/collapse content, dates, history and comparison. Missing previousArticleId disables previous comparison. Embedded historicalEntries are retained; fetched history can be compared too. Diff shows both original texts and equality, without generating amendments. Only literal ** display markers are removed; HTML is never injected.

Submission is disabled while loading; no invented stages/percentages. Navigation aborts browser requests and discards late responses, without promising cancellation of provider work. Inputs remain for manual retry. No automatic POST retry.

## Deployment and preservation

Server-only LAW_RAG_BASE_URL points to the existing internal origin (local example http://127.0.0.1:8080). Blank means law connectivity unavailable. Only http/https origins without credentials/query/fragment/additional path are accepted. RAG does not validate Hub sessions: restrict RAG/DB/Qdrant network access. Containers require service DNS. Inspected DB host ports: Hub 5433, RAG 5432.

LAW_RAG_TIMEOUT_SECONDS defaults to 180, allowed 1–180; connect timeout 5 seconds. The total deadline includes response reading, capped at 4 MiB. Next.js proxy is 195 seconds, browser 205 seconds. These finite budgets may expire across multiple provider calls/retries (provider defaults are 30 seconds each). Tune providers separately using measurements; never silently retry paid questions.

Start existing RAG with its own server script and existing settings, then Hub with LAW_RAG_BASE_URL. Do not recreate databases, remove volumes/collections, reingest or reindex to connect. Hub has no RAG DB credentials and runs no law migration/vector writes. Hub-only restarts cause no RAG data work. Repositories and volumes stay separate.

## Verification

RAG inspected revision: 9c2d67fca773c50eca885a98b0a0b144f4dd742c; untracked output remains untouched. Hub branch feat/law-search is based on 7445fbf. Final tested revisions and results are recorded after runtime verification.

Initial read-only health: DB ok, articles 4112, indexed 4431, unindexed 0, index stale, overall degraded. This is not full readiness or an automatic reindex instruction. Compare counts after checks.

LawIntegrationTest covers input, states, citations, projection, trace correlation, history/diff and session/CSRF/forced-change/deactivation. LawClientTest uses a controlled HTTP server for headers, redirects, malformed JSON, deadline and no retries. Browser and small real-provider checks supplement these; builds alone are not acceptance. Existing BIS regression remains in the local gate.
