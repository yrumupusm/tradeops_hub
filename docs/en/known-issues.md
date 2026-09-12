# Issues identified so far

Recorded: 2026-09-13. These are unresolved findings from today's service review. Proposed remedies are plans, not completed implementation. Existing operational limits and publication readiness remain in the [task list](../../tasks/todo.md).

## Service improvements

### RAG-01 — Long-article embedding input limit

- [ ] Implement and verify
- Articles form retrieval units, but the OpenRouter embedding input uses only the first 4,000 characters under the default setting. The database retains the full original article.
- Content appearing only near the end may be underrepresented in vector retrieval; this is not a claim that every long article is missed.
- Direction: consider child chunks retaining the article identifier and consolidate retrieved results.
- Acceptance: verify retrieval and original-article links for questions supported near the end of long articles. Preserve existing data/vectors and provide index switching and recovery.
- Evidence: input truncation in `OpenRouterEmbeddingClient` and `app.embedding.max-chars` in `application.yml`.

### RAG-02 — Weakly relevant retrieval candidates

- [ ] Improve and verify
- The reviewed runtime used `MockRerankerClient`, which sorts existing scores rather than calling a separate AI reranking model. Keyword/vector retrieval and score combination are implemented.
- The actual tank-export question included council-related articles with weak direct relevance. Missing AI reranking has not been established as the sole cause.
- Direction: evaluate candidate retrieval and score combination; compare reranking models if needed.
- Acceptance: compare evidence retrieval quality on fixed questions and check latency/cost. Connecting a model alone does not complete this item.

### RAG-03 — Interpretation and omission risks despite citations

- [ ] Improve guidance and quality verification
- Citation conditions and expression checks do not guarantee correct legal interpretation or complete coverage of required conditions.
- Direction: retain original-source review and human final-judgment guidance; evaluate answer/evidence agreement and missing conditions.
- Acceptance: include incorrect evidence links and omitted conditions in evaluation. Do not claim complete elimination of risk.

### RAG-04 — Insufficient systematic project-level quality evaluation

- [ ] Establish evaluation set and comparison procedure
- Live operation was checked, but this review did not establish a fixed evaluation set with expected answers/evidence and measured retrieval/answer quality. Functional tests or an `OK` response are distinct from answer correctness.
- Direction: define representative questions, expected articles, required answer elements and criteria; record a baseline.
- Acceptance: support reproducible evaluation and failure review with model/search settings and corpus reference dates. Do not claim model superiority without comparison.

### SEARCH-01 — General English abbreviation expansion not implemented

- [ ] Define scope, implement and verify
- Original-name and registered-alias search works. General rules equating `Ltd` with `Limited` or `Inc` with `Incorporated` without a registered alias are not implemented.
- Direction: define search-only abbreviation rules. Preserve original names and never merge companies by name. Do not equate `Co` and `Corp`.
- Acceptance: verify fictional cases without registered aliases and check increased false positives.

## Resolution order and record

Establish the RAG-04 baseline before assessing RAG-01/RAG-02/RAG-03 improvements. SEARCH-01 can proceed independently. On completion, update the checkbox and record the change commit, verification and remaining limits in both language versions.
