# CLAUDE.md — Aether Memory Project Brief

> Read this at the start of every session. Single source of truth for what this project is, how it is built, and what rules apply.

---

## What This Project Is

**Aether Memory** (`suplab/aether-memory`) is the shared-memory platform of the Aether ecosystem — the layer that owns **team and organisational memory**, generalising Aether Core's per-user cognition to groups, and adding privacy-preserving **federation** across instances.

> **Ecosystem navigation**
>
> | Layer | Repo | Purpose |
> |---|---|---|
> | Aether Philosophy | [`suplab/aether`](https://github.com/suplab/aether) | The vision: cognitive fabric connecting humans, memory, and AI |
> | **Aether Core** | [`suplab/aether-core`](https://github.com/suplab/aether-core) | Personal cognitive engine — individual memory, reasoning, emotional context |
> | **Aether Grid** | [`suplab/aether-grid`](https://github.com/suplab/aether-grid) | Distributed agent mesh — enterprise API governance platform |
> | **Aether Memory** | `suplab/aether-memory` ← **you are here** | Shared team/org memory platform — federation, per-tenant policy |

**Capability owned (exclusively):** *Shared Memory* — Team Memory, Shared Reinforcement, Memory Federation API, Configurable Policies. Personal memory remains owned by Aether Core; Memory does not duplicate it.

**Current status:** Phases 0–4 complete (Scaffold, Shared Memory Engine, Federation, Governance & Policy, Kubernetes + Helm). The planned roadmap is fully delivered.

**One runnable application:**
- `memory-api` — Shared Memory Platform API (port 8083)

**Three library modules:** `memory-domain`, `memory-engine`, `memory-infra`

---

## Technology Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.3.5 (`jakarta.*` exclusively — never `javax.*`) |
| Database | PostgreSQL 16 + pgvector extension |
| Vector Store | pgvector (384-dim, all-MiniLM-L6-v2) |
| Embedding | all-MiniLM-L6-v2 via Ollama (`/api/embeddings`) |
| LLM Runtime | Ollama (local, model-agnostic) |
| DB Migrations | Flyway (classpath:db/migration in memory-api) |
| Build | Maven (multi-module, Java 21, --enable-preview) |
| Local Dev | Docker Compose (`memory-infra/docker/docker-compose.yml`) |
| CI/CD | GitHub Actions (OIDC, SHA-pinned actions) |

---

## Bounded Context

- Package root: `com.suplab.aether.memory`
- Port: **8083** (Grid proxy=8080, Grid api=8081, Core=8082, Memory=8083)
- Database: `aether_memory` (separate schema — data isolation from Core and Grid)
- REST API surface:
  - `.../teams/{teamId}/memories` — team-scoped shared memory CRUD
  - `POST /api/v1/federation/query` — privacy-preserving cross-instance query
  - `GET|PUT /api/v1/tenants/{tenantId}/memory-policy` — per-tenant governance

---

## Module Structure

```
aether-memory-parent (pom.xml)
├── memory-domain   — domain types (SharedMemory, MemoryPolicy, FederatedMemory, MemoryScope) + port interfaces
├── memory-engine   — pgvector store, Ollama embedding, policy-aware lifecycle, federation service
├── memory-api      — Spring Boot REST API, Flyway migrations, config
└── memory-infra    — Docker Compose, k8s manifests, migration reference copies (no Java sources)
```

### Dependency Graph

```
memory-api
  ├── memory-domain
  └── memory-engine
        └── memory-domain
memory-infra  (no Java)
```

`memory-domain` has no framework dependency — pure Java 21 records and interfaces.

---

## Core Domain Concepts

| Concept | Meaning |
|---|---|
| **SharedMemory** | A memory owned by a *team* (not a user). Strength shaped by collective access. |
| **MemoryScope** | The `tenantId` + `teamId` ownership key — the multi-tenancy boundary. |
| **MemoryVisibility** | `PRIVATE` (team) → `TENANT` (all teams in tenant) → `FEDERATED` (cross-instance). |
| **Shared Reinforcement** | Every team retrieval reinforces a memory; every distinct contributor raises `contributorCount`. |
| **MemoryPolicy** | Per-tenant decay rate, grace period, reinforcement increment, archive threshold, retention, federation toggle. |
| **Federation** | Privacy-preserving cross-instance query — only `FEDERATED` memories in federation-enabled tenants, projected to bounded summaries with coarse provenance. |

---

## Pre-Coding Checklist

Before writing any code:
- [ ] Which module does this change belong to? Does it respect bounded context?
- [ ] Is there an existing port interface or utility to reuse?
- [ ] Does this change require a new Flyway migration?
- [ ] Does this change affect the data model or API contract? → update `docs/architecture.md`
- [ ] Does this change affect the roadmap status? → update `docs/progress.md` and `docs/roadmap.md`
- [ ] Does this touch federation? → is privacy preserved (no team/contributor identity leaves the boundary)?

---

## Ten Golden Rules (Non-Negotiable)

1. **Constructor injection exclusively** — no field-level `@Autowired`, no `@Inject`, fields must be `final`
2. **No hardcoded secrets** — all credentials to environment variables; never committed to source
3. **SLF4J with parameterized messages** — never `System.out.println()` or string concatenation in logs
4. **SOLID design principles** — single responsibility, open/closed, Liskov, interface segregation, dependency inversion
5. **DDD bounded contexts** — cross-module calls go through port interfaces, never reach into another module's internals
6. **Explicit column lists in SQL** — never `SELECT *`; always name every column
7. **Parameterized queries only** — no string concatenation for SQL; use `NamedParameterJdbcTemplate`
8. **Conventional Commits** — `type(scope): description` (feat, fix, docs, chore, build, test, refactor)
9. **No `// TODO` in committed code** — if it's not done, don't commit it
10. **`jakarta.*` exclusively** — Spring Boot 3.x; `javax.*` imports are a build-breaking error

### Aether Memory-Specific Constraints

- All shared-memory queries scoped by `tenant_id` **and** `team_id` — no cross-team or cross-tenant read path
- Federation returns only `FEDERATED`-visibility memories from federation-enabled tenants — privacy is the default, sharing is opt-in
- Federated results never carry `team_id`, contributor identity, or raw IDs — only bounded summaries + coarse provenance
- Embedding dimension is 384 (all-MiniLM-L6-v2) — changing requires a full re-embedding migration
- Ollama must be replaceable: all embedding calls go through `SharedEmbeddingService` (not direct HTTP)
- Memory is a *platform* layer — it must run standalone without Core or Grid present

---

## Slash Commands

| Command | Purpose |
|---|---|
| `/estimate` | P50/P80/P90 effort estimate (Human Days = Raw Hours / 6.4) |
| `/review` | Code review against golden rules |
| `/adr` | Create an Architecture Decision Record |
| `/security-scan` | Security review of current changes |
| `/memory-update` | Update `.claude/memory/` files after major decisions |

---

## Memory Files

| File | Contents |
|---|---|
| `project-context.md` | Service details, ports, environments |
| `domain-glossary.md` | Aether Memory terminology |
| `decisions.md` | Architecture decisions log |
| `constraints.md` | Hard constraints + golden rules |
| `patterns.md` | Approved patterns in use |
| `session-log.md` | Rolling session log |

---

## Prohibited Patterns

- `javax.*` in any Spring Boot 3.x file
- Field `@Autowired` or `@Inject`
- `SELECT *` in any SQL
- Hardcoded passwords, tokens, or connection strings
- `Thread.sleep()` in tests (use Awaitility or Testcontainers)
- Empty `catch` blocks
- `Optional.get()` without guard
- `System.out.println()` in any production code
- Cross-team / cross-tenant data access (missing `tenant_id` or `team_id` in WHERE clause)
- Returning raw `SharedMemory` across the federation boundary

---

## Documentation Sync Rule

Every commit that changes system behavior MUST update:
- `docs/progress.md` — mark completed deliverables
- `README.md` — if architecture or scope changed
- `docs/index.html` — if conceptual overview or tech stack changed
- `docs/roadmap.md` — if milestones shift
- `docs/architecture.md` — if architectural decisions change
