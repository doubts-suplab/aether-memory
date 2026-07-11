package com.suplab.aether.memory.engine.store;

import com.suplab.aether.memory.domain.MemoryScope;
import com.suplab.aether.memory.domain.MemoryType;
import com.suplab.aether.memory.domain.MemoryVisibility;
import com.suplab.aether.memory.domain.SharedMemory;
import com.suplab.aether.memory.ports.SharedMemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

/**
 * pgvector-backed implementation of {@link SharedMemoryStore}.
 *
 * <p>Vector embeddings are stored in a {@code vector(384)} column using the pgvector
 * extension. The {@code <=>} operator provides cosine distance ordering for semantic
 * similarity search. Embeddings are serialised as {@code [x,y,z,...]} strings and cast to
 * {@code ::vector} in the SQL, which pgvector parses at query time.</p>
 *
 * <p>Reads reinforce on access (shared reinforcement): every team retrieval strengthens the
 * memory by the tenant's configured increment, so memories the team relies on stay vivid.</p>
 */
public class PGVectorSharedMemoryStore implements SharedMemoryStore {

    private static final Logger log = LoggerFactory.getLogger(PGVectorSharedMemoryStore.class);

    private final NamedParameterJdbcTemplate jdbc;

    public PGVectorSharedMemoryStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(SharedMemory memory, float[] embedding) {
        var sql = """
                INSERT INTO shared_memories
                    (id, tenant_id, team_id, memory_type, content, visibility, embedding,
                     strength, access_count, contributor_count, created_at, last_accessed_at)
                VALUES
                    (:id, :tenantId, :teamId, :memoryType, :content, :visibility, :embedding::vector,
                     :strength, :accessCount, :contributorCount, :createdAt, :lastAccessedAt)
                ON CONFLICT (id) DO UPDATE SET
                    content = EXCLUDED.content,
                    visibility = EXCLUDED.visibility,
                    strength = EXCLUDED.strength,
                    access_count = EXCLUDED.access_count,
                    contributor_count = EXCLUDED.contributor_count,
                    last_accessed_at = EXCLUDED.last_accessed_at
                """;
        var params = new MapSqlParameterSource()
                .addValue("id", memory.id())
                .addValue("tenantId", memory.tenantId())
                .addValue("teamId", memory.teamId())
                .addValue("memoryType", memory.type().name())
                .addValue("content", memory.content())
                .addValue("visibility", memory.visibility().name())
                .addValue("embedding", toVectorString(embedding))
                .addValue("strength", memory.strength())
                .addValue("accessCount", memory.accessCount())
                .addValue("contributorCount", memory.contributorCount())
                .addValue("createdAt", Timestamp.from(memory.createdAt()))
                .addValue("lastAccessedAt", Timestamp.from(memory.lastAccessedAt()));
        jdbc.update(sql, params);
        log.debug("Saved shared memory id={} tenantId={} teamId={} type={} visibility={}",
                memory.id(), memory.tenantId(), memory.teamId(), memory.type(), memory.visibility());
    }

    @Override
    public List<SharedMemory> findSimilar(MemoryScope scope, float[] queryEmbedding, int limit,
                                          double reinforcementIncrement) {
        var sql = """
                SELECT id, tenant_id, team_id, memory_type, content, visibility, strength,
                       access_count, contributor_count, created_at, last_accessed_at
                FROM shared_memories
                WHERE tenant_id = :tenantId AND team_id = :teamId
                ORDER BY embedding <=> :query::vector
                LIMIT :limit
                """;
        var params = new MapSqlParameterSource()
                .addValue("tenantId", scope.tenantId())
                .addValue("teamId", scope.teamId())
                .addValue("query", toVectorString(queryEmbedding))
                .addValue("limit", limit);
        var memories = jdbc.query(sql, params, this::mapRow);
        return memories.stream().map(m -> reinforceAndPersist(m, reinforcementIncrement)).toList();
    }

    @Override
    public List<SharedMemory> findByType(MemoryScope scope, MemoryType type, int limit,
                                         double reinforcementIncrement) {
        var sql = """
                SELECT id, tenant_id, team_id, memory_type, content, visibility, strength,
                       access_count, contributor_count, created_at, last_accessed_at
                FROM shared_memories
                WHERE tenant_id = :tenantId AND team_id = :teamId AND memory_type = :memoryType
                ORDER BY strength DESC, last_accessed_at DESC
                LIMIT :limit
                """;
        var params = new MapSqlParameterSource()
                .addValue("tenantId", scope.tenantId())
                .addValue("teamId", scope.teamId())
                .addValue("memoryType", type.name())
                .addValue("limit", limit);
        var memories = jdbc.query(sql, params, this::mapRow);
        return memories.stream().map(m -> reinforceAndPersist(m, reinforcementIncrement)).toList();
    }

