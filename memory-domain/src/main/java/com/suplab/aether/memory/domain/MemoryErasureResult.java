package com.suplab.aether.memory.domain;

/**
 * The outcome of erasing a team's shared memories — Aether Memory's contribution to the ecosystem's
 * right-to-erasure (GDPR Art. 17).
 *
 * <p>Erasure removes a team's memories from <em>both</em> the active store and the archive, so faded
 * (archived) memories are not overlooked. The counts report what was removed from each so the deletion
 * is auditable. A team with nothing stored returns zero counts — the operation is idempotent.</p>
 *
 * @param tenantId       owning tenant (isolation boundary)
 * @param teamId         the erased team
 * @param activeErased   memories removed from the active store
 * @param archivedErased memories removed from the archive
 */
public record MemoryErasureResult(
        String tenantId,
        String teamId,
        int activeErased,
        int archivedErased
) {
    public MemoryErasureResult {
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId required");
        if (teamId == null || teamId.isBlank()) throw new IllegalArgumentException("teamId required");
        if (activeErased < 0 || archivedErased < 0)
            throw new IllegalArgumentException("erasure counts must be >= 0");
    }

    /** @return {@code true} if the team held no memories to erase. */
    public boolean isEmpty() {
        return activeErased == 0 && archivedErased == 0;
    }
}
