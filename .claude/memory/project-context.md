# Project Context — Aether Memory

## Service Identity
- **Name:** Aether Memory (`suplab/aether-memory`)
- **Purpose:** Shared team and organisational memory platform — team memory, shared reinforcement, per-tenant policy, privacy-preserving federation
- **Port:** 8083
- **Database:** `aether_memory` (PostgreSQL 16 + pgvector, separate from Core and Grid)
- **Ecosystem layer:** Platform (above Grid runtime and Core cognition; below domain products)

## Capability Ownership
- **Owns (exclusively):** Shared Memory — Team Memory, Shared Reinforcement, Memory Federation API, Configurable Policies
- Personal memory stays in Aether Core; Memory does not duplicate it

## Maven Modules
| Module | Artifact ID | Purpose |
|---|---|---|
| `memory-domain` | `memory-domain` | Domain types + port interfaces (no Spring) |
| `memory-engine` | `memory-engine` | pgvector store, embedding, policy lifecycle, federation |
| `memory-api` | `memory-api` | Spring Boot app, REST controllers, Flyway |
| `memory-infra` | `memory-infra` | Docker Compose, k8s, standalone migrations |

## Key Packages
- `com.suplab.aether.memory.domain` — SharedMemory, MemoryScope, MemoryVisibility, MemoryType, MemoryPolicy, FederationQuery, FederatedMemory
- `com.suplab.aether.memory.ports` — SharedMemoryStore, MemoryPolicyStore, MemoryFederationPort, MemoryLifecyclePort
- `com.suplab.aether.memory.engine.store` — PGVectorSharedMemoryStore
- `com.suplab.aether.memory.engine.embedding` — SharedEmbeddingService (Ollama)
- `com.suplab.aether.memory.engine.policy` — JdbcMemoryPolicyStore
- `com.suplab.aether.memory.engine.lifecycle` — PolicyAwareMemoryLifecycleService
- `com.suplab.aether.memory.engine.federation` — DefaultMemoryFederationService
- `com.suplab.aether.memory.api` — AetherMemoryApplication, controllers, config

## Environments
- **Local:** Docker Compose at `memory-infra/docker/docker-compose.yml` (postgres-memory on 5434, app on 8083)
- **CI:** GitHub Actions, pgvector service container
- **Production:** Kubernetes (manifests in `memory-infra/k8s/`; Helm chart — planned Phase 4)

## Current Status
- Phase 0 (scaffold) complete — domain, engine, API, infra, docs, CI all in place
- Next: Phase 1 — Shared Memory Engine
