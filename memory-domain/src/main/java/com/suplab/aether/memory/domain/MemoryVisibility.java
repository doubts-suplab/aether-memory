package com.suplab.aether.memory.domain;

/**
 * Controls how far a shared memory may travel.
 *
 * <p>Visibility is the access-control primitive of Aether Memory. It widens outward from a
 * single team, to a whole tenant, to cross-instance federation. Federation queries never
 * return anything below {@link #FEDERATED} — privacy is the default, sharing is opt-in.</p>
 *
 * <ul>
 *   <li>PRIVATE   — visible only to the owning team ({@code tenantId} + {@code teamId})</li>
 *   <li>TENANT    — visible to every team within the same tenant</li>
 *   <li>FEDERATED — eligible for privacy-preserving cross-instance federation queries</li>
 * </ul>
 */
public enum MemoryVisibility {
    PRIVATE,
    TENANT,
    FEDERATED;

    /**
     * @return {@code true} if a memory at this visibility may be surfaced by a federation query.
     */
    public boolean isFederatable() {
        return this == FEDERATED;
    }
}
