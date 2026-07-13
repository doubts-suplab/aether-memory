package com.suplab.aether.memory.engine.governance;

import com.suplab.aether.memory.domain.ErasureAuditEntry;
import com.suplab.aether.memory.domain.ErasureScope;
import com.suplab.aether.memory.domain.MemoryScope;
import com.suplab.aether.memory.domain.MemoryType;
import com.suplab.aether.memory.domain.MemoryVisibility;
import com.suplab.aether.memory.domain.SharedMemory;
import com.suplab.aether.memory.ports.MemoryGovernancePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

/**
 * JDBC implementation of {@link MemoryGovernancePort} — GDPR right-to-erasure and data export.
 *
 * <p>Erasure is {@link Transactional}: the deletes across {@code shared_memories} and
 * {@code shared_memories_archive} (and, for tenant erasure, {@code federation_audit}) and the
 * {@code gdpr_erasure_audit} insert all commit together or not at all. A caller therefore never
 * observes a half-erased team, and a proof-of-erasure record always accompanies a successful
 * deletion.</p>
 */
public class JdbcMemoryGovernanceService implements MemoryGovernancePort {

    private static final Logger log = LoggerFactory.getLogger(JdbcMemoryGovernanceService.class);

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcMemoryGovernanceService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public ErasureResult eraseTeam(MemoryScope scope) {
        var params = new MapSqlParameterSource()
                .addValue("tenantId", scope.tenantId())
                .addValue("teamId", scope.teamId());
        long archived = jdbc.update("""
                DELETE FROM shared_memories_archive
                WHERE tenant_id = :tenantId AND team_id = :teamId
                """, params);
        long active = jdbc.update("""
                DELETE FROM shared_memories
                WHERE tenant_id = :tenantId AND team_id = :teamId
                """, params);
        recordErasure(new ErasureAuditEntry(UUID.randomUUID(), scope.tenantId(), scope.teamId(),
                ErasureScope.TEAM, active, archived, java.time.Instant.now()));
        log.info("GDPR team erasure tenantId={} teamId={} active={} archived={}",
                scope.tenantId(), scope.teamId(), active, archived);
        return new ErasureResult(active, archived);
    }

    @Override
    @Transactional
    public ErasureResult eraseTenant(String tenantId) {
        var params = new MapSqlParameterSource("tenantId", tenantId);
        long archived = jdbc.update(
                "DELETE FROM shared_memories_archive WHERE tenant_id = :tenantId", params);
        long active = jdbc.update(
                "DELETE FROM shared_memories WHERE tenant_id = :tenantId", params);
        jdbc.update("DELETE FROM federation_audit WHERE origin_tenant_id = :tenantId", params);
        recordErasure(new ErasureAuditEntry(UUID.randomUUID(), tenantId, null,
                ErasureScope.TENANT, active, archived, java.time.Instant.now()));
        log.info("GDPR tenant erasure tenantId={} active={} archived={}", tenantId, active, archived);
        return new ErasureResult(active, archived);
    }

    @Override
    public List<SharedMemory> exportTeam(MemoryScope scope) {
        var sql = """
                SELECT id, tenant_id, team_id, memory_type, content, visibility, strength,
                       access_count, contributor_count, created_at, last_accessed_at
                FROM shared_memories
                WHERE tenant_id = :tenantId AND team_id = :teamId
                ORDER BY created_at ASC
                """;
        var params = new MapSqlParameterSource()
                .addValue("tenantId", scope.tenantId())
                .addValue("teamId", scope.teamId());
        return jdbc.query(sql, params, JdbcMemoryGovernanceService::mapMemory);
    }

    @Override
    public List<ErasureAuditEntry> recentErasures(String tenantId, int limit) {
        var sql = """
                SELECT id, tenant_id, team_id, erasure_scope, active_deleted, archived_deleted, occurred_at
                FROM gdpr_erasure_audit
                WHERE tenant_id = :tenantId
                ORDER BY occurred_at DESC
                LIMIT :limit
                """;
        var params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("limit", limit);
        return jdbc.query(sql, params, JdbcMemoryGovernanceService::mapErasure);
    }

    private void recordErasure(ErasureAuditEntry entry) {
        var sql = """
                INSERT INTO gdpr_erasure_audit
                    (id, tenant_id, team_id, erasure_scope, active_deleted, archived_deleted, occurred_at)
                VALUES
                    (:id, :tenantId, :teamId, :scope, :activeDeleted, :archivedDeleted, :occurredAt)
                """;
        var params = new MapSqlParameterSource()
                .addValue("id", entry.id())
                .addValue("tenantId", entry.tenantId())
                .addValue("teamId", entry.teamId())
                .addValue("scope", entry.scope().name())
                .addValue("activeDeleted", entry.activeDeleted())
                .addValue("archivedDeleted", entry.archivedDeleted())
                .addValue("occurredAt", Timestamp.from(entry.occurredAt()));
        jdbc.update(sql, params);
    }

    private static SharedMemory mapMemory(ResultSet rs, int row) throws SQLException {
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
                rs.getTimestamp("last_accessed_at").toInstant());
    }

    private static ErasureAuditEntry mapErasure(ResultSet rs, int row) throws SQLException {
        return new ErasureAuditEntry(
                UUID.fromString(rs.getString("id")),
                rs.getString("tenant_id"),
                rs.getString("team_id"),
                ErasureScope.valueOf(rs.getString("erasure_scope")),
                rs.getLong("active_deleted"),
                rs.getLong("archived_deleted"),
                rs.getTimestamp("occurred_at").toInstant());
    }
}
