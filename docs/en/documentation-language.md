# Documentation Languages and Korean UX

[한국어](../ko/documentation-language.md) | [English](../en/documentation-language.md)

This repository follows TradeOps Hub's separate Korean/English documentation model. The references inspected on 2026-09-10 were `Documentation languages and Korean UX` in local `C:\dev\tradeops_hub\AGENTS.md` and `Documentation entry points` in `PROJECT_GUIDE.md`. The [TradeOps Hub working agreement](https://github.com/yrumupusm/tradeops_hub/blob/main/AGENTS.md) is the remote reference; its revision may differ from local working documents.

## Documentation Structure

| Category | Rule |
| --- | --- |
| Public README | Korean introduction, startup, verification, architecture, and documentation navigation |
| Public explanatory documents | Link to `docs/ko/` by default |
| Implementation/verification references | Consult `docs/en/` first |
| Working instructions | Keep `AGENTS.md` and `PROJECT_GUIDE.md` in English; these are not public-document translation pairs |
| Correspondence | Use identical relative filenames in `docs/ko/<path>` and `docs/en/<path>` |
| Entry point | Link document pairs in the [complete documentation index](../README.md) |
| Change management | Update both languages in the same commit for design/API/operational changes |

TradeOps Hub's `tasks/todo.md` is also English working status. Creating that file or a new task-management scheme in this repository is outside this translation scope. Preserve the root `handoff.md` as existing working history, separate from public design documentation.

## Language Parity Rules

- Both languages describe one design and implementation state. Do not maintain language-specific designs.
- Preserve constraints, examples, completion status, and the historical context of ADR decisions in translation.
- Add or remove pages in both languages together and update the complete index.
- Update the related Korean and English documents in the same commit when code, contracts, or operational procedures change.
- If content disagrees, inspect code, tests, and the user's latest authorized requirements and fix both versions. English working references do not override user intent.
- Do not describe proposed features as implemented. Preserve historical baseline commits, observed counts, and unimplemented items in the TradeOps handoff.
- Check relative paths and opposite-language links together. Old root document paths retain migration notices only; maintain the body in language-specific paths.

## Korean UX Rules

- Use natural Korean for navigation, input guidance, errors, notices, and CSV headings.
- Format dates/numbers for Korean readers and identify the time zone, such as `Asia/Seoul`.
- Preserve code identifiers, API paths, configuration names, and standard names such as C4/ADR.
- Preserve original names and data, including law titles, article numbers, and source text. Do not automatically translate or arbitrarily polish raw records.
- English documents may quote exact Korean UI copy, evaluation questions, and source-format examples. English documentation does not imply an English UI.
- Translating the display of a status code is different from editing legal source text.

## Applied Scope and Verification

All public design, API, operations, evaluation, integration, and ADR documents are maintained as corresponding pages under `docs/ko/` and `docs/en/`. The root README guides readers to Korean documents; working instructions and verification scripts reference English documents. Unless otherwise stated, run commands from the repository root.

Documentation checks cover page pairing, language-switch links, local links, corresponding example code blocks, corrupted Korean encoding, and existing guidance/verification-script contracts. Automated checks do not guarantee semantic translation parity; directly compare constraints and implementation status in both versions. This structural change itself does not alter API, retrieval, or embedding behavior.
