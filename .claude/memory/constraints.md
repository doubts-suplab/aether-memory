# Constraints — Aether Memory

## Ten Golden Rules (Non-Negotiable)
1. Constructor injection exclusively — no field `@Autowired`/`@Inject`; fields `final`
2. No hardcoded secrets — credentials via environment variables only
3. SLF4J parameterized logging — never `System.out.println()` or string concatenation
4. SOLID design principles
5. DDD bounded contexts — cross-module calls go through port interfaces
6. Explicit column lists in SQL — never `SELECT *`
7. Parameterized queries only — `NamedParameterJdbcTemplate`, never string concatenation
8. Conventional Commits — `type(scope): description`
9. No `// TODO` in committed code
10. `jakarta.*` exclusively — `javax.*` is a build-breaking error

## Aether Memory-Specific Hard Constraints
- **Scoping:** every shared-memory query includes `tenant_id` AND `team_id` in WHERE — no cross-team or cross-tenant read path
- **Federation privacy:** federation returns only `FEDERATED` memories from `federation_enabled` tenants; results are `FederatedMemory` projections — never raw `SharedMemory`, never team/contributor identity or raw IDs
- **Federation bounds:** result count clamped to `DefaultMemoryFederationService.MAX_FEDERATION_LIMIT`; summary ≤ `FederatedMemory.MAX_SUMMARY_LENGTH`
- **Embedding dimension:** 384 (all-MiniLM-L6-v2). Changing requires a full re-embedding migration.
- **Ollama replaceable:** all embedding calls go through `SharedEmbeddingService` — never direct HTTP from callers
- **Standalone:** must boot, migrate, serve, and run its lifecycle with no dependency on Core or Grid
- **Ports:** Grid proxy=8080, Grid api=8081, Core=8082, Memory=**8083** — do not collide

## Prohibited Patterns
- `javax.*`, field injection, `SELECT *`, hardcoded credentials
- `Thread.sleep()` in tests (use Testcontainers/Awaitility)
- Empty `catch` blocks, `Optional.get()` without guard
- Returning a raw `SharedMemory` across the federation boundary
- Missing `tenant_id`/`team_id` in a WHERE clause
