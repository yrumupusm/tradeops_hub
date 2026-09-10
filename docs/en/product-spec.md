# Product Specification

[한국어](../ko/product-spec.md) | [English](../en/product-spec.md)

## Goal

When a user enters a natural-language legal question, retrieve relevant laws and articles and return research assistance grounded in cited articles.

## Core Value

- Structure questions into actions, objects, candidate domains, and uncertainties.
- Find article evidence through both keyword and vector retrieval.
- Support an in-memory vector store for local verification and a Qdrant HTTP provider.
- When citations are unavailable, ask for further information instead of giving a definitive answer.
- Record the source snapshot version and indexing time for every answer.
- Support date-based search, article history, and comparison with previous articles.
- Date-based search uses articles with effective-date metadata; the end date is inclusive under the ingestion storage policy.
- Ingest local Markdown law files to create a new snapshot and search index.
- Normalize repeated upstream Markdown article numbers into unique article numbers during ingestion.
- When the same law/article is ingested again, close the previous current article and link the new article to its history.
- Correlate search logs and agent traces by requestId for reproducibility and inspection, without storing full questions or sensitive patterns in operational logs.

## Users

- Export and compliance staff
- Legal support staff
- Staff reviewing technology transfers or research and development

## Non-Goals

- Legal advice or final determinations on permission or illegality
- General-purpose search covering every area of law
- User authorization systems and organization-specific approval workflows
- Fully automated external law synchronization

## Main Screens

- Question input
- Research results
- Cited article list
- Expand/collapse controls for long cited article bodies
- Article history and comparison
- Administrative search logs and processing traces; diagnostic panels are hidden from ordinary result screens

## Main APIs

- `POST /api/ask`
- `GET /api/laws`
- `GET /api/articles/{id}`
- `GET /api/articles/{id}/history`
- `GET /api/articles/{id}/diff`
- `GET /api/admin/status`
- `POST /api/admin/ingest-local`
- `POST /api/admin/reindex`
- `GET /api/admin/ingestion-runs`
- `GET /api/admin/search-logs`
- `GET /api/admin/agent-traces`
