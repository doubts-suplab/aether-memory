package com.suplab.aether.memory.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable proof-of-erasure record for a GDPR right-to-erasure request.
 *
 * <p>Written in the same transaction as the deletion so the audit and the erasure are atomic.
 * For a {@link ErasureScope#TENANT} erasure {@code teamId} is {@code null}.</p>
 *
 * @param id              audit id
 * @param tenantId        the tenant erased (or the tenant of the erased team)
 * @param teamId          the team erased, or {@code null} for tenant-wide erasure
 * @param scope           the erasure breadth
 * @param activeDeleted   count removed from {@code shared_memories}
 * @param archivedDeleted count removed from {@code shared_memories_archive}
 * @param occurredAt      when the erasure happened
 */
public record ErasureAuditEntry(
        UUID id,
        String tenantId,
        String teamId,
        ErasureScope scope,
        long activeDeleted,
        long archivedDeleted,
        Instant occurredAt
) {
    public ErasureAuditEntry {
        if (id == null) id = UUID.randomUUID();
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId required");
        if (scope == null) throw new IllegalArgumentException("scope required");
        if (scope == ErasureScope.TEAM && (teamId == null || teamId.isBlank()))
            throw new IllegalArgumentException("teamId required for TEAM erasure");
        if (activeDeleted < 0 || archivedDeleted < 0)
            throw new IllegalArgumentException("deleted counts must be >= 0");
        if (occurredAt == null) occurredAt = Instant.now();
    }
}
