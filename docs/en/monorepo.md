# Monorepo ownership and migration

[한국어](../ko/monorepo.md)

Since 2026-09-11 TradeOps Hub owns `frontend/` (integrated Korean UI), `backend/` (Hub authentication/BIS/audit/API mediation) and `services/law-search/` (law ingestion, retrieval, embeddings and answers). Use task branches and merge verified changes into main; services do not have permanent branches. Processes, configuration, databases and deployment remain separate.

Law main `2f902ca2e2fd73b3b8b1959938eae7716991134b` and all 41 reachable commits were imported by `git subtree add` without squashing. Import commit `b52bf2170a3896a5f49d1a0fdf3a8e6accba111d` has the law revision as a parent. Before adaptations, both original and subtree hashes were `e93c93d5ec8a9c9cd4f05a957c2af208790c38ab`. Original hashes remain reachable. Older commits use the former root paths: inspect them with `git log 2f902ca -- <old-path>`.

The original checkout remains intact. Ignored configuration, evidence, law sources, databases and vectors are not imported into Git. Both repository bundles are backed up under ignored artifacts. Existing processes need not restart for a source-tree change. Configure the new service directory before its next start; do not delete the old directory while a process or configured source path still refers to it.

## Runtime and verification

On 2026-09-11 the former GitHub repository was made private and the law runtime was restarted from the monorepo package with schema validation. Read-only checks confirmed unchanged 44 laws, 4112 articles, 4431 indexed articles and snapshot metadata. The existing stale index remains unchanged. The Markdown source directory is independent of the old checkout. The old checkout is no longer needed by the law process, but its archive move failed due to a directory lock; automatic approval review rejected further cleanup and cleanup-script preparation. It remains intact on disk.

Root `.env` belongs to Hub; `services/law-search/.env` belongs to law. Preserve datasource URL/driver/credentials/schema mode, providers, source paths and vector collection. Resolve relative source paths against the previous working directory. Existing PostgreSQL data must never use create-drop.

Law Compose pins project name `law-research-assistant-spring`, retaining volumes `law-research-assistant-spring_postgres_data` and `law-research-assistant-spring_qdrant_data`. No volume is recreated or copied during this migration. Hub Compose remains separate. Do not use `down -v`. Source Markdown repositories are data inputs, not application repositories to merge.

Run root `scripts/verify-monorepo.ps1 -MavenPath <mvn.cmd>`. It runs Hub verification, law JavaScript syntax checks and the full law Maven suite with explicit temporary H2/mock/inmemory settings and no local `.env` import. Live PostgreSQL/provider checks remain separate. Service-relative scripts/docs run from `services/law-search/`. Historical separate-repository handoff notes are superseded by this guide.

## Retiring the old GitHub repository

Once imported history is pushed and verified, the old remote is not required for application source history. Deletion is not part of this task. Prefer archiving it with a migration link first. Git does not migrate issues, PR discussions, release attachments, Actions artifacts, secrets, branch protection, stars, separate wiki history or deployment settings. Preserve what is needed and update external links before deletion. Remote deletion does not delete local DB volumes; deleting the old local directory can break processes/configuration/source paths. Treat these separately.
