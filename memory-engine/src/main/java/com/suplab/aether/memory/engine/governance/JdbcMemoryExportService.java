package com.suplab.aether.memory.engine.governance;

import com.suplab.aether.memory.domain.ExportedMemory;
import com.suplab.aether.memory.domain.MemoryExport;
import com.suplab.aether.memory.domain.MemoryScope;
import com.suplab.aether.memory.domain.MemoryType;
import com.suplab.aether.memory.domain.MemoryVisibility;
import com.suplab.aether.memory.ports.MemoryExportPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JDBC {@link MemoryExportPort} — data-portability export across a team's active and archived
 * memories (GDPR Art. 20 / right-to-know).
 *
 * <p>Reads a team's rows from both {@code shared_memories} and {@code shared_memories_archive} with
 * plain, explicit-column {@code SELECT}s — <strong>non-reinforcing</strong> (unlike the store's
 * retrieval methods, it never touches strength or access counts). The embedding vector is
 * deliberately excluded (an internal search artefact, not portable data). Scoped by
 * {@code tenant_id} and {@code team_id}.</p>
 */
public class JdbcMemoryExportService implements MemoryExportPort {

    private static final Logger log = LoggerFactory.getLogger(JdbcMemoryExportService.class);

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcMemoryExportService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public MemoryExport exportTeam(MemoryScope scope) {
        var params = new MapSqlParameterSource()
                .addValue("tenantId", scope.tenantId())
                .addValue("teamId", scope.teamId());

        var active = jdbc.query("""
                SELECT id, memory_type, content, visibility, strength, access_count, contributor_count,
                       created_at, last_accessed_at
                FROM shared_memories
                WHERE tenant_id = :tenantId AND team_id = :teamId
                ORDER BY strength DESC, last_accessed_at DESC
                """, params, (rs, row) -> mapRow(rs, false));

        var archived = jdbc.query("""
                SELECT id, memory_type, content, visibility, strength, access_count, contributor_count,
                       created_at, last_accessed_at
                FROM shared_memories_archive
                WHERE tenant_id = :tenantId AND team_id = :teamId
                ORDER BY archived_at DESC
                """, params, (rs, row) -> mapRow(rs, true));

        var all = new ArrayList<ExportedMemory>(active.size() + archived.size());
        all.addAll(active);
        all.addAll(archived);

        log.info("Exported team memories tenantId={} teamId={} active={} archived={}",
                scope.tenantId(), scope.teamId(), active.size(), archived.size());
        return new MemoryExport(scope.tenantId(), scope.teamId(), active.size(), archived.size(), null, all);
    }

    private ExportedMemory mapRow(ResultSet rs, boolean archived) throws SQLException {
        return new ExportedMemory(
                UUID.fromString(rs.getString("id")),
                MemoryType.valueOf(rs.getString("memory_type")),
                rs.getString("content"),
                MemoryVisibility.valueOf(rs.getString("visibility")),
                rs.getDouble("strength"),
                rs.getInt("access_count"),
                rs.getInt("contributor_count"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("last_accessed_at").toInstant(),
                archived
        );
    }
}
