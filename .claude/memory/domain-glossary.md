# Domain Glossary — Aether Memory

| Term | Definition |
|---|---|
| **Shared Memory** | A memory owned by a *team* (not an individual). The core aggregate of this platform. |
| **Memory Scope** | The `(tenantId, teamId)` ownership key. Every store read/write is scoped by it — the multi-tenancy boundary. |
| **Visibility** | How far a memory may travel: `PRIVATE` (team) → `TENANT` (all teams in tenant) → `FEDERATED` (cross-instance). |
| **Shared Reinforcement** | Strength growth driven by *collective* access. Retrieval reinforces (`reinforce`); a distinct contributor raises `contributorCount` (`contribute`). |
| **Contributor Count** | How many distinct members have independently asserted a memory. More contributors → stronger memory. |
| **Memory Policy** | Per-tenant governance: decay rate, grace period, reinforcement increment, archive threshold, retention days, federation toggle. Defaults apply when unset. |
| **Decay** | Scheduled strength reduction for idle memories, using per-tenant parameters. Mirrors forgetting. |
| **Archive** | Sub-threshold memories are moved to `shared_memories_archive` (keeping embeddings) — never silently deleted. |
| **Federation** | Privacy-preserving cross-instance query. Only `FEDERATED` memories in federation-enabled tenants; projected to bounded summaries. |
| **Federated Memory** | The lossy projection returned across the federation boundary: type, summary (≤280 chars), strength, coarse provenance. |
| **Provenance** | The coarse origin label on a federated result — the *source tenant*, never the owning team or contributor. |
| **Memory Type** | EPISODIC \| SEMANTIC \| PROCEDURAL \| EMOTIONAL — the four-type cognitive model shared with Aether Core. |

## Ecosystem terms
| Term | Definition |
|---|---|
| **Aether Core** | Personal cognitive engine — owns per-user memory. Complements Memory. |
| **Aether Grid** | Distributed agent mesh / API governance runtime. |
| **Platform layer** | Aether Memory's position — above Grid/Core, below domain products (Vault, Flow, Enterprise). |
| **Standalone guarantee** | Memory boots and runs with no dependency on Core or Grid. |
