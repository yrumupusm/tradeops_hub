# Agent Orchestration

[한국어](../ko/agent-orchestration.md) | [English](../en/agent-orchestration.md)

This project is a role-based agent pipeline, not a fully autonomous multi-agent system.

| Agent | Responsibility |
| --- | --- |
| QueryAnalyzerAgent | Extract question type, action, object, and search terms |
| RetrievalAgent | Retrieve relevant articles |
| EvidenceValidatorAgent | Determine whether evidence supports an answer and flag weak evidence |
| AnswerWriterAgent | Draft from cited articles; deterministic metadata/revision responses; Korean-language, forbidden-copy, and decisive-statement quality gates; one corrective retry |
| CriticAgent | Final validation of citation invariants and Korean-language, forbidden-copy, and decisive-statement rules for visible text |
| SearchLogAgent | Store search logs |
| AskAuditLogger | Store completion-event JSON payloads without full questions |

## Interview Explanation

The design uses a role-based agent pipeline rather than fully autonomous agents. Question analysis, retrieval, evidence validation, answer generation, and final review are separate, while the orchestrator controls their order and failure handling.

LLM output is not shown directly. It is checked for Korean wording, cited evidence, demo-oriented copy, and definitive legal judgments; failures trigger a corrective retry.

The OpenRouter chat client uses `response_format=json_object`. If the model returns fenced JSON or surrounding prose, the client extracts the first JSON object and passes it to the answer quality gate.

At the final stage, `CriticAgent` inspects both reasoning and follow-up questions, blocking English-dominant sentences, demo-oriented copy, and definitive legal judgments from the screen.

Even when retrieval returns articles, low relevance scores cause `EvidenceValidatorAgent` to flag weak evidence, and `AnswerWriterAgent` limits the response to `LOW_CONFIDENCE`.

Administrative traces are stored in the order `QueryAnalyzerAgent`, `RetrievalAgent`, `EvidenceValidatorAgent`, `AnswerWriterAndCritic`, allowing each request's decision flow to be reconstructed.

Questions answerable from data, such as effective dates and revision comparisons, prioritize deterministic responses based on legal metadata and article history over LLM generation.

A missing index snapshot or an exception during analysis, retrieval, evidence validation, or answer generation produces a `FAILED` response, with a failed search log and failed agent trace sharing the same `requestId`. Missing citations or quality-rule violations found by `CriticAgent` also downgrade the response to `FAILED`. Clients receive only safe stage-specific or quality-gate-specific `errorMessage` codes, not raw provider exceptions.

Request completion is recorded as a single `event=ask_complete payload={...}` line. The JSON payload excludes the full question and contains only its hash and length, status, latency, and retrieval statistics, balancing operational visibility and information protection.

HTTP request logs are separate from agent traces. Each `event=http_request payload={...}` line records only method, path, status, and elapsedMs, without query strings or request bodies.
