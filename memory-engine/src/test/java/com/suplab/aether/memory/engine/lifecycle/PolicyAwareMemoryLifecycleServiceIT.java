package com.suplab.aether.memory.engine.lifecycle;

import com.suplab.aether.memory.domain.MemoryScope;
import com.suplab.aether.memory.domain.MemoryType;
import com.suplab.aether.memory.domain.MemoryVisibility;
import com.suplab.aether.memory.domain.SharedMemory;
import com.suplab.aether.memory.engine.store.PGVectorSharedMemoryStore;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@Testcontainers
class PolicyAwareMemoryLifecycleServiceIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("aether_memory_test")
            .withUsername("aether")
            .withPassword("aether");

    private NamedParameterJdbcTemplate jdbc;
    private PGVectorSharedMemoryStore store;
    private PolicyAwareMemoryLifecycleService service;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        jdbc = new NamedParameterJdbcTemplate(dataSource);
        store = new PGVectorSharedMemoryStore(jdbc);
        // Default parameters: decay 0.1/day after 7 days, archive below 0.1, retain archive 90 days.
        service = new PolicyAwareMemoryLifecycleService(jdbc, 0.1, 7, 0.1, 90);
    }

    @Test
    void decay_reducesStrengthForIdleMemories() {
        var scope = uniqueScope();
        saveWithLastAccess(scope, "idle", 1.0, Instant.now().minus(17, ChronoUnit.DAYS));

        var result = service.runLifecycle();

        assertThat(result.decayedCount()).isGreaterThanOrEqualTo(1);
        // ~17 days idle * 0.1/day = ~1.7 decay, floored at 0 → archived (strength < 0.1).
        assertThat(result.archivedCount()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void decay_respectsGracePeriod() {
        var scope = uniqueScope();
        saveWithLastAccess(scope, "recent", 0.5, Instant.now().minus(2, ChronoUnit.DAYS));

        service.runLifecycle();

        var strength = currentStrength(scope, "recent");
        assertThat(strength).isCloseTo(0.5, within(0.0001)); // within grace, untouched
    }

    @Test
    void archive_movesFadedMemoriesOutOfActiveTable() {
        var scope = uniqueScope();
        saveWithLastAccess(scope, "faded", 0.05, Instant.now().minus(1, ChronoUnit.DAYS));

        var result = service.runLifecycle();

        assertThat(result.archivedCount()).isGreaterThanOrEqualTo(1);
        assertThat(store.countByTeam(scope)).isZero();
        assertThat(archiveCount(scope)).isGreaterThanOrEqualTo(1);
    }

    @Test
    void perTenantPolicy_overridesDefaultThreshold() {
        var scope = uniqueScope();
        // Tenant policy: aggressive archive threshold 0.6 so a 0.5-strength memory is archived.
        jdbc.update("""
                INSERT INTO memory_policies (tenant_id, decay_rate, decay_after_days, archive_threshold)
                VALUES (:tenantId, 0.0, 0, 0.6)
                """, new MapSqlParameterSource("tenantId", scope.tenantId()));
        saveWithLastAccess(scope, "mid", 0.5, Instant.now().minus(1, ChronoUnit.DAYS));

        var result = service.runLifecycle();

        assertThat(result.archivedCount()).isGreaterThanOrEqualTo(1);
        assertThat(store.countByTeam(scope)).isZero();
    }

    @Test
    void purge_removesArchivedMemoriesPastRetentionWindow() {
        var scope = uniqueScope();
        // A short 1-day retention for this tenant; archive row is 5 days old → purged.
        jdbc.update("""
                INSERT INTO memory_policies (tenant_id, retention_days)
                VALUES (:tenantId, 1)
                """, new MapSqlParameterSource("tenantId", scope.tenantId()));
        insertArchived(scope, "expired", Instant.now().minus(5, ChronoUnit.DAYS));
        insertArchived(scope, "fresh", Instant.now());

        var result = service.runLifecycle();

        assertThat(result.purgedCount()).isGreaterThanOrEqualTo(1);
        assertThat(archiveCount(scope)).isEqualTo(1); // only "fresh" remains
    }

    private void insertArchived(MemoryScope scope, String content, Instant archivedAt) {
        jdbc.update("""
                INSERT INTO shared_memories_archive
                    (id, tenant_id, team_id, memory_type, content, visibility, embedding, strength,
                     access_count, contributor_count, created_at, last_accessed_at, archived_at)
                VALUES
                    (:id, :tenantId, :teamId, 'SEMANTIC', :content, 'PRIVATE', NULL, 0.05,
                     0, 1, :ts, :ts, :archivedAt)
                """, new MapSqlParameterSource()
                .addValue("id", UUID.randomUUID())
                .addValue("tenantId", scope.tenantId())
                .addValue("teamId", scope.teamId())
                .addValue("content", content)
                .addValue("ts", java.sql.Timestamp.from(Instant.now()))
                .addValue("archivedAt", java.sql.Timestamp.from(archivedAt)));
    }

    private void saveWithLastAccess(MemoryScope scope, String content, double strength, Instant lastAccess) {
        var memory = new SharedMemory(UUID.randomUUID(), scope.tenantId(), scope.teamId(),
                MemoryType.SEMANTIC, content, MemoryVisibility.PRIVATE, strength, 0, 1,
                lastAccess, lastAccess);
        store.save(memory, new float[384]);
    }

    private Double currentStrength(MemoryScope scope, String content) {
        return jdbc.queryForObject("""
                SELECT strength FROM shared_memories
                WHERE tenant_id = :tenantId AND team_id = :teamId AND content = :content
                """, new MapSqlParameterSource()
                .addValue("tenantId", scope.tenantId())
                .addValue("teamId", scope.teamId())
                .addValue("content", content), Double.class);
    }

    private long archiveCount(MemoryScope scope) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM shared_memories_archive
                WHERE tenant_id = :tenantId AND team_id = :teamId
                """, new MapSqlParameterSource()
                .addValue("tenantId", scope.tenantId())
                .addValue("teamId", scope.teamId()), Long.class);
        return count != null ? count : 0L;
    }

    private static MemoryScope uniqueScope() {
        return MemoryScope.of("tenant-" + UUID.randomUUID(), "team-" + UUID.randomUUID());
    }
}
