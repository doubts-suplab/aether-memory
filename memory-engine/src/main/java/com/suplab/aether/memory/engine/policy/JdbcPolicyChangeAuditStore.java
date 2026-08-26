package com.suplab.aether.memory.engine.policy;

import com.suplab.aether.memory.domain.PolicyChangeEvent;
import com.suplab.aether.memory.ports.PolicyChangeAuditStore;
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
 * JDBC implementation of {@link PolicyChangeAuditStore} backed by the append-only
 * {@code policy_change_audit} table.
 *
 * <p>Write-once: only {@code INSERT} and a bounded, tenant-scoped {@code SELECT} of recent events —
 * no update or delete path. Explicit column lists and named parameters throughout. The stored actor
 * and summary are already bounded by {@link PolicyChangeEvent}; no secrets or memory content are
 * recorded.</p>
 */
public class JdbcPolicyChangeAuditStore implements PolicyChangeAuditStore {

    private static final Logger log = LoggerFactory.getLogger(JdbcPolicyChangeAuditStore.class);

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcPolicyChangeAuditStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void record(PolicyChangeEvent event) {
        var sql = """
                INSERT INTO policy_change_audit
                    (id, tenant_id, actor, change_summary, occurred_at)
                VALUES
                    (:id, :tenantId, :actor, :changeSummary, :occurredAt)
                """;
        var params = new MapSqlParameterSource()
                .addValue("id", event.id())
                .addValue("tenantId", event.tenantId())
                .addValue("actor", event.actor())
                .addValue("changeSummary", event.summary())
                .addValue("occurredAt", Timestamp.from(event.occurredAt()));
        jdbc.update(sql, params);
        log.debug("Recorded policy-change audit id={} tenantId={}", event.id(), event.tenantId());
    }

    @Override
    public List<PolicyChangeEvent> recentForTenant(String tenantId, int limit) {
        var sql = """
                SELECT id, tenant_id, actor, change_summary, occurred_at
                FROM policy_change_audit
                WHERE tenant_id = :tenantId
                ORDER BY occurred_at DESC
                LIMIT :limit
                """;
        var params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("limit", limit);
        return jdbc.query(sql, params, this::mapRow);
    }

    private PolicyChangeEvent mapRow(ResultSet rs, int row) throws SQLException {
        return new PolicyChangeEvent(
                UUID.fromString(rs.getString("id")),
                rs.getString("tenant_id"),
                rs.getString("actor"),
                rs.getString("change_summary"),
                rs.getTimestamp("occurred_at").toInstant()
        );
    }
}
