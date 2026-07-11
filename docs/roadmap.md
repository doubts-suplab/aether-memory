# Aether Memory — Development Roadmap

> **Scope:** This roadmap covers Aether Memory only.
> For the ecosystem roadmap see [suplab/aether](https://github.com/suplab/aether).

---

## Phase 0 — Scaffold ✅

**Goal:** Standalone platform bootstrapped. Independent Maven multi-module, Spring Boot 3.3.5, all golden rules enforced, ecosystem relationship established.

| Deliverable | Status |
|---|---|
| Independent parent POM (`aether-memory-parent`) | ✅ |
| 4 Maven modules: memory-domain, memory-engine, memory-api, memory-infra | ✅ |
| Domain model: SharedMemory, MemoryScope, MemoryVisibility, MemoryType, MemoryPolicy, FederationQuery, FederatedMemory | ✅ |
| Port interfaces: SharedMemoryStore, MemoryPolicyStore, MemoryFederationPort, MemoryLifecyclePort | ✅ |
| `PGVectorSharedMemoryStore` adapter (reinforce-on-read, federatable fan-out) | ✅ |
| `SharedEmbeddingService` (Ollama all-MiniLM-L6-v2, 384-dim, graceful fallback) | ✅ |
| `JdbcMemoryPolicyStore` (per-tenant policy, defaults when unset) | ✅ |
| `PolicyAwareMemoryLifecycleService` (per-tenant decay + archive) | ✅ |
| `DefaultMemoryFederationService` (privacy-preserving projection) | ✅ |
| REST: SharedMemory, Federation, Policy controllers + lifecycle scheduler | ✅ |
| Flyway migrations V001–V004 | ✅ |
| Docker Compose + Kubernetes manifests | ✅ |
| GitHub Actions CI + quality-gate + docker-build | ✅ |
| CLAUDE.md + .claude/memory/ + .claude/agents/ | ✅ |
| Docs: README, index.html, architecture.md, roadmap.md, progress.md | ✅ |

---

## Phase 1 — Shared Memory Engine ✅

**Goal:** Team memory fully operational with semantic retrieval, shared reinforcement, and contributor tracking under integration tests.

| Deliverable | Status |
|---|---|
| Semantic `findSimilar` retrieval endpoint (`GET …/memories/search?q=`) | ✅ |
| `contribute()` API — record additional contributors (`POST …/memories/{id}/contribute`) | ✅ |
| Atomic `SharedMemoryStore.contribute` (UPDATE … RETURNING, capped at 1.0) | ✅ |
| Testcontainers coverage for store contribute paths (found / capped / unknown / cross-team) | ✅ |
| Reinforcement increment sourced from tenant policy end-to-end (search, list, contribute) | ✅ |

---

## Phase 2 — Federation

**Goal:** Cross-instance federation hardened — outbound federation adapter, rate limiting, and audit.

| Deliverable | Status |
|---|---|
| Outbound federation client (query remote Memory instances) | ⏳ |
| Federation audit log (who queried what, when) | ⏳ |
| Per-origin rate limiting on `/federation/query` | ⏳ |
| Configurable redaction depth per tenant | ⏳ |

---

## Phase 3 — Governance & Policy UI

**Goal:** Full per-tenant governance surface and retention enforcement.

| Deliverable | Status |
|---|---|
| Retention-window purge of archived memories | ⏳ |
| Policy validation + change audit | ⏳ |
| GDPR erasure across active + archive tables | ⏳ |
| Bulk export API | ⏳ |

---

## Phase 4 — Kubernetes + Helm

**Goal:** Production-ready deployment.

| Deliverable | Status |
|---|---|
| Multi-stage Dockerfile (Temurin 21 JRE, non-root uid 1000) | ✅ (scaffolded) |
| Helm chart `memory-infra/helm/aether-memory/` | ⏳ |
| HPA (min 2, max 8 replicas) | ✅ (manifest) |
| Docker build + Helm release workflows | ⏳ |
