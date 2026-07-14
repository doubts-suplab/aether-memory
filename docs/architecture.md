# Aether Memory — Architecture

> **Scope:** This document covers **Aether Memory** (`suplab/aether-memory`) only.
> For the ecosystem-wide view see [suplab/aether](https://github.com/suplab/aether).

---

## 1. Purpose & Position

Aether Memory owns the **Shared Memory** capability of the Aether ecosystem: memory that belongs to a *team* or *organisation* rather than an individual. It is a **platform layer** — above the runtime (Grid) and cognitive (Core) layers, below domain products (Vault, Flow, Enterprise).

```
Domain Products  (aether-vault, aether-flow, aether-enterprise)
        ↓
Platform Layer   →  aether-memory  ← this repo
        ↓
Runtime Layer    (aether-grid)
        ↓
Cognitive Layer  (aether-core)
```

Personal memory remains owned exclusively by Aether Core. Aether Memory does **not** re-implement it — the two are complementary: Core is one mind; Memory is a shared mind for a group.

---

## 2. Module Boundaries

| Module | Package root | Responsibility |
|---|---|---|
| `memory-domain` | `com.suplab.aether.memory.domain` / `.ports` | Pure records + port interfaces. No framework. |
| `memory-engine` | `com.suplab.aether.memory.engine.*` | pgvector store, embedding, policy-aware lifecycle, federation. |
| `memory-api` | `com.suplab.aether.memory.api.*` | Spring Boot app, REST controllers, Flyway, scheduling, config. |
| `memory-infra` | — | Docker Compose, Kubernetes manifests, migration reference copies. |

Dependency direction is strictly inward: `memory-api → memory-engine → memory-domain`. The domain never depends on Spring.

---

## 3. Domain Model

```
SharedMemory
  id, tenantId, teamId, type, content, visibility,
  strength (0–1), accessCount, contributorCount, createdAt, lastAccessedAt
  ├── reinforce(increment)  → +strength (capped), +accessCount        (retrieval)
  ├── contribute(increment) → +contributorCount, +strength            (new contributor)
  └── withVisibility(v)      → promote/demote reach

MemoryScope        = (tenantId, teamId)   — the ownership + isolation key
MemoryVisibility   = PRIVATE | TENANT | FEDERATED
MemoryType         = EPISODIC | SEMANTIC | PROCEDURAL | EMOTIONAL
MemoryPolicy       = per-tenant (decayRate, decayAfterDays, reinforcementIncrement,
                                 archiveThreshold, retentionDays, federationEnabled)
FederationQuery    = (originTenantId, type?, queryText, limit)
FederatedMemory    = (type, summary≤280, strength, provenance)   — privacy-preserving projection
```

### Ports

| Port | Implementation | Purpose |
|---|---|---|
| `SharedMemoryStore` | `PGVectorSharedMemoryStore` | Persist/retrieve team memory; reinforce on read; atomic `contribute`; federatable fan-out |
| `MemoryPolicyStore` | `JdbcMemoryPolicyStore` | Resolve/save per-tenant policy (defaults when unset) |
| `MemoryFederationPort` | `DefaultMemoryFederationService` | Privacy-preserving *local* federated projection (source-tenant redaction depth) |
| `MemoryFederationGateway` | `DefaultMemoryFederationGateway` | Orchestrates local search + peer fan-out, dedupe/rank, audit |
| `FederationPeerClient` | `HttpFederationPeerClient` | Outbound query to peer instances (`?localOnly=true`, resilient) |
| `FederationAuditStore` | `JdbcFederationAuditStore` | Persist per-query audit records |
| `MemoryLifecyclePort` | `PolicyAwareMemoryLifecycleService` | Per-tenant decay + archive + retention purge |
| `MemoryPolicyAuditStore` | `JdbcMemoryPolicyAuditStore` | Policy-change snapshots (governance audit) |
| `MemoryGovernancePort` | `JdbcMemoryGovernanceService` | Transactional GDPR erasure (active + archive), export, erasure audit |

---

## 4. Data Model (PostgreSQL 16 + pgvector)

| Migration | Object | Notes |
|---|---|---|
| `V001` | `shared_memories` | Team-scoped memory; indexes on `(tenant_id, team_id)`, `(…, memory_type)`, partial index on `visibility='FEDERATED'` |
| `V002` | `shared_memories.embedding vector(384)` | IVFFlat cosine index (`lists=100`) |
| `V003` | `memory_policies` | One configurable policy per tenant; overrides only |
| `V004` | `shared_memories_archive` | Faded memories moved here (keeps embedding for restore) |
| `V005` | `federation_audit` | One row per federation query (origin, query, count, localOnly) |
| `V006` | `memory_policies.federation_max_summary_length` | Per-tenant redaction depth (1..280) |
| `V007` | `policy_change_audit` | Full policy snapshot per change (governance audit) |
| `V008` | `gdpr_erasure_audit` | Proof-of-erasure per GDPR request (team/tenant scope) |

All embeddings are 384-dim (all-MiniLM-L6-v2), consistent across the ecosystem.

---

## 5. Key Flows

### 5.1 Store & retrieve (team-scoped)
1. `POST …/teams/{teamId}/memories` → embed content via Ollama → `SharedMemoryStore.save` (UPSERT).
2. `GET …/teams/{teamId}/memories?type=` → `findByType` orders by strength, **reinforces on read** using the tenant's `reinforcementIncrement`, persists the reinforced state.
3. `GET …/teams/{teamId}/memories/search?q=` → embed the query → `findSimilar` (cosine), reinforced on read (same policy-sourced increment).
4. `POST …/teams/{teamId}/memories/{id}/contribute` → `SharedMemoryStore.contribute` runs a single atomic `UPDATE … SET contributor_count = contributor_count + 1, strength = LEAST(1.0, strength + increment), last_accessed_at = NOW() … RETURNING …`, scoped by tenant + team; returns 404 when nothing matches.

### 5.2 Federation (privacy-preserving, distributed)
1. `POST /api/v1/federation/query` → `MemoryFederationController` enforces the per-origin `FederationRateLimiter` (429 when exceeded).
2. `DefaultMemoryFederationGateway.search(query, fanOut)` runs the local `DefaultMemoryFederationService` (embeds `queryText`; `findFederatable` joins `memory_policies` and returns only `FEDERATED` rows in `federation_enabled` tenants; each result truncated to the source tenant's redaction depth).
3. When `fanOut` (default), the gateway forwards the query to each configured peer via `HttpFederationPeerClient` (`?localOnly=true` so peers don't recurse); inbound peer calls pass `localOnly=true` → `fanOut=false`.
4. Merge → dedupe by (provenance, summary) keeping strongest → rank by strength desc → clamp to limit → record a `FederationAuditEntry`.
5. Every projection is a `FederatedMemory` (bounded summary, coarse provenance = source tenant); the overall count is clamped to `MAX_FEDERATION_LIMIT`.

### 5.3 Lifecycle (per-tenant, set-based)
1. Scheduler (`@Scheduled`, default 03:00) → `MemoryLifecyclePort.runLifecycle`.
2. **Decay**: single UPDATE, `LEFT JOIN memory_policies` with `COALESCE` to defaults, `strength -= decayRate × days_idle` beyond the grace period.
3. **Archive**: data-modifying CTE (`DELETE … RETURNING … INSERT`) moves sub-threshold rows atomically into `shared_memories_archive`.
4. **Purge**: archived rows older than each tenant's `retentionDays` (COALESCE to default) are permanently deleted.
5. Micrometer: `aether.memory.shared.decayed` / `.archived` / `.purged` counters, `.total` gauge.

### 5.4 GDPR governance
- **Erasure** (`JdbcMemoryGovernanceService`, `@Transactional`): `DELETE …/teams/{teamId}` removes a team's active + archived memories; `DELETE …/tenants/{tenantId}` removes every team plus the tenant's `federation_audit`. A `gdpr_erasure_audit` proof row is written in the same transaction.
- **Export** (`GET …/teams/{teamId}/export`): returns a team's active memories for data portability.
- **Policy audit**: each `PUT …/memory-policy` snapshots the new policy to `policy_change_audit`, served by `GET …/memory-policy/audit`.

---

## 6. Multi-Tenancy & Privacy

- Every store read/write is scoped by `tenant_id` **and** `team_id`. There is no cross-team read path.
- Federation is opt-in per tenant (`federation_enabled`, default `false`) and only ever exposes `FEDERATED` memories.
- Federated projections strip team identity, contributor identity, and raw IDs — a source tenant cannot leak *who* knows what.

---

## 7. Configuration Surface

Reads from environment variables (never hardcoded). Defaults target local Docker Compose. See `README.md` for the full table. Decay/reinforcement defaults apply only to tenants without an explicit `MemoryPolicy`.

---

## 8. Deployment

- **Local:** Docker Compose (`memory-infra/docker`) — pgvector + the app.
- **Kubernetes:** raw manifests (`memory-infra/k8s`) or the Helm chart (`memory-infra/helm/aether-memory`). The chart renders deployment, service, HPA, ConfigMap, and ServiceAccount; DB credentials are read from an **existing Secret** and never templated in. Pods run non-root (uid 1000), read-only root filesystem, all capabilities dropped, SA token unmounted, with startup/liveness/readiness probes and CPU-based autoscaling (2–8 replicas).
- **Release:** `Docker Build & Push` builds a multi-arch image to GHCR; `Helm Release` lints/templates on chart PRs and, on `v*` tags, packages and pushes the chart to `oci://ghcr.io/suplab/charts`.

## 9. Standalone Guarantee

Aether Memory has no compile-time or runtime dependency on Core or Grid. It boots, migrates, serves, and runs its lifecycle entirely on its own PostgreSQL schema (`aether_memory`).
