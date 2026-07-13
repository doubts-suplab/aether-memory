package com.suplab.aether.memory.engine.federation;

import com.suplab.aether.memory.domain.FederationAuditEntry;
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
 * JDBC implementation of {@link FederationAuditStore} backed by the {@code federation_audit} table.
 */
public class JdbcFederationAuditStore implements FederationAuditStore {

    private static final Logger log = LoggerFactory.getLogger(JdbcFederationAuditStore.class);

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcFederationAuditStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void record(FederationAuditEntry entry) {
        var sql = """
                INSERT INTO federation_audit
                    (id, origin_tenant_id, query_text, memory_type, result_count, local_only, occurred_at)
                VALUES
                    (:id, :originTenantId, :queryText, :memoryType, :resultCount, :localOnly, :occurredAt)
                """;
        var params = new MapSqlParameterSource()
                .addValue("id", entry.id())
                .addValue("originTenantId", entry.originTenantId())
                .addValue("queryText", entry.queryText())
                .addValue("memoryType", entry.type() != null ? entry.type().name() : null)
                .addValue("resultCount", entry.resultCount())
                .addValue("localOnly", entry.localOnly())
                .addValue("occurredAt", Timestamp.from(entry.occurredAt()));
        jdbc.update(sql, params);
        log.debug("Recorded federation audit id={} originTenantId={} resultCount={} localOnly={}",
                entry.id(), entry.originTenantId(), entry.resultCount(), entry.localOnly());
    }

    @Override
    public List<FederationAuditEntry> recentFor(String originTenantId, int limit) {
        var sql = """
                SELECT id, origin_tenant_id, query_text, memory_type, result_count, local_only, occurred_at
                FROM federation_audit
                WHERE origin_tenant_id = :originTenantId
                ORDER BY occurred_at DESC
                LIMIT :limit
                """;
        var params = new MapSqlParameterSource()
                .addValue("originTenantId", originTenantId)
                .addValue("limit", limit);
        return jdbc.query(sql, params, this::mapRow);
    }

    private FederationAuditEntry mapRow(ResultSet rs, int row) throws SQLException {
        var typeRaw = rs.getString("memory_type");
        return new FederationAuditEntry(
                UUID.fromString(rs.getString("id")),
                rs.getString("origin_tenant_id"),
                rs.getString("query_text"),
                typeRaw != null ? MemoryType.valueOf(typeRaw) : null,
                rs.getInt("result_count"),
                rs.getBoolean("local_only"),
                rs.getTimestamp("occurred_at").toInstant()
        );
    }
}
