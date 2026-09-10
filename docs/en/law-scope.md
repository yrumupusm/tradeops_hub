# Law Scope

[한국어](../ko/law-scope.md) | [English](../en/law-scope.md)

This service does not index all laws of the Republic of Korea.

The original `law_research_assistant` began with a limited subset of public legal sources. The Spring Boot version focuses on a RAG workflow for trade security, defense, and technology protection, rather than building a database of all laws.

## Included Laws

The Korean source titles are retained unchanged:

- 대외무역법
- 방위사업법
- 관세법
- 외국환거래법
- 국가첨단전략산업 경쟁력 강화 및 보호에 관한 특별조치법
- 산업기술의 유출방지 및 보호에 관한 법률
- 국방과학기술혁신 촉진법
- 군수품관리법

The seed corpus consists of 21 summarized articles based on public-domain material for these eight laws. Evaluation questions cover strategic goods exports, providing technical data overseas, tank/defense material exports, strategic goods classification, the legal basis for 무역안보관리원, export declarations under 관세법, and revision comparison. These seed counts describe initial verification data, not the live ingested database; ingestion can also include subordinate legislation within the configured scope.

## Why This Scope

- The goal is to demonstrate AX/RAG workflow design skills, rather than operate a comprehensive legal search service.
- A small corpus makes query analysis, embeddings, vector search, citations, and scenario tests easier to explain in interviews.
- It reproduces a scaled-down business workflow without company-internal data.

## Data Safety

Seed articles are summaries based on public-domain material. They are neither official full legal texts nor company-internal data. They are used to verify retrieval, citations, answer synthesis, search logs, and agent traces.
