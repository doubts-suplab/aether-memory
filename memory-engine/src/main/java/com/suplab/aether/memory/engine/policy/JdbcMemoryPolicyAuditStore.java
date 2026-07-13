package com.suplab.aether.memory.engine.policy;

import com.suplab.aether.memory.domain.MemoryPolicy;
import com.suplab.aether.memory.domain.PolicyChangeEntry;
import com.suplab.aether.memory.ports.MemoryPolicyAuditStore;
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
 * JDBC implementation of {@link MemoryPolicyAuditStore} backed by the {@code policy_change_audit}
 * table. Stores a full policy snapshot per change.
 */
public class JdbcMemoryPolicyAuditStore implements MemoryPolicyAuditStore {

    private static final Logger log = LoggerFactory.getLogger(JdbcMemoryPolicyAuditStore.class);

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcMemoryPolicyAuditStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void record(PolicyChangeEntry entry) {
        var policy = entry.policy();
        var sql = """
                INSERT INTO policy_change_audit
                    (id, tenant_id, decay_rate, decay_after_days, reinforcement_increment,
                     archive_threshold, retention_days, federation_enabled,
                     federation_max_summary_length, changed_at)
                VALUES
                    (:id, :tenantId, :decayRate, :decayAfterDays, :reinforcementIncrement,
                     :archiveThreshold, :retentionDays, :federationEnabled,
                     :federationMaxSummaryLength, :changedAt)
                """;
        var params = new MapSqlParameterSource()
                .addValue("id", entry.id())
                .addValue("tenantId", policy.tenantId())
                .addValue("decayRate", policy.decayRate())
                .addValue("decayAfterDays", policy.decayAfterDays())
                .addValue("reinforcementIncrement", policy.reinforcementIncrement())
                .addValue("archiveThreshold", policy.archiveThreshold())
                .addValue("retentionDays", policy.retentionDays())
                .addValue("federationEnabled", policy.federationEnabled())
                .addValue("federationMaxSummaryLength", policy.federationMaxSummaryLength())
                .addValue("changedAt", Timestamp.from(entry.changedAt()));
        jdbc.update(sql, params);
        log.debug("Recorded policy change audit id={} tenantId={}", entry.id(), policy.tenantId());
    }

    @Override
    public List<PolicyChangeEntry> recentFor(String tenantId, int limit) {
        var sql = """
                SELECT id, tenant_id, decay_rate, decay_after_days, reinforcement_increment,
                       archive_threshold, retention_days, federation_enabled,
                       federation_max_summary_length, changed_at
                FROM policy_change_audit
                WHERE tenant_id = :tenantId
                ORDER BY changed_at DESC
                LIMIT :limit
                """;
        var params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("limit", limit);
        return jdbc.query(sql, params, this::mapRow);
    }

    private PolicyChangeEntry mapRow(ResultSet rs, int row) throws SQLException {
        var policy = new MemoryPolicy(
                rs.getString("tenant_id"),
                rs.getDouble("decay_rate"),
                rs.getInt("decay_after_days"),
                rs.getDouble("reinforcement_increment"),
                rs.getDouble("archive_threshold"),
                rs.getInt("retention_days"),
                rs.getBoolean("federation_enabled"),
                rs.getInt("federation_max_summary_length"));
        return new PolicyChangeEntry(
                UUID.fromString(rs.getString("id")),
                policy,
                rs.getTimestamp("changed_at").toInstant());
    }
}
