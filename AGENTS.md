# TradeOps Hub Working Agreement

## Purpose

TradeOps Hub is an independently implemented application for internal
trade-data operations. It must not contain copied company source code, company
data, internal names, URLs, credentials, screenshots, or commit history.

## Required workflow

### Documentation languages and Korean UX

- Read `PROJECT_GUIDE.md` and `docs/en/` for implementation and verification. Public README links and human-facing documentation default to `docs/ko/`.
- `docs/ko/<path>` and `docs/en/<path>` describe one design at the same revision. Keep the same relative filenames and maintain the correspondence index in `docs/README.md`. Do not maintain separate language-specific designs.
- When changing design, APIs, workflows, invariants or operations, update both language versions in the same commit. Preserve constraints, examples, status and historical ADR context in translation. New or removed pages must be paired.
- If the two versions disagree, inspect code, tests and the user's latest authorized requirements, resolve the discrepancy and update both; English is the working reference, not an override of user intent. Disclose unimplemented requirements rather than describing them as complete.
- Write `README.md` for Korean readers. Keep `AGENTS.md`, `PROJECT_GUIDE.md` and `tasks/todo.md` as English working instructions/status; they are not duplicate public design documents.
- Design UI/UX for Korean users regardless of documentation language. Use natural Korean for navigation, forms, errors, notices and CSV headings; show dates/numbers in Korean conventions and make the time zone clear (scheduled collection uses Asia/Seoul). Preserve source names and original data; do not translate raw records automatically.
- Keep code identifiers, API paths, configuration names and standard names such as C4/ADR in their original form. English working documents may quote exact Korean UI labels.

1. Read `PROJECT_GUIDE.md` and `tasks/todo.md` before changing behaviour.
2. For non-trivial work, update the checklist in `tasks/todo.md` before coding.
3. Keep controllers thin; put business rules in application services and data
   access in repositories.
4. Update the closest contract or design document when a public API, schema,
   workflow, or invariant changes.
5. Run the narrowest relevant test and then the appropriate verification gate
   before reporting a change complete.
6. Check `git status --short` before and after edits. Never stage `.env`, build
   output, generated evidence, or real data.

## Product invariants

- Every imported row has a source file, import-run identifier, validation
  outcome, and creation timestamp.
- A completed import run has immutable aggregate counts. Failed rows remain
  reviewable with a safe error code; they are never silently discarded.
- Screening decisions record the actor, timestamp, matched entity, score, and
  disposition. A match is not automatically a compliance conclusion.
- Authorization is enforced by the backend, not only hidden in the frontend.
- Mutating requests are validated and auditable with a correlation ID.
- Health responses disclose component state but never credentials, connection
  strings, stack traces, or raw uploaded data.
- All committed fixture data is explicitly fictional and reproducible from a
  documented seed.

## Safety and privacy

- Public BIS DPL and Entity List data and official source URLs are authorized
  for collection and runtime use. Do not copy company-internal data, terminology,
  account information or secrets. Never commit collected public records or exports.
- Store configuration only in environment variables. Keep `.env` ignored and
  document variable names in `.env.example`.
- Log identifiers, row counts, and bounded safe error codes rather than raw
  uploaded rows or full personal information.
- Do not label the system as a compliance or sanctions decision engine.
  Identify BIS public source data and its collection time accurately; use fictional
  records only for reproducible tests.

## Verification model

- Unit tests prove business rules and authorization decisions.
- Integration tests prove API contracts, persistence, and validation errors.
- `harness/scenarios.json` defines fixed end-to-end acceptance scenarios.
- `scripts/verify-local.ps1` is the server-free gate.
- `scripts/verify-runtime.ps1` is the running-service smoke gate and writes
  non-sensitive evidence under `artifacts/`.
- `scripts/verify-final.ps1` combines readiness, runtime, scenario, and
  evidence checks when those components are available.

## Git discipline

- Keep commits small and verified. Include behavior changes with the test or
  harness case that proves them.
- Do not force-push or rewrite shared history without explicit instruction.
- Treat generated artifacts as ignored unless they are intentionally part of a
  documented public release.
