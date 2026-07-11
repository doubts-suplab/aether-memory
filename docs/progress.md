# Aether Memory — Progress Tracker

> **Scope:** This tracker covers **Aether Memory** (`suplab/aether-memory`) only.
> For ecosystem progress see [suplab/aether](https://github.com/suplab/aether).

---

**Active Phase:** Phase 0 — Scaffold ✅ (complete)

| Phase | Name | Status | Sessions |
|---|---|---|---|
| 0 | Scaffold | ✅ Complete | 1 |
| 1 | Shared Memory Engine | ⏳ Planned | — |
| 2 | Federation | ⏳ Planned | — |
| 3 | Governance & Policy | ⏳ Planned | — |
| 4 | Kubernetes + Helm | ⏳ Planned | — |

---

## Phase 0 — Scaffold ✅

**Commit:** `feat(memory): scaffold Aether Memory — shared team memory platform`

### What was done

**Maven project:**
- `pom.xml` — independent parent POM (`aether-memory-parent`), Spring Boot 3.3.5 BOM, Java 21, `--enable-preview`, `-parameters` flags, JaCoCo config (pluginManagement, mirroring Core)
- 4 modules: `memory-domain`, `memory-engine`, `memory-api`, `memory-infra`

**`memory-domain` — pure domain (no Spring):**
- `SharedMemory` record: team-owned memory with strength, accessCount, contributorCount, visibility; `create()`, `reinforce(increment)`, `contribute(increment)`, `withVisibility()`
- `MemoryScope` record: `(tenantId, teamId)` ownership + isolation key
- `MemoryVisibility` enum: PRIVATE, TENANT, FEDERATED (`isFederatable()`)
- `MemoryType` enum: EPISODIC, SEMANTIC, PROCEDURAL, EMOTIONAL
- `MemoryPolicy` record: per-tenant decay/reinforcement/archive/retention/federation; `defaults()`
- `FederationQuery` + `FederatedMemory` records: privacy-preserving projection (`from()`, `MAX_SUMMARY_LENGTH=280`)
- Ports: `SharedMemoryStore`, `MemoryPolicyStore`, `MemoryFederationPort`, `MemoryLifecyclePort`

**`memory-engine` — adapters + services:**
- `PGVectorSharedMemoryStore`: cosine similarity (`<=> :query::vector`), explicit column lists, `NamedParameterJdbcTemplate`, `ON CONFLICT` upsert, reinforce-on-read, `findFederatable` (joins `memory_policies`)
- `SharedEmbeddingService`: Ollama REST client, 384-dim, zero-vector fallback on error
- `JdbcMemoryPolicyStore`: per-tenant policy, defaults when unset, upsert
- `PolicyAwareMemoryLifecycleService`: set-based decay + archive with per-tenant `COALESCE` overrides, atomic move-to-archive CTE
- `DefaultMemoryFederationService`: privacy-preserving projection, limit clamp, optional embedding

**`memory-api` — Spring Boot application:**
- `AetherMemoryApplication`: port 8083, `scanBasePackages = "com.suplab.aether.memory"`
- `SharedMemoryController`: POST store, GET by type (reinforced), GET count, DELETE — all tenant+team scoped
- `MemoryFederationController`: `POST /api/v1/federation/query`
- `MemoryPolicyController`: `GET/PUT /api/v1/tenants/{tenantId}/memory-policy`
- `MemoryLifecycleScheduler` + `MemoryLifecycleConfig`: `@Scheduled` decay, Micrometer metrics, opt-out flag
- `MemoryApiConfig`: wires all engine beans via constructor injection; embedding `@ConditionalOnProperty`
- `application.yml`: port 8083, Flyway enabled, Ollama + lifecycle config; `Dockerfile` (multi-stage, non-root)

**`memory-infra` — infrastructure:**
- Flyway migrations V001–V004 (shared_memories, pgvector embeddings, memory_policies, archive)
- `docker/docker-compose.yml`: postgres-memory (port 5434) + aether-memory (port 8083)
- `k8s/`: namespace, deployment (probes, non-root, read-only fs), service + HPA + ConfigMap + Secret template

**Tests — 45 unit tests green:**
- `SharedMemoryTest` (17), `MemoryPolicyTest` (8), `FederatedMemoryTest` (6), `MemoryScopeAndVisibilityTest` (8)
- `DefaultMemoryFederationServiceTest` (4): projection, limit clamp, type filter, empty result
- `MemoryLifecycleSchedulerTest` (2): metric recording, counter-accumulation vs gauge-latest
- Testcontainers ITs (CI, `pgvector/pgvector:pg16`): `PGVectorSharedMemoryStoreIT` (10), `JdbcMemoryPolicyStoreIT` (4), `PolicyAwareMemoryLifecycleServiceIT` (4)

**`.claude/` setup:**
- Specialist agent definitions + memory files seeded with Memory context
- `CLAUDE.md` project brief, `aether.manifest.yaml`

**Docs:**
- `README.md`, `docs/index.html`, `docs/architecture.md`, `docs/roadmap.md`, `docs/progress.md`
- GitHub Actions: `ci.yml`, `quality-gate.yml`, `docker-build.yml`
