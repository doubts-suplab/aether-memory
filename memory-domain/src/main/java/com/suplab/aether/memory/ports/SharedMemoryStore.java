package com.suplab.aether.memory.ports;

import com.suplab.aether.memory.domain.MemoryScope;
import com.suplab.aether.memory.domain.MemoryType;
import com.suplab.aether.memory.domain.SharedMemory;

import java.util.List;
import java.util.UUID;

/**
 * Port interface for shared-memory persistence.
 *
 * <p>Implementations live in {@code memory-engine} (pgvector) and test stubs. The domain layer
 * never depends on any concrete implementation — only this interface. Every read and write is
 * scoped by {@link MemoryScope} so there is no cross-tenant or cross-team access path.</p>
 */
public interface SharedMemoryStore {

    /**
     * Persists a shared memory alongside its vector embedding.
     * Uses UPSERT semantics — calling with an existing ID updates the record.
     *
     * @param memory    the shared memory to persist
     * @param embedding the 384-dimension embedding vector for semantic search
     */
    void save(SharedMemory memory, float[] embedding);

    /**
     * Returns the {@code limit} memories most semantically similar to the query embedding for a
     * team, ordered by cosine distance ascending. Each returned memory is reinforced on read
     * using the supplied {@code reinforcementIncrement} (shared reinforcement).
     *
     * @param scope                  the owning tenant + team
     * @param queryEmbedding         the 384-dimension query vector
     * @param limit                  maximum number of results to return
     * @param reinforcementIncrement strength added to each retrieved memory (from tenant policy)
     * @return ordered list of similar memories (nearest first), reinforced
     */
    List<SharedMemory> findSimilar(MemoryScope scope, float[] queryEmbedding, int limit,
                                   double reinforcementIncrement);

    /**
     * Returns memories of a specific type for a team, ordered by strength descending then last
     * access descending. Each returned memory is reinforced on read.
     *
     * @param scope                  the owning tenant + team
     * @param type                   the memory type filter
     * @param limit                  maximum number of results to return
     * @param reinforcementIncrement strength added to each retrieved memory (from tenant policy)
     * @return ordered list of memories matching the type, reinforced
     */
    List<SharedMemory> findByType(MemoryScope scope, MemoryType type, int limit,
                                  double reinforcementIncrement);

    /**
     * Returns federatable memories (visibility {@code FEDERATED}) across all tenants that permit
     * federation, most semantically similar to the query embedding. Used only by the federation
     * service — application code should call the federation port, never this method directly.
     *
     * @param queryEmbedding the 384-dimension query vector
     * @param type           optional type filter; {@code null} matches all types
     * @param limit          maximum number of results to return
     * @return ordered list of federatable memories (nearest first); not reinforced
     */
    List<SharedMemory> findFederatable(float[] queryEmbedding, MemoryType type, int limit);

    /**
     * Hard-deletes a specific memory. The scope is required to prevent cross-team deletion.
     *
     * @param memoryId the UUID of the memory to delete
     * @param scope    the owner of the memory (enforces scoping)
     */
    void delete(UUID memoryId, MemoryScope scope);

    /**
     * Returns the total number of active memories stored for a team.
     *
     * @param scope the owning tenant + team
     * @return non-negative memory count
     */
    long countByTeam(MemoryScope scope);
}
