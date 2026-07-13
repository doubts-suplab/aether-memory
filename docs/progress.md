# Aether Memory — Progress Tracker

> **Scope:** This tracker covers **Aether Memory** (`suplab/aether-memory`) only.
> For ecosystem progress see [suplab/aether](https://github.com/suplab/aether).

---

**Active Phase:** Phase 4 — Kubernetes + Helm (next)

| Phase | Name | Status | Sessions |
|---|---|---|---|
| 0 | Scaffold | ✅ Complete | 1 |
| 1 | Shared Memory Engine | ✅ Complete | 1 |
| 2 | Federation | ✅ Complete | 1 |
| 3 | Governance & Policy | ✅ Complete | 1 |
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

---

## Phase 1 — Shared Memory Engine ✅

**Commit:** `feat(memory): Phase 1 — semantic search + contribute API with policy-sourced reinforcement`

### What was done

**Port (`memory-domain`):**
- `SharedMemoryStore.contribute(memoryId, scope, increment)` → `Optional<SharedMemory>` — the shared-reinforcement write path

**Engine (`memory-engine`):**
- `PGVectorSharedMemoryStore.contribute` — atomic `UPDATE … SET contributor_count = contributor_count + 1, strength = LEAST(1.0, strength + :increment), last_accessed_at = NOW() … RETURNING …`; scoped by tenant + team; returns empty when nothing matches

**API (`memory-api`):**
- `GET /api/v1/tenants/{t}/teams/{team}/memories/search?q=&limit=` — semantic retrieval via `findSimilar`, reinforced on read, embedding-optional (zero-vector fallback)
- `POST /api/v1/tenants/{t}/teams/{team}/memories/{memoryId}/contribute` — records an additional contributor; 200 with updated view, 404 when absent
- Reinforcement increment resolved from the tenant's `MemoryPolicy` end-to-end (search, list-by-type, contribute)

**Tests:**
- Unit suite unchanged and green (45 tests)
- `PGVectorSharedMemoryStoreIT` extended: `contribute` increments count + strength, caps at 1.0, returns empty for unknown id, and does not cross the team boundary (Testcontainers, CI)

### Files changed: 4 (+ docs)

---

## Phase 2 — Federation ✅

**Commit:** `feat(memory): Phase 2 — federation gateway, peer fan-out, audit, rate limiting, redaction depth`

### What was done

**Domain (`memory-domain`):**
- `FederationAuditEntry` record (`record()` factory) — audits the *query*, never matched-memory identity
- Ports: `FederationAuditStore`, `FederationPeerClient` (outbound), `MemoryFederationGateway` (orchestration)
- `MemoryPolicy` gained `federationMaxSummaryLength` (per-tenant redaction depth, 1..280); `FederatedMemory.from(memory, provenance, maxLength)` overload

**Engine (`memory-engine`):**
- `JdbcFederationAuditStore` — persist + `recentFor`
- `HttpFederationPeerClient` — calls peer `…/federation/query?localOnly=true` with 2s/5s timeouts; failures degrade to empty (one bad peer never fails the query)
- `FederationRateLimiter` — per-origin fixed-window limiter, injectable clock, thread-safe
- `DefaultMemoryFederationService` — now applies the source tenant's redaction depth per result
- `DefaultMemoryFederationGateway` — local search + peer fan-out, dedupe by (provenance, summary) keeping strongest, rank by strength, clamp to limit, record audit

**API (`memory-api`):**
- `MemoryFederationController` — injects gateway + rate limiter; `?localOnly=true` for inbound peer calls (no recursion); 429 on rate-limit; 400 on bad input
- `MemoryPolicyController` — read/replace `federationMaxSummaryLength`
- `MemoryApiConfig` — beans: audit store, peer client, gateway (peers from `aether.memory.federation.peers`), rate limiter (capacity/window config)
- `application.yml` — `aether.memory.federation.{peers,rate-limit.*}`

**Migrations:** `V005__create_federation_audit.sql`, `V006__add_federation_redaction_depth.sql`

**Tests:**
- Unit: `FederationRateLimiterTest` (4), `DefaultMemoryFederationGatewayTest` (5), `DefaultMemoryFederationServiceTest` (+redaction), `MemoryPolicyTest` (+redaction validation)
- IT (Testcontainers): `JdbcFederationAuditStoreIT` (4); `JdbcMemoryPolicyStoreIT` updated for the new column

### Files changed: ~22

---

## Phase 3 — Governance & Policy ✅

**Commit:** `feat(memory): Phase 3 — retention purge, policy audit, GDPR erasure, bulk export`

### What was done

**Retention purge (lifecycle):**
- `MemoryLifecyclePort.LifecycleResult` gained `purgedCount`; `PolicyAwareMemoryLifecycleService` adds a set-based `purge()` step — deletes archived rows past each tenant's `retentionDays` (`COALESCE` to a configurable default)
- `MemoryLifecycleScheduler` records `aether.memory.shared.purged`; `aether.memory.lifecycle.retention-days` config

**Policy change audit:**
- `policy_change_audit` (V007) + `PolicyChangeEntry` + `MemoryPolicyAuditStore` / `JdbcMemoryPolicyAuditStore`
- `MemoryPolicyController` records a snapshot on every `PUT` and serves `GET …/memory-policy/audit`

**GDPR right-to-erasure + export:**
- `MemoryGovernancePort` (+ nested `ErasureResult`) / `JdbcMemoryGovernanceService` — `@Transactional` erasure across `shared_memories` + `shared_memories_archive` (tenant erasure also clears `federation_audit`), with a `gdpr_erasure_audit` (V008) proof-of-erasure written in the same transaction
- `ErasureScope`, `ErasureAuditEntry` domain types
- `MemoryGovernanceController`: `DELETE …/teams/{teamId}` (team erase), `DELETE …/tenants/{tenantId}` (tenant erase), `GET …/teams/{teamId}/export` (portability), `GET …/erasures` (audit)

**Wiring:** `MemoryApiConfig` beans (policy-audit store, governance service); lifecycle bean gains retention-days.

**Tests:**
- Unit: `GovernanceDomainTest` (4); scheduler test updated for the purge metric
- IT (Testcontainers): `JdbcMemoryGovernanceServiceIT` (4 — team/tenant erasure, isolation, export), `JdbcMemoryPolicyAuditStoreIT` (3), lifecycle purge scenario

**Migrations:** `V007__create_policy_change_audit.sql`, `V008__create_gdpr_erasure_audit.sql`

### Files changed: ~20
