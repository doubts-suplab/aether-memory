package com.suplab.aether.memory.domain;

/**
 * Per-tenant governance policy for shared memory.
 *
 * <p>Aether Memory generalises Aether Core's fixed lifecycle constants into a
 * <em>configurable</em>, per-tenant contract: how fast memories decay, how long they are
 * retained, how strongly access reinforces them, and whether the tenant permits federation.
 * A tenant with no explicit policy falls back to {@link #defaults(String)}.</p>
 *
 * @param tenantId              the tenant this policy governs
 * @param decayRate             strength lost per idle day (0–1)
 * @param decayAfterDays        grace period — memories accessed within this window do not decay
 * @param reinforcementIncrement strength gained on each team access (0–1)
 * @param archiveThreshold      memories below this strength are archived (0–1)
 * @param retentionDays         days an archived memory is retained before it is eligible for purge
 * @param federationEnabled     whether {@code FEDERATED} memories may leave this tenant
 */
public record MemoryPolicy(
        String tenantId,
        double decayRate,
        int decayAfterDays,
        double reinforcementIncrement,
        double archiveThreshold,
        int retentionDays,
        boolean federationEnabled
) {
    public MemoryPolicy {
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId required");
        if (decayRate < 0 || decayRate > 1) throw new IllegalArgumentException("decayRate must be 0-1");
        if (decayAfterDays < 0) throw new IllegalArgumentException("decayAfterDays must be >= 0");
        if (reinforcementIncrement < 0 || reinforcementIncrement > 1)
            throw new IllegalArgumentException("reinforcementIncrement must be 0-1");
        if (archiveThreshold < 0 || archiveThreshold > 1)
            throw new IllegalArgumentException("archiveThreshold must be 0-1");
        if (retentionDays < 0) throw new IllegalArgumentException("retentionDays must be >= 0");
    }

    /**
     * The default policy applied to any tenant that has not configured its own. Values mirror
     * Aether Core's lifecycle defaults (decay 0.01/day after a 7-day grace, archive below 0.1)
     * with federation disabled — sharing across instances is always an explicit opt-in.
     */
    public static MemoryPolicy defaults(String tenantId) {
        return new MemoryPolicy(tenantId, 0.01, 7, 0.1, 0.1, 90, false);
    }
}
