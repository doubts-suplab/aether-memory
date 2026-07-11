# Session Log — Aether Memory

> Rolling log of working sessions. Newest first.

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
