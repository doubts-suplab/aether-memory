package com.suplab.aether.memory.engine.policy;

import com.suplab.aether.memory.domain.MemoryPolicy;
import com.suplab.aether.memory.ports.MemoryPolicyStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * JDBC implementation of {@link MemoryPolicyStore} backed by the {@code memory_policies} table.
 *
 * <p>Only explicitly configured policies are stored. {@link #resolve(String)} returns
 * {@link MemoryPolicy#defaults(String)} for any tenant without a row, so callers never have to
 * handle a missing policy.</p>
 */
public class JdbcMemoryPolicyStore implements MemoryPolicyStore {

    private static final Logger log = LoggerFactory.getLogger(JdbcMemoryPolicyStore.class);

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcMemoryPolicyStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public MemoryPolicy resolve(String tenantId) {
        var sql = """
                SELECT tenant_id, decay_rate, decay_after_days, reinforcement_increment,
                       archive_threshold, retention_days, federation_enabled
                FROM memory_policies
                WHERE tenant_id = :tenantId
                """;
        var params = new MapSqlParameterSource("tenantId", tenantId);
        var policies = jdbc.query(sql, params, this::mapRow);
        if (policies.isEmpty()) {
            return MemoryPolicy.defaults(tenantId);
        }
        return policies.getFirst();
    }

    @Override
    public void save(MemoryPolicy policy) {
        var sql = """
                INSERT INTO memory_policies
                    (tenant_id, decay_rate, decay_after_days, reinforcement_increment,
                     archive_threshold, retention_days, federation_enabled, updated_at)
                VALUES
                    (:tenantId, :decayRate, :decayAfterDays, :reinforcementIncrement,
                     :archiveThreshold, :retentionDays, :federationEnabled, NOW())
                ON CONFLICT (tenant_id) DO UPDATE SET
                    decay_rate = EXCLUDED.decay_rate,
                    decay_after_days = EXCLUDED.decay_after_days,
                    reinforcement_increment = EXCLUDED.reinforcement_increment,
                    archive_threshold = EXCLUDED.archive_threshold,
                    retention_days = EXCLUDED.retention_days,
                    federation_enabled = EXCLUDED.federation_enabled,
                    updated_at = NOW()
                """;
        var params = new MapSqlParameterSource()
                .addValue("tenantId", policy.tenantId())
                .addValue("decayRate", policy.decayRate())
                .addValue("decayAfterDays", policy.decayAfterDays())
                .addValue("reinforcementIncrement", policy.reinforcementIncrement())
                .addValue("archiveThreshold", policy.archiveThreshold())
                .addValue("retentionDays", policy.retentionDays())
                .addValue("federationEnabled", policy.federationEnabled());
        jdbc.update(sql, params);
        log.info("Saved memory policy tenantId={} decayRate={} federationEnabled={}",
                policy.tenantId(), policy.decayRate(), policy.federationEnabled());
    }

    @Override
    public List<MemoryPolicy> findAll() {
        var sql = """
                SELECT tenant_id, decay_rate, decay_after_days, reinforcement_increment,
                       archive_threshold, retention_days, federation_enabled
                FROM memory_policies
                ORDER BY tenant_id
                """;
        return jdbc.query(sql, this::mapRow);
    }

    private MemoryPolicy mapRow(ResultSet rs, int row) throws SQLException {
        return new MemoryPolicy(
                rs.getString("tenant_id"),
                rs.getDouble("decay_rate"),
                rs.getInt("decay_after_days"),
                rs.getDouble("reinforcement_increment"),
                rs.getDouble("archive_threshold"),
                rs.getInt("retention_days"),
                rs.getBoolean("federation_enabled")
        );
    }
}
