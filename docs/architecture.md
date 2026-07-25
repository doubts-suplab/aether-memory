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
                                 archiveThreshold, retentionDays, federationEnabled,
                                 federationSummaryChars)   — incl. redaction depth
FederationQuery    = (originTenantId, type?, queryText, limit)
FederatedMemory    = (type, summary≤280, strength, provenance)   — privacy-preserving projection
FederationAuditEvent = (originTenantId, type?, queryLabel≤120, resultCount, occurredAt)
```

### Ports

| Port | Implementation | Purpose |
|---|---|---|
| `SharedMemoryStore` | `PGVectorSharedMemoryStore` | Persist/retrieve team memory; reinforce on read; `contribute` (distinct-contributor signal); federatable fan-out |
| `MemoryPolicyStore` | `JdbcMemoryPolicyStore` | Resolve/save per-tenant policy (defaults when unset), incl. redaction depth |
| `MemoryFederationPort` | `DefaultMemoryFederationService` | Privacy-preserving cross-instance query + peer fan-out; per-owner redaction; audits every query |
| `FederationAuditStore` | `JdbcFederationAuditStore` | Append-only log of served federation queries |
| `FederationRateLimiter` | `InMemoryFederationRateLimiter` | Per-origin fixed-window throttle on `/federation/query` |
| `FederationPeerClient` | `HttpFederationPeerClient` | Outbound fan-out to configured peer instances (optional; gated by config) |
| `MemoryLifecyclePort` | `PolicyAwareMemoryLifecycleService` | Per-tenant decay + archive |

---

## 4. Data Model (PostgreSQL 16 + pgvector)

| Migration | Object | Notes |
|---|---|---|
| `V001` | `shared_memories` | Team-scoped memory; indexes on `(tenant_id, team_id)`, `(…, memory_type)`, partial index on `visibility='FEDERATED'` |
| `V002` | `shared_memories.embedding vector(384)` | IVFFlat cosine index (`lists=100`) |
| `V003` | `memory_policies` | One configurable policy per tenant; overrides only |
| `V004` | `shared_memories_archive` | Faded memories moved here (keeps embedding for restore) |
| `V005` | `federation_audit` + `memory_policies.federation_summary_chars` | Append-only federation-query audit; per-tenant redaction depth (0–280) |

All embeddings are 384-dim (all-MiniLM-L6-v2), consistent across the ecosystem.

---

## 5. Key Flows

### 5.1 Store & retrieve (team-scoped)
1. `POST …/teams/{teamId}/memories` → embed content via Ollama → `SharedMemoryStore.save` (UPSERT).
2. `GET …/teams/{teamId}/memories?type=` → `findByType` orders by strength, **reinforces on read** using the tenant's `reinforcementIncrement`, persists the reinforced state.
3. `POST …/teams/{teamId}/memories/search` (body `{"query","limit"}`) → embed the query → `findSimilar` cosine search **within the team scope**, reinforced on read by the tenant increment (semantic retrieval + policy-sourced reinforcement, end-to-end).
4. `POST …/teams/{teamId}/memories/{id}/contribute` → `SharedMemoryStore.contribute` runs a scoped `UPDATE … contributor_count + 1, strength = LEAST(1.0, …) … RETURNING …` (the shared-reinforcement signal); 404 when the memory is not in scope.

### 5.2 Federation (privacy-preserving, hardened)
1. `POST /api/v1/federation/query` → the origin is **rate-limited** (`FederationRateLimiter`, per-origin fixed window); an over-budget origin gets `429` + `Retry-After`.
2. `DefaultMemoryFederationService` embeds `queryText`; `SharedMemoryStore.findFederatable` joins `memory_policies` and returns only `FEDERATED` rows in `federation_enabled` tenants.
3. Each candidate is projected to `FederatedMemory` at its **owning tenant's redaction depth** (`MemoryPolicy.federationSummaryChars` — 0 = fully redacted), coarse provenance = source tenant, count clamped to `MAX_FEDERATION_LIMIT`. Team/contributor identity never crosses the boundary.
4. The served query is written to the append-only `federation_audit` (origin, type, bounded query label, result count) — surfaced by `GET /federation/audit`.
5. With `"includePeers": true`, `federatedFanout` also queries every configured peer via `HttpFederationPeerClient`, merges by strength, and re-clamps. Peer failures are tolerated (skipped); with no peers configured it is local-only, so Memory runs standalone.

### 5.3 Lifecycle (per-tenant, set-based)
1. Scheduler (`@Scheduled`, default 03:00) → `MemoryLifecyclePort.runLifecycle`.
2. **Decay**: single UPDATE, `LEFT JOIN memory_policies` with `COALESCE` to defaults, `strength -= decayRate × days_idle` beyond the grace period.
3. **Archive**: data-modifying CTE (`DELETE … RETURNING … INSERT`) moves sub-threshold rows atomically into `shared_memories_archive`.
4. Micrometer: `aether.memory.shared.decayed` / `.archived` counters, `.total` gauge.

---

## 6. Multi-Tenancy & Privacy

- Every store read/write is scoped by `tenant_id` **and** `team_id`. There is no cross-team read path.
- Federation is opt-in per tenant (`federation_enabled`, default `false`) and only ever exposes `FEDERATED` memories.
- Federated projections strip team identity, contributor identity, and raw IDs — a source tenant cannot leak *who* knows what.

---

## 7. Configuration Surface

Reads from environment variables (never hardcoded). Defaults target local Docker Compose. See `README.md` for the full table. Decay/reinforcement defaults apply only to tenants without an explicit `MemoryPolicy`.

---

## 8. Standalone Guarantee

Aether Memory has no compile-time or runtime dependency on Core or Grid. It boots, migrates, serves, and runs its lifecycle entirely on its own PostgreSQL schema (`aether_memory`).
