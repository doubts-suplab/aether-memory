package com.suplab.aether.memory.ports;

/**
 * Port interface for the policy-driven memory decay + archive lifecycle.
 *
 * <p>Mirrors human forgetting, generalised to teams and made <em>configurable per tenant</em>:
 * memories that no team member accesses lose strength over time (retrieval reinforces them via
 * {@link SharedMemoryStore}), and memories that fade below a tenant's archive threshold are
 * moved out of active recall — archived, never silently deleted.</p>
 */
public interface MemoryLifecyclePort {

    /**
     * Outcome of one lifecycle run across all tenants.
     *
     * @param decayedCount   memories whose strength was reduced this run
     * @param archivedCount  memories moved to the archive this run
     * @param purgedCount    archived memories permanently purged past their retention window
     * @param totalRemaining active memories remaining after the run
     */
    record LifecycleResult(long decayedCount, long archivedCount, long purgedCount, long totalRemaining) {}

    /**
     * Runs one decay + archive + purge cycle. For each tenant the effective
     * {@link com.suplab.aether.memory.domain.MemoryPolicy} supplies the decay rate, grace period,
     * archive threshold, and retention window:
     * <ol>
     *   <li>Decay: {@code strength -= decayRate × days_since_access} for memories not accessed
     *       within the grace period (floored at 0).</li>
     *   <li>Archive: memories with {@code strength} below the archive threshold are moved to
     *       {@code shared_memories_archive}.</li>
     *   <li>Purge: archived memories older than the tenant's {@code retentionDays} are permanently
     *       deleted from the archive.</li>
     * </ol>
     */
    LifecycleResult runLifecycle();
}
