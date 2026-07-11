# Approved Patterns — Aether Memory

## Persistence
- **pgvector cosine search:** `ORDER BY embedding <=> :query::vector` with the query vector serialised as `[x,y,…]` and cast `::vector`. Explicit column lists always.
- **UPSERT:** `INSERT … ON CONFLICT (id) DO UPDATE SET …` for idempotent saves.
- **Reinforce-on-read:** `findByType`/`findSimilar` reinforce each returned memory using the tenant's `reinforcementIncrement`, then persist the reinforced state via a scoped UPDATE.
- **Set-based lifecycle:** decay and archive are single SQL statements. Per-tenant params via `LEFT JOIN memory_policies` + `COALESCE(<policy>, :default)`. Archive is a data-modifying CTE (`WITH victims … , moved AS (DELETE … RETURNING …) INSERT …`) — atomic move.

## Domain
- **Immutable records** with compact-constructor validation; mutation returns a new instance (`reinforce`, `contribute`, `withVisibility`).
- **Factory methods:** `SharedMemory.create(scope, type, content, visibility)`, `MemoryPolicy.defaults(tenantId)`, `FederatedMemory.from(memory, provenance)`, `MemoryScope.of(...)`.
- **Defaults over nulls:** `MemoryPolicyStore.resolve` returns `MemoryPolicy.defaults(...)` rather than empty — callers never handle a missing policy.

## Spring wiring
- **Constructor injection only.** Beans declared in `MemoryApiConfig` / `MemoryLifecycleConfig`; adapters live in `memory-engine` and are pure (no Spring annotations).
- **Optional beans:** `Optional<SharedEmbeddingService>` so the app runs with embeddings disabled (`@ConditionalOnProperty`, zero-vector fallback).
- **Config via `@Value` with env-backed defaults** in `application.yml`.

## API
- **Tenant+team scoping in the path:** `/api/v1/tenants/{tenantId}/teams/{teamId}/…`.
- **Lenient request parsing:** tolerant `asString`/`asInt`/`asDouble`/`asBoolean` helpers; validation errors → 400 with an `error` field.
- **Always-usable responses:** policy GET returns defaults (never 404); federation returns `[]` when nothing matches.

## Testing
- **Unit tests** (`*Test`, surefire) for domain logic and pure services (fake in-memory store, `SimpleMeterRegistry`).
- **Integration tests** (`*IT`, Testcontainers `pgvector/pgvector:pg16`, Flyway-migrated) for JDBC adapters — run in CI where Docker is present.
