package com.suplab.aether.memory.engine.governance;

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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class JdbcMemoryGovernanceIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("aether_memory_test")
            .withUsername("aether")
            .withPassword("aether");

    private NamedParameterJdbcTemplate jdbc;
    private PGVectorSharedMemoryStore store;
    private JdbcMemoryErasureService erasure;
    private JdbcMemoryExportService export;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        jdbc = new NamedParameterJdbcTemplate(dataSource);
        store = new PGVectorSharedMemoryStore(jdbc);
        erasure = new JdbcMemoryErasureService(jdbc);
        export = new JdbcMemoryExportService(jdbc);
    }

    private static MemoryScope uniqueScope() {
        return MemoryScope.of("tenant-" + UUID.randomUUID(), "team-" + UUID.randomUUID());
    }

    private void saveActive(MemoryScope scope, String content) {
        var now = Instant.now();
        var memory = new SharedMemory(UUID.randomUUID(), scope.tenantId(), scope.teamId(),
                MemoryType.SEMANTIC, content, MemoryVisibility.TENANT, 0.8, 2, 1, now, now);
        store.save(memory, new float[384]);
    }

    private void insertArchived(MemoryScope scope, String content) {
        jdbc.update("""
                INSERT INTO shared_memories_archive
                    (id, tenant_id, team_id, memory_type, content, visibility, embedding, strength,
                     access_count, contributor_count, created_at, last_accessed_at, archived_at)
                VALUES
                    (:id, :tenantId, :teamId, 'SEMANTIC', :content, 'TENANT', NULL, 0.05,
                     1, 1, NOW(), NOW(), NOW())
                """, new MapSqlParameterSource()
                .addValue("id", UUID.randomUUID())
                .addValue("tenantId", scope.tenantId())
                .addValue("teamId", scope.teamId())
                .addValue("content", content));
    }

    @Test
    void export_spansActiveAndArchive_nonReinforcing() {
        var scope = uniqueScope();
        saveActive(scope, "active-1");
        saveActive(scope, "active-2");
        insertArchived(scope, "archived-1");

        var result = export.exportTeam(scope);

        assertThat(result.activeCount()).isEqualTo(2);
        assertThat(result.archivedCount()).isEqualTo(1);
        assertThat(result.memories()).hasSize(3);
        assertThat(result.memories()).anyMatch(m -> m.archived() && m.content().equals("archived-1"));
        // export is non-reinforcing: the active memory's strength is unchanged (still 0.8)
        assertThat(store.countByTeam(scope)).isEqualTo(2);
        assertThat(result.memories()).filteredOn(m -> !m.archived())
                .allMatch(m -> m.strength() == 0.8);
    }

    @Test
    void erase_removesActiveAndArchive_scopedToTeam() {
        var scope = uniqueScope();
        var other = MemoryScope.of(scope.tenantId(), scope.teamId() + "-b");
        saveActive(scope, "a1");
        insertArchived(scope, "arch-1");
        insertArchived(scope, "arch-2");
        saveActive(other, "other-active");

        var result = erasure.eraseTeam(scope);

        assertThat(result.activeErased()).isEqualTo(1);
        assertThat(result.archivedErased()).isEqualTo(2);
        assertThat(store.countByTeam(scope)).isZero();
        // a different team is untouched
        assertThat(store.countByTeam(other)).isEqualTo(1);
    }

    @Test
    void erase_isIdempotentForAnEmptyTeam() {
        var result = erasure.eraseTeam(uniqueScope());
        assertThat(result.isEmpty()).isTrue();
    }
}
