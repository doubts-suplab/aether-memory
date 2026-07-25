package com.suplab.aether.memory.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * An append-only audit record of a federation query served by this instance.
 *
 * <p>Federation crosses the tenancy boundary, so who asked for what — and how much they got back —
 * must be demonstrable after the fact (accountability). This record captures the querying tenant, an
 * optional type filter, a <strong>length-bounded label</strong> of the query text (never an
 * unbounded payload), the number of projections returned, and when. It holds no memory content and
 * no owning-team identity. Written once; never updated or deleted.</p>
 *
 * @param id             stable identifier
 * @param originTenantId the tenant that issued the federation query
 * @param type           the optional type filter applied ({@code null} = all types)
 * @param queryLabel     a bounded excerpt of the query text (for audit; truncated on construction)
 * @param resultCount    how many federated projections were returned
 * @param occurredAt     when the query was served
 */
public record FederationAuditEvent(
        UUID id,
        String originTenantId,
        MemoryType type,
        String queryLabel,
        int resultCount,
        Instant occurredAt
) {
    /** Maximum characters of the query text retained in the audit label. */
    public static final int MAX_QUERY_LABEL = 120;

    public FederationAuditEvent {
        if (id == null) id = UUID.randomUUID();
        if (originTenantId == null || originTenantId.isBlank())
            throw new IllegalArgumentException("originTenantId required");
        if (resultCount < 0) throw new IllegalArgumentException("resultCount must be >= 0");
        if (queryLabel == null) queryLabel = "";
        if (queryLabel.length() > MAX_QUERY_LABEL) queryLabel = queryLabel.substring(0, MAX_QUERY_LABEL);
        if (occurredAt == null) occurredAt = Instant.now();
    }

    /**
     * Factory recording a freshly served federation query: random ID, {@code occurredAt} now, query
     * text truncated to {@link #MAX_QUERY_LABEL}.
     *
     * @param originTenantId the querying tenant
     * @param type           the type filter applied (nullable)
     * @param queryText      the raw query text (truncated into the label)
     * @param resultCount    the number of projections returned
     */
    public static FederationAuditEvent of(String originTenantId, MemoryType type, String queryText,
                                          int resultCount) {
        return new FederationAuditEvent(UUID.randomUUID(), originTenantId, type, queryText, resultCount,
                Instant.now());
    }
}
