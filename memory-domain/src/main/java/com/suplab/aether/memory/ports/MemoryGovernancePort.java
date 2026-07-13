package com.suplab.aether.memory.ports;

import com.suplab.aether.memory.domain.ErasureAuditEntry;
import com.suplab.aether.memory.domain.MemoryScope;
import com.suplab.aether.memory.domain.SharedMemory;

import java.util.List;

/**
 * Port for GDPR governance operations: right-to-erasure and data-portability export.
 *
 * <p>Erasure spans both the active {@code shared_memories} table and the
 * {@code shared_memories_archive}, and writes a {@link ErasureAuditEntry} in the same transaction
 * so deletion and proof-of-erasure are atomic. Export returns a team's active memories for the
 * data-access / portability right.</p>
 */
public interface MemoryGovernancePort {

    /**
     * Outcome of an erasure request.
     *
     * @param activeDeleted   rows removed from {@code shared_memories}
     * @param archivedDeleted rows removed from {@code shared_memories_archive}
     */
    record ErasureResult(long activeDeleted, long archivedDeleted) {}

    /**
     * Erases every memory owned by a team (active + archive), atomically with an audit record.
     *
     * @param scope the team to erase
     * @return the counts removed from each table
     */
    ErasureResult eraseTeam(MemoryScope scope);

    /**
     * Erases every memory across all teams of a tenant (active + archive) and the tenant's
     * federation-audit trail, atomically with an audit record.
     *
     * @param tenantId the tenant to erase
     * @return the counts removed from each memory table
     */
    ErasureResult eraseTenant(String tenantId);

    /**
     * Returns a team's active memories for GDPR data access / portability. No reinforcement occurs
     * and there is no result cap — this is a full export.
     *
     * @param scope the team whose memories to export
     * @return all active memories owned by the team (may be empty)
     */
    List<SharedMemory> exportTeam(MemoryScope scope);

    /**
     * Returns the most recent erasure audit records for a tenant, newest first.
     *
     * @param tenantId the tenant to report on
     * @param limit    maximum number of entries
     * @return erasure audit entries (may be empty)
     */
    List<ErasureAuditEntry> recentErasures(String tenantId, int limit);
}
