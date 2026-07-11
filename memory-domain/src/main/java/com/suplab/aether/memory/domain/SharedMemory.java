package com.suplab.aether.memory.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * A single shared memory owned by a team within a tenant.
 *
 * <p>Where Aether Core's {@code PersonalMemory} belongs to one user, a {@code SharedMemory}
 * belongs to a <em>group</em>. Its strength is shaped by <em>collective</em> access:
 * every team member who retrieves it reinforces it ({@link #reinforce(double)}), and every
 * distinct member who re-asserts it raises its {@code contributorCount}
 * ({@link #contribute(double)}) — a memory many people rely on stays vivid.</p>
 *
 * <p>Strength ranges from 0.0 (faded) to 1.0 (vivid). Idle memories decay via a policy-driven
 * scheduler and, once below the tenant's archive threshold, are moved to the archive rather
 * than deleted. All fields are immutable; mutating operations return new instances.</p>
 */
public record SharedMemory(
        UUID id,
        String tenantId,
        String teamId,
        MemoryType type,
        String content,
        MemoryVisibility visibility,
        double strength,
        int accessCount,
        int contributorCount,
        Instant createdAt,
        Instant lastAccessedAt
) {
    public SharedMemory {
        if (id == null) id = UUID.randomUUID();
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId required");
        if (teamId == null || teamId.isBlank()) throw new IllegalArgumentException("teamId required");
        if (type == null) throw new IllegalArgumentException("type required");
        if (content == null || content.isBlank()) throw new IllegalArgumentException("content required");
        if (visibility == null) visibility = MemoryVisibility.PRIVATE;
        if (strength < 0 || strength > 1) throw new IllegalArgumentException("strength must be 0-1");
        if (contributorCount < 1) throw new IllegalArgumentException("contributorCount must be >= 1");
        if (createdAt == null) createdAt = Instant.now();
        if (lastAccessedAt == null) lastAccessedAt = createdAt;
    }

    /**
     * Factory for new shared memories. Assigns a random ID, sets initial strength to 1.0,
     * and records the founding contributor (count 1).
     */
    public static SharedMemory create(MemoryScope scope, MemoryType type, String content,
                                      MemoryVisibility visibility) {
        return new SharedMemory(UUID.randomUUID(), scope.tenantId(), scope.teamId(), type, content,
                visibility, 1.0, 0, 1, Instant.now(), Instant.now());
    }

    /**
     * Returns the owning scope ({@code tenantId} + {@code teamId}) of this memory.
     */
    public MemoryScope scope() {
        return new MemoryScope(tenantId, teamId);
    }

    /**
     * Returns a reinforced copy: strength increased by {@code increment} (capped at 1.0),
     * {@code accessCount} incremented, {@code lastAccessedAt} refreshed. Call on every
     * successful team retrieval. The increment is supplied by the tenant's {@link MemoryPolicy}
     * so different tenants can tune how quickly shared memories consolidate.
     *
     * @param increment strength gained per access (must be >= 0)
     */
    public SharedMemory reinforce(double increment) {
        if (increment < 0) throw new IllegalArgumentException("increment must be >= 0");
        double newStrength = Math.min(1.0, strength + increment);
        return new SharedMemory(id, tenantId, teamId, type, content, visibility, newStrength,
                accessCount + 1, contributorCount, createdAt, Instant.now());
    }

    /**
     * Records a distinct additional contributor re-asserting this memory: {@code contributorCount}
     * is incremented and strength is reinforced by {@code increment}. This is the "shared
     * reinforcement" signal — the more members that independently hold a memory, the stronger it
     * becomes.
     *
     * @param increment strength gained from the new contributor (must be >= 0)
     */
    public SharedMemory contribute(double increment) {
        if (increment < 0) throw new IllegalArgumentException("increment must be >= 0");
        double newStrength = Math.min(1.0, strength + increment);
        return new SharedMemory(id, tenantId, teamId, type, content, visibility, newStrength,
                accessCount, contributorCount + 1, createdAt, Instant.now());
    }

    /**
     * Returns a copy at a new visibility level — e.g. promoting a {@code TENANT} memory to
     * {@code FEDERATED} once it is deemed safe to share across instances.
     */
    public SharedMemory withVisibility(MemoryVisibility newVisibility) {
        if (newVisibility == null) throw new IllegalArgumentException("visibility required");
        return new SharedMemory(id, tenantId, teamId, type, content, newVisibility, strength,
                accessCount, contributorCount, createdAt, lastAccessedAt);
    }
}
