package com.suplab.aether.memory.domain;

/**
 * A request to search shared memories across the federation boundary.
 *
 * <p>Federation queries only ever match memories at {@link MemoryVisibility#FEDERATED} and
 * only within tenants whose {@link MemoryPolicy#federationEnabled()} is {@code true}. The
 * {@code originTenantId} identifies the caller for audit and rate purposes; it does not widen
 * what the caller may see.</p>
 *
 * @param originTenantId the tenant issuing the query (for audit/provenance, never for elevation)
 * @param type           optional memory-type filter; {@code null} matches all types
 * @param queryText      the natural-language text to embed and match semantically
 * @param limit          maximum number of results (clamped to a sane maximum by the service)
 */
public record FederationQuery(
        String originTenantId,
        MemoryType type,
        String queryText,
        int limit
) {
    public FederationQuery {
        if (originTenantId == null || originTenantId.isBlank())
            throw new IllegalArgumentException("originTenantId required");
        if (queryText == null || queryText.isBlank())
            throw new IllegalArgumentException("queryText required");
        if (limit <= 0) limit = 10;
    }
}
