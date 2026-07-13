package com.suplab.aether.memory.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * An audit record for a single federation query.
 *
 * <p>Captures <em>the query</em> — who asked ({@code originTenantId}), what for
 * ({@code queryText}, optional {@code type}), when ({@code occurredAt}), how many results were
 * returned, and whether the query was served locally-only (a peer-to-peer call) or fanned out.
 * It never records the identity of the matched memories, preserving federated content privacy
 * while still enabling abuse detection and rate accounting.</p>
 */
public record FederationAuditEntry(
        UUID id,
        String originTenantId,
        String queryText,
        MemoryType type,
        int resultCount,
        boolean localOnly,
        Instant occurredAt
) {
    public FederationAuditEntry {
        if (id == null) id = UUID.randomUUID();
        if (originTenantId == null || originTenantId.isBlank())
            throw new IllegalArgumentException("originTenantId required");
        if (queryText == null) queryText = "";
        if (resultCount < 0) throw new IllegalArgumentException("resultCount must be >= 0");
        if (occurredAt == null) occurredAt = Instant.now();
    }

    /**
     * Factory for a fresh audit entry stamped at {@link Instant#now()}.
     */
    public static FederationAuditEntry record(String originTenantId, String queryText, MemoryType type,
                                              int resultCount, boolean localOnly) {
        return new FederationAuditEntry(UUID.randomUUID(), originTenantId, queryText, type,
                resultCount, localOnly, Instant.now());
    }
}
