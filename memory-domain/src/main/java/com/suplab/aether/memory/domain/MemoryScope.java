package com.suplab.aether.memory.domain;

/**
 * The ownership key for a shared memory: a {@code teamId} within a {@code tenantId}.
 *
 * <p>Every query into the shared-memory store is scoped by this pair — there is no
 * cross-tenant or cross-team read path that does not pass a {@code MemoryScope}. This is the
 * multi-tenancy boundary of Aether Memory, equivalent to Aether Core's per-{@code userId}
 * scoping but generalised to a group.</p>
 */
public record MemoryScope(String tenantId, String teamId) {

    public MemoryScope {
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId required");
        if (teamId == null || teamId.isBlank()) throw new IllegalArgumentException("teamId required");
    }

    /**
     * Convenience factory mirroring the {@code of(...)} idiom used across the domain.
     */
    public static MemoryScope of(String tenantId, String teamId) {
        return new MemoryScope(tenantId, teamId);
    }
}
