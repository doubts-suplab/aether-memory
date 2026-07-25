package com.suplab.aether.memory.engine.federation;

import com.suplab.aether.memory.domain.FederationAuditEvent;
import com.suplab.aether.memory.domain.MemoryType;
import com.suplab.aether.memory.ports.FederationAuditStore;
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
 * JDBC implementation of {@link FederationAuditStore} backed by the append-only
 * {@code federation_audit} table.
 *
 * <p>Write-once: only {@code INSERT} and a bounded {@code SELECT} of recent events — no update or
 * delete path. Explicit column lists and named parameters throughout. The stored query label is
 * already bounded by {@link FederationAuditEvent}; no memory content or team identity is recorded.</p>
 */
public class JdbcFederationAuditStore implements FederationAuditStore {

    private static final Logger log = LoggerFactory.getLogger(JdbcFederationAuditStore.class);

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcFederationAuditStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void record(FederationAuditEvent event) {
        var sql = """
                INSERT INTO federation_audit
                    (id, origin_tenant_id, memory_type, query_label, result_count, occurred_at)
                VALUES
                    (:id, :originTenantId, :memoryType, :queryLabel, :resultCount, :occurredAt)
                """;
        var params = new MapSqlParameterSource()
                .addValue("id", event.id())
                .addValue("originTenantId", event.originTenantId())
                .addValue("memoryType", event.type() != null ? event.type().name() : null)
                .addValue("queryLabel", event.queryLabel())
                .addValue("resultCount", event.resultCount())
                .addValue("occurredAt", Timestamp.from(event.occurredAt()));
        jdbc.update(sql, params);
        log.debug("Recorded federation audit id={} originTenantId={} results={}",
                event.id(), event.originTenantId(), event.resultCount());
    }

    @Override
    public List<FederationAuditEvent> recent(int limit) {
        var sql = """
                SELECT id, origin_tenant_id, memory_type, query_label, result_count, occurred_at
                FROM federation_audit
                ORDER BY occurred_at DESC
                LIMIT :limit
                """;
        var params = new MapSqlParameterSource().addValue("limit", limit);
        return jdbc.query(sql, params, this::mapRow);
    }

    private FederationAuditEvent mapRow(ResultSet rs, int row) throws SQLException {
        var typeRaw = rs.getString("memory_type");
        return new FederationAuditEvent(
                UUID.fromString(rs.getString("id")),
                rs.getString("origin_tenant_id"),
                typeRaw != null ? MemoryType.valueOf(typeRaw) : null,
                rs.getString("query_label"),
                rs.getInt("result_count"),
                rs.getTimestamp("occurred_at").toInstant()
        );
    }
}
