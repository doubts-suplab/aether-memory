package com.suplab.aether.memory.engine.lifecycle;

import com.suplab.aether.memory.ports.MemoryLifecyclePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Set-based JDBC implementation of {@link MemoryLifecyclePort} that resolves decay and archive
 * parameters <em>per tenant</em>.
 *
 * <p>Each statement {@code LEFT JOIN}s {@code memory_policies} and uses {@code COALESCE} to fall
 * back to the injected default constants for tenants with no configured policy. Both steps run
 * as single SQL statements — no per-row round trips — so a run over millions of memories stays
 * cheap. The archive step uses a data-modifying CTE ({@code WITH moved AS (DELETE ... RETURNING
 * ...) INSERT ...}) so move-to-archive is atomic: a memory is never deleted without landing in
 * the archive, and never duplicated.</p>
 */
public class PolicyAwareMemoryLifecycleService implements MemoryLifecyclePort {

    private static final Logger log = LoggerFactory.getLogger(PolicyAwareMemoryLifecycleService.class);

    private final NamedParameterJdbcTemplate jdbc;
    private final double defaultDecayRate;
    private final int defaultDecayAfterDays;
    private final double defaultArchiveThreshold;

    /**
     * @param defaultDecayRate        strength lost per idle day for tenants without a policy
     * @param defaultDecayAfterDays   grace period for tenants without a policy
     * @param defaultArchiveThreshold archive cutoff for tenants without a policy
     */
    public PolicyAwareMemoryLifecycleService(NamedParameterJdbcTemplate jdbc,
                                             double defaultDecayRate,
                                             int defaultDecayAfterDays,
                                             double defaultArchiveThreshold) {
        this.jdbc = jdbc;
        this.defaultDecayRate = defaultDecayRate;
        this.defaultDecayAfterDays = defaultDecayAfterDays;
        this.defaultArchiveThreshold = defaultArchiveThreshold;
    }

    @Override
    public LifecycleResult runLifecycle() {
        long decayed = decay();
        long archived = archive();
        long remaining = countActive();
        log.info("Shared-memory lifecycle run complete: decayed={} archived={} totalRemaining={}",
                decayed, archived, remaining);
        return new LifecycleResult(decayed, archived, remaining);
    }

    private long decay() {
        var sql = """
                UPDATE shared_memories sm
                SET strength = GREATEST(0,
                        sm.strength - COALESCE(mp.decay_rate, :defaultDecayRate)
                            * (EXTRACT(EPOCH FROM (NOW() - sm.last_accessed_at)) / 86400.0))
                FROM shared_memories tgt
                LEFT JOIN memory_policies mp ON mp.tenant_id = tgt.tenant_id
                WHERE sm.id = tgt.id
                  AND sm.last_accessed_at
                        < NOW() - make_interval(days => COALESCE(mp.decay_after_days, :defaultDecayAfterDays))
                """;
        var params = new MapSqlParameterSource()
                .addValue("defaultDecayRate", defaultDecayRate)
                .addValue("defaultDecayAfterDays", defaultDecayAfterDays);
        return jdbc.update(sql, params);
    }

    private long archive() {
        var sql = """
                WITH victims AS (
                    SELECT s.id
                    FROM shared_memories s
                    LEFT JOIN memory_policies mp ON mp.tenant_id = s.tenant_id
                    WHERE s.strength < COALESCE(mp.archive_threshold, :defaultArchiveThreshold)
                ),
                moved AS (
                    DELETE FROM shared_memories sm
                    USING victims
                    WHERE sm.id = victims.id
                    RETURNING sm.id, sm.tenant_id, sm.team_id, sm.memory_type, sm.content,
                              sm.visibility, sm.embedding, sm.strength, sm.access_count,
                              sm.contributor_count, sm.created_at, sm.last_accessed_at
                )
                INSERT INTO shared_memories_archive
                    (id, tenant_id, team_id, memory_type, content, visibility, embedding, strength,
                     access_count, contributor_count, created_at, last_accessed_at, archived_at)
                SELECT id, tenant_id, team_id, memory_type, content, visibility, embedding, strength,
                       access_count, contributor_count, created_at, last_accessed_at, NOW()
                FROM moved
                """;
        var params = new MapSqlParameterSource("defaultArchiveThreshold", defaultArchiveThreshold);
        return jdbc.update(sql, params);
    }

    private long countActive() {
        var sql = "SELECT COUNT(*) FROM shared_memories";
        Long count = jdbc.queryForObject(sql, new MapSqlParameterSource(), Long.class);
        return count != null ? count : 0L;
    }
}
