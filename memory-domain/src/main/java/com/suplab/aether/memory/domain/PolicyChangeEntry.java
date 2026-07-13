package com.suplab.aether.memory.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * An audit record capturing a full snapshot of a tenant's {@link MemoryPolicy} at the moment it
 * was replaced. One row is written per policy change so governance can see how a tenant's decay,
 * retention, and federation settings evolved over time.
 *
 * @param id       audit id
 * @param policy   the policy as it was set
 * @param changedAt when the change was applied
 */
public record PolicyChangeEntry(
        UUID id,
        MemoryPolicy policy,
        Instant changedAt
) {
    public PolicyChangeEntry {
        if (id == null) id = UUID.randomUUID();
        if (policy == null) throw new IllegalArgumentException("policy required");
        if (changedAt == null) changedAt = Instant.now();
    }

    /**
     * Factory for a fresh change entry stamped at {@link Instant#now()}.
     */
    public static PolicyChangeEntry of(MemoryPolicy policy) {
        return new PolicyChangeEntry(UUID.randomUUID(), policy, Instant.now());
    }
}
