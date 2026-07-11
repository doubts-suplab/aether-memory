# Architecture Decisions — Aether Memory

> Chronological log of significant decisions. Promote any that need full rationale to an ADR under `docs/adr/`.

## ADR-0001 — Standalone platform, mirroring Aether Core structure (Phase 0)
- **Decision:** Bootstrap Aether Memory as an independent Maven multi-module project (`memory-domain`, `memory-engine`, `memory-api`, `memory-infra`) mirroring the shape and conventions of `aether-core`.
- **Rationale:** The ecosystem standardises on Java 21 / Spring Boot 3.3 / pgvector / Flyway. Consistency with Core/Grid makes the platform legible, reviewable, and integrable. Divergent stacks (e.g. Python) were considered and rejected for fragmenting the ecosystem.
- **Consequence:** Reuses the ecosystem's golden rules, CI patterns, Docker/k8s patterns, and 384-dim embedding contract.

## ADR-0002 — Team as the ownership unit; MemoryScope = (tenantId, teamId) (Phase 0)
- **Decision:** Shared memory is owned by a team within a tenant, keyed by `MemoryScope`. Every query is scoped by both.
- **Rationale:** Generalises Core's per-`userId` scoping to a group while preserving strict multi-tenant isolation.

## ADR-0003 — Visibility ladder with opt-in federation (Phase 0)
- **Decision:** `PRIVATE → TENANT → FEDERATED`. Federation returns only `FEDERATED` memories from tenants where `federationEnabled = true` (default false).
- **Rationale:** Privacy by default; sharing is an explicit, per-tenant opt-in.

## ADR-0004 — Privacy-preserving federation projections (Phase 0)
- **Decision:** Federation never returns raw `SharedMemory`. Results are `FederatedMemory` projections: bounded summary (≤280 chars), coarse provenance (source tenant), no team/contributor identity or raw IDs. Result count clamped to `MAX_FEDERATION_LIMIT`.
- **Rationale:** A source tenant must not leak *who* knows what, nor expose an unbounded slice of its corpus.

## ADR-0005 — Per-tenant, set-based lifecycle (Phase 0)
- **Decision:** Decay + archive run as single SQL statements that `LEFT JOIN memory_policies` and `COALESCE` to injected defaults. Archive uses a data-modifying CTE for an atomic move.
- **Rationale:** Configurable per-tenant governance without per-row round trips; a memory is never deleted without landing in the archive.

## ADR-0006 — JaCoCo gate mirrored from Core (dormant in pluginManagement) (Phase 0)
- **Decision:** Keep the JaCoCo 80% configuration in parent `pluginManagement` exactly as Aether Core does; Testcontainers ITs named `*IT` run in CI where Docker is available.
- **Rationale:** Ecosystem consistency. Revisit wiring an enforced coverage gate (with failsafe) as a deliberate ecosystem-wide change, not a per-repo divergence.
