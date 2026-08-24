package com.suplab.aether.memory.engine.governance;

import com.suplab.aether.memory.domain.MemoryErasureResult;
import com.suplab.aether.memory.domain.MemoryScope;
import com.suplab.aether.memory.ports.MemoryErasurePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * JDBC {@link MemoryErasurePort} — right-to-erasure across a team's active and archived memories
 * (GDPR Art. 17).
 *
 * <p>Deletes a team's rows from both {@code shared_memories} and {@code shared_memories_archive} in
 * tenant + team scope, and reports the counts removed from each. Idempotent — a team with nothing
 * stored returns zero counts. Uses {@code NamedParameterJdbcTemplate} with parameterized queries;
 * every statement is scoped by {@code tenant_id} and {@code team_id}, so there is no cross-team
 * deletion path.</p>
 */
public class JdbcMemoryErasureService implements MemoryErasurePort {

    private static final Logger log = LoggerFactory.getLogger(JdbcMemoryErasureService.class);

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcMemoryErasureService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public MemoryErasureResult eraseTeam(MemoryScope scope) {
        var params = new MapSqlParameterSource()
                .addValue("tenantId", scope.tenantId())
                .addValue("teamId", scope.teamId());
        int activeErased = jdbc.update("""
                DELETE FROM shared_memories
                WHERE tenant_id = :tenantId AND team_id = :teamId
                """, params);
        int archivedErased = jdbc.update("""
                DELETE FROM shared_memories_archive
                WHERE tenant_id = :tenantId AND team_id = :teamId
                """, params);

        var result = new MemoryErasureResult(scope.tenantId(), scope.teamId(), activeErased, archivedErased);
        if (result.isEmpty()) {
            log.info("Erasure requested for tenantId={} teamId={} — nothing to erase",
                    scope.tenantId(), scope.teamId());
        } else {
            log.info("Erased team memories tenantId={} teamId={} active={} archived={}",
                    scope.tenantId(), scope.teamId(), activeErased, archivedErased);
        }
        return result;
    }
}
