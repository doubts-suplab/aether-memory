# Session Log — Aether Memory

> Rolling log of working sessions. Newest first.

## Session 1 — Phase 4 Kubernetes + Helm
- Authored the Helm chart `memory-infra/helm/aether-memory` (Chart/values/.helmignore + templates: _helpers, deployment, service, hpa, configmap, serviceaccount, NOTES).
- Config → ConfigMap → env; DB creds from an existing Secret (never templated). Hardened pod (non-root, read-only rootfs, dropped caps, SA token off), probes, resources, HPA toggle, Prometheus annotations.
- Added `.github/workflows/helm-release.yml` — lint + template on chart PRs; package + `helm push` OCI to GHCR on `v*` tags.
- Docs synced (roadmap/progress/README/index.html/architecture/CLAUDE). All planned phases 0–4 complete.

## Session 1 — Phase 3 Governance & Policy
- Retention purge: `LifecycleResult` gained `purgedCount`; `PolicyAwareMemoryLifecycleService.purge()` deletes archived rows past per-tenant `retentionDays`; `.purged` metric; retention-days config.
- Policy change audit: `policy_change_audit` (V007), `PolicyChangeEntry` + `MemoryPolicyAuditStore`/`JdbcMemoryPolicyAuditStore`; controller records on PUT + `GET …/memory-policy/audit`.
- GDPR: `MemoryGovernancePort`/`JdbcMemoryGovernanceService` (@Transactional erasure across active+archive, tenant erase also clears federation_audit; `gdpr_erasure_audit` V008 proof); `ErasureScope`/`ErasureAuditEntry`; `MemoryGovernanceController` (DELETE team, DELETE tenant, GET export, GET erasures).
- Tests: GovernanceDomainTest, JdbcMemoryGovernanceServiceIT, JdbcMemoryPolicyAuditStoreIT, lifecycle purge scenario, scheduler purge metric. 60 unit tests green; verified build before push.
- Docs synced (roadmap/progress/README/index.html/architecture/CLAUDE).

## Session 1 — Phase 2 Federation
- Added federation gateway (`MemoryFederationGateway`/`DefaultMemoryFederationGateway`): local search + peer fan-out, dedupe by (provenance, summary), rank by strength, clamp, audit.
- Outbound `FederationPeerClient`/`HttpFederationPeerClient` calls peers at `…/federation/query?localOnly=true` (2s/5s timeouts, resilient to failures); controller sets `fanOut = !localOnly` to prevent recursion.
- Federation audit: `FederationAuditEntry` + `FederationAuditStore`/`JdbcFederationAuditStore`, `federation_audit` table (V005).
- Per-origin `FederationRateLimiter` (fixed window, injectable clock) → 429 in controller.
- Per-tenant redaction depth: `MemoryPolicy.federationMaxSummaryLength` (V006) applied in `DefaultMemoryFederationService`; `FederatedMemory.from(..., maxLength)` overload.
- Tests: FederationRateLimiterTest, DefaultMemoryFederationGatewayTest, JdbcFederationAuditStoreIT; updated federation service + policy tests for new signatures.
- Docs synced (roadmap/progress/README/index.html/architecture). Pushed to branch (PR #1) without local build per request.

## Session 1 — Phase 1 Shared Memory Engine
- Added `SharedMemoryStore.contribute(memoryId, scope, increment)` → `Optional<SharedMemory>`; implemented atomically in `PGVectorSharedMemoryStore` (`UPDATE … RETURNING`, strength capped via `LEAST(1.0, …)`).
- Added `GET …/memories/search?q=` (semantic `findSimilar`, reinforced on read) and `POST …/memories/{id}/contribute` (404 when absent) to `SharedMemoryController`.
- Reinforcement increment now resolved from the tenant `MemoryPolicy` across search, list-by-type, and contribute.
- Extended `PGVectorSharedMemoryStoreIT` with 4 contribute scenarios; updated federation test fake for the new port method. 45 unit tests green.
- Synced docs: roadmap (P1 ✅), progress (P1 section), README + index.html API tables, architecture flows.
- PR #1 opened for the branch (scaffold + Phase 1).

## Session 1 — Phase 0 Scaffold
- Bootstrapped the standalone `aether-memory` platform mirroring `aether-core`'s structure and quality bar.
- Created 4 modules: `memory-domain`, `memory-engine`, `memory-api`, `memory-infra`.
- Domain: SharedMemory, MemoryScope, MemoryVisibility, MemoryType, MemoryPolicy, FederationQuery, FederatedMemory + 4 ports.
- Engine: PGVectorSharedMemoryStore, SharedEmbeddingService, JdbcMemoryPolicyStore, PolicyAwareMemoryLifecycleService, DefaultMemoryFederationService.
- API: AetherMemoryApplication (8083), SharedMemory/Federation/Policy controllers, lifecycle scheduler + config, application.yml, Dockerfile.
- Infra: Flyway V001–V004, docker-compose, k8s (namespace/deployment/service+HPA).
- Tests: 45 unit tests green (`mvn test`); Testcontainers ITs authored for CI.
- Docs: CLAUDE.md, README, aether.manifest.yaml, docs/{index.html, architecture.md, roadmap.md, progress.md}, .claude/{memory,agents}.
- CI: ci.yml, quality-gate.yml, docker-build.yml (SHA-pinned actions, OIDC).
- Key decisions logged as ADR-0001..0006 in decisions.md.
