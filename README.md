# Æ Aether Memory

> Shared team and organisational memory — the platform layer that generalises personal cognition to groups, with privacy-preserving federation across instances.

**Aether Memory** is the shared-memory platform of the [Aether ecosystem](https://github.com/suplab/aether). Where [Aether Core](https://github.com/suplab/aether-core) owns *personal* memory for an individual, Aether Memory owns memory that belongs to a **team** or **organisation**: knowledge many people rely on, reinforced by collective access, governed by per-tenant policy, and shareable across instances through a privacy-preserving federation API.

**Ecosystem position:** Aether Memory is a **platform layer** — it sits above the runtime (Grid) and cognitive (Core) layers and is consumed by higher-level products. It runs standalone; Core and Grid are not required to be present.

---

## Quick Start

```bash
cd memory-infra/docker && docker compose up -d
cd ../.. && mvn spring-boot:run -pl memory-api
# Memory API: http://localhost:8083
# Health:     http://localhost:8083/actuator/health
```

## Modules

| Module | Purpose |
|---|---|
| `memory-domain` | Domain types: SharedMemory, MemoryScope, MemoryVisibility, MemoryPolicy, FederatedMemory, port interfaces |
| `memory-engine` | pgvector shared-memory store, Ollama embedding, policy-aware decay/archive lifecycle, federation service |
| `memory-api` | Spring Boot REST API (port 8083) + Flyway migrations |
| `memory-infra` | Docker Compose, Kubernetes manifests, standalone Flyway migrations |

## Key API Endpoints

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/v1/tenants/{tenantId}/teams/{teamId}/memories` | Store a new shared memory |
| `POST` | `/api/v1/tenants/{tenantId}/teams/{teamId}/memories/search` | Semantic retrieval (body `{"query","limit"}`; reinforces on read) |
| `GET` | `/api/v1/tenants/{tenantId}/teams/{teamId}/memories?type=SEMANTIC` | Retrieve by type (reinforces on read) |
| `POST` | `/api/v1/tenants/{tenantId}/teams/{teamId}/memories/{memoryId}/contribute` | Record a distinct additional contributor (shared reinforcement) |
| `GET` | `/api/v1/tenants/{tenantId}/teams/{teamId}/memories/count` | Active memory count for a team |
| `DELETE` | `/api/v1/tenants/{tenantId}/teams/{teamId}/memories/{memoryId}` | Delete a specific memory |
| `POST` | `/api/v1/federation/query` | Privacy-preserving cross-instance query — optional per-peer bearer auth (401 when required + missing/invalid), rate-limited per origin; `"includePeers": true` fans out to configured peer instances |
| `GET` | `/api/v1/federation/audit` | Recent federation-query audit (who queried, what type, how many results) |
| `GET`/`PUT` | `/api/v1/tenants/{tenantId}/memory-policy` | Read / replace a tenant's governance policy (incl. `federationSummaryChars` redaction depth) |
| `GET` | `/actuator/health` | Liveness + readiness probes |

## Memory Model

Shared memories reuse the four-type cognitive model of Aether Core, generalised from an individual to a group:

| Type | Description |
|---|---|
| `EPISODIC` | Team events and shared experiences ("the Q3 incident retrospective") |
| `SEMANTIC` | Organisational facts and domain knowledge ("our SLA is 99.95%") |
| `PROCEDURAL` | Shared playbooks and conventions ("how the team deploys") |
| `EMOTIONAL` | Collective sentiment and team affect ("morale after the launch") |

### Visibility & Federation

| Visibility | Reach |
|---|---|
| `PRIVATE` | Owning team only |
| `TENANT` | Every team within the tenant |
| `FEDERATED` | Eligible for cross-instance federation queries (opt-in per tenant) |

Federation is privacy-preserving by construction: only `FEDERATED` memories in **federation-enabled** tenants are candidates, results are projected to length-bounded summaries with coarse provenance (source tenant, never the owning team), and the result count is capped.

### Shared Reinforcement & Decay

Every team retrieval reinforces a memory (strength up by the tenant's configured increment) — via both `GET …/memories?type=` and the semantic `POST …/memories/search`. Every distinct contributor raises its `contributorCount` through `POST …/memories/{id}/contribute`. Idle memories decay on a schedule using **per-tenant** parameters; once below a tenant's archive threshold they are moved to an archive table — never silently deleted.

## Ecosystem

```
Aether Ecosystem
├── aether          (suplab/aether)         — philosophy, standards, ADRs
├── aether-core     (suplab/aether-core)    — personal cognitive engine (port 8082)
├── aether-grid     (suplab/aether-grid)    — enterprise agent mesh (ports 8080/8081)
└── aether-memory   (suplab/aether-memory)  ← you are here — shared team memory platform (port 8083)
```

Aether Memory owns the **Shared Memory** capability exclusively. Personal memory stays in Aether Core; this platform does not duplicate it.

---

## Configuration

| Environment Variable | Default | Description |
|---|---|---|
| `POSTGRES_URL` | `jdbc:postgresql://localhost:5432/aether_memory` | PostgreSQL connection |
| `POSTGRES_USER` | `aether` | DB username |
| `POSTGRES_PASSWORD` | `aether` | DB password |
| `OLLAMA_BASE_URL` | `http://localhost:11434` | Ollama embedding endpoint |
| `EMBEDDING_ENABLED` | `true` | Toggle embedding (zero-vector fallback when off) |
| `EMBEDDING_MODEL` | `all-minilm` | Embedding model name |
| `MEMORY_DECAY_ENABLED` | `true` | Toggle the scheduled decay/archive lifecycle |
| `MEMORY_DECAY_RATE` | `0.01` | Default strength lost per idle day (tenants may override) |
| `MEMORY_ARCHIVE_THRESHOLD` | `0.1` | Default archive cutoff strength (tenants may override) |
| `FEDERATION_RATE_LIMIT_MAX` | `60` | Max federation queries per origin per window |
| `FEDERATION_RATE_LIMIT_WINDOW_SECONDS` | `60` | Rate-limit window length (seconds) |
| `FEDERATION_RATE_LIMIT_BACKEND` | `memory` | `memory` (per-instance) or `redis` (shared across the fleet; degrades to per-node if Redis is down) |
| `REDIS_HOST` | `localhost` | Redis host — only used when the rate-limit backend is `redis` |
| `REDIS_PORT` | `6379` | Redis port — only used when the rate-limit backend is `redis` |
| `aether.memory.federation.peers` | _(empty)_ | Comma-separated peer base URLs for outbound fan-out (empty = local-only) |
| `FEDERATION_REQUIRE_AUTH` | `false` | Require a bearer token on inbound `/federation/query` (fail-closed: needs `FEDERATION_AUTH_TOKEN`) |
| `FEDERATION_AUTH_TOKEN` | _(empty)_ | Shared inbound token peers must present as `Authorization: Bearer …` |
| `FEDERATION_PEER_AUTH_TOKEN` | _(empty)_ | Outbound bearer token sent to configured peers on fan-out |
| `SERVER_PORT` | `8083` | HTTP port |