    @Override
    public List<SharedMemory> findFederatable(float[] queryEmbedding, MemoryType type, int limit) {
        var sql = """
                SELECT sm.id, sm.tenant_id, sm.team_id, sm.memory_type, sm.content, sm.visibility,
                       sm.strength, sm.access_count, sm.contributor_count, sm.created_at,
                       sm.last_accessed_at
                FROM shared_memories sm
                JOIN memory_policies mp ON mp.tenant_id = sm.tenant_id
                WHERE sm.visibility = 'FEDERATED'
                  AND mp.federation_enabled = TRUE
                  AND (:memoryType IS NULL OR sm.memory_type = :memoryType)
                ORDER BY sm.embedding <=> :query::vector
                LIMIT :limit
                """;
        var params = new MapSqlParameterSource()
                .addValue("memoryType", type != null ? type.name() : null)
                .addValue("query", toVectorString(queryEmbedding))
                .addValue("limit", limit);
        // Federation reads do not reinforce — the querying tenant is not the owning team.
        return jdbc.query(sql, params, this::mapRow);
    }

    private SharedMemory reinforceAndPersist(SharedMemory memory, double reinforcementIncrement) {
        var reinforced = memory.reinforce(reinforcementIncrement);
        var sql = """
                UPDATE shared_memories
                SET strength = :strength, access_count = :accessCount,
                    last_accessed_at = :lastAccessedAt
                WHERE id = :id AND tenant_id = :tenantId AND team_id = :teamId
                """;
        var params = new MapSqlParameterSource()
                .addValue("id", reinforced.id())
                .addValue("tenantId", reinforced.tenantId())
                .addValue("teamId", reinforced.teamId())
                .addValue("strength", reinforced.strength())
                .addValue("accessCount", reinforced.accessCount())
                .addValue("lastAccessedAt", Timestamp.from(reinforced.lastAccessedAt()));
        jdbc.update(sql, params);
        log.debug("Reinforced shared memory id={} strength={}", reinforced.id(), reinforced.strength());
        return reinforced;
    }

    private SharedMemory mapRow(ResultSet rs, int row) throws SQLException {
        return new SharedMemory(
                UUID.fromString(rs.getString("id")),
                rs.getString("tenant_id"),
                rs.getString("team_id"),
                MemoryType.valueOf(rs.getString("memory_type")),
                rs.getString("content"),
                MemoryVisibility.valueOf(rs.getString("visibility")),
                rs.getDouble("strength"),
                rs.getInt("access_count"),
                rs.getInt("contributor_count"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("last_accessed_at").toInstant()
        );
    }

    @Override
    public void delete(UUID memoryId, MemoryScope scope) {
        var sql = """
                DELETE FROM shared_memories
                WHERE id = :id AND tenant_id = :tenantId AND team_id = :teamId
                """;
        var params = new MapSqlParameterSource()
                .addValue("id", memoryId)
                .addValue("tenantId", scope.tenantId())
                .addValue("teamId", scope.teamId());
        int deleted = jdbc.update(sql, params);
        log.debug("Deleted {} shared memory record(s) memoryId={} tenantId={} teamId={}",
                deleted, memoryId, scope.tenantId(), scope.teamId());
    }

    @Override
    public long countByTeam(MemoryScope scope) {
        var sql = """
                SELECT COUNT(*) FROM shared_memories
                WHERE tenant_id = :tenantId AND team_id = :teamId
                """;
        var params = new MapSqlParameterSource()
                .addValue("tenantId", scope.tenantId())
                .addValue("teamId", scope.teamId());
        Long count = jdbc.queryForObject(sql, params, Long.class);
        return count != null ? count : 0L;
    }

    /**
     * Converts a float array to the {@code [x,y,z,...]} string format expected by pgvector's
     * {@code ::vector} cast operator.
     */
    static String toVectorString(float[] vec) {
        var sb = new StringBuilder("[");
        for (int i = 0; i < vec.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(vec[i]);
        }
        return sb.append(']').toString();
    }
}
