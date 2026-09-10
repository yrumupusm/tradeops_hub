# Constraints

[한국어](../ko/constraints.md) | [English](../en/constraints.md)

- Do not answer without retrieval.
- `status=OK` must have at least one cited article.
- Do not make definitive statements as legal advice.
- Do not use real internal data, internal URLs, or API keys.
- Do not store full questions in operational logs. Search logs retain a hash, length, and incomplete preview with sensitive patterns replaced. The `asOf` value for date-based searches is not question text, so store it as a separate date field for auditing and reproduction.
- Cited articles must be directly accessible alongside the answer. Keep long article bodies collapsed by default, with expand and collapse controls.
- Do not expose confidence scores or wording such as “검증됨” that suggests a final determination on the user-facing screen.
- The current scope is RAG for a limited legal domain, date-based search, article history, revision comparison, and administrative inspection.
