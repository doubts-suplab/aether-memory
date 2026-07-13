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
class JdbcMemoryGovernanceServiceIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("aether_memory_test")
            .withUsername("aether")
            .withPassword("aether");

    private NamedParameterJdbcTemplate jdbc;
    private PGVectorSharedMemoryStore store;
    private JdbcMemoryGovernanceService governance;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        jdbc = new NamedParameterJdbcTemplate(dataSource);
        store = new PGVectorSharedMemoryStore(jdbc);
        governance = new JdbcMemoryGovernanceService(jdbc);
    }

    @Test
    void eraseTeam_removesActiveAndArchive_andAudits() {
        var scope = uniqueScope();
        store.save(SharedMemory.create(scope, MemoryType.SEMANTIC, "active", MemoryVisibility.PRIVATE),
                new float[384]);
        insertArchived(scope);

        var result = governance.eraseTeam(scope);

        assertThat(result.activeDeleted()).isEqualTo(1);
        assertThat(result.archivedDeleted()).isEqualTo(1);
        assertThat(store.countByTeam(scope)).isZero();
        assertThat(governance.recentErasures(scope.tenantId(), 10)).hasSize(1);
        assertThat(governance.recentErasures(scope.tenantId(), 10).getFirst().scope().name()).isEqualTo("TEAM");
    }

    @Test
    void eraseTeam_doesNotTouchOtherTeams() {
        var teamA = uniqueScope();
        var teamB = new MemoryScope(teamA.tenantId(), teamA.teamId() + "-b");
        store.save(SharedMemory.create(teamA, MemoryType.SEMANTIC, "a", MemoryVisibility.PRIVATE), new float[384]);
        store.save(SharedMemory.create(teamB, MemoryType.SEMANTIC, "b", MemoryVisibility.PRIVATE), new float[384]);

        governance.eraseTeam(teamA);

        assertThat(store.countByTeam(teamA)).isZero();
        assertThat(store.countByTeam(teamB)).isEqualTo(1);
    }

    @Test
    void eraseTenant_removesEveryTeamAndFederationAudit() {
        var tenantId = "tenant-" + UUID.randomUUID();
        var teamA = new MemoryScope(tenantId, "team-a");
        var teamB = new MemoryScope(tenantId, "team-b");
        store.save(SharedMemory.create(teamA, MemoryType.SEMANTIC, "a", MemoryVisibility.PRIVATE), new float[384]);
        store.save(SharedMemory.create(teamB, MemoryType.SEMANTIC, "b", MemoryVisibility.PRIVATE), new float[384]);
        jdbc.update("""
                INSERT INTO federation_audit (origin_tenant_id, query_text, result_count, local_only)
                VALUES (:tenantId, 'q', 0, false)
                """, new MapSqlParameterSource("tenantId", tenantId));

        var result = governance.eraseTenant(tenantId);

        assertThat(result.activeDeleted()).isEqualTo(2);
        assertThat(store.countByTeam(teamA)).isZero();
        assertThat(store.countByTeam(teamB)).isZero();
        Long auditRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM federation_audit WHERE origin_tenant_id = :t",
                new MapSqlParameterSource("t", tenantId), Long.class);
        assertThat(auditRows).isZero();
    }

    @Test
    void exportTeam_returnsAllActiveMemories() {
        var scope = uniqueScope();
        store.save(SharedMemory.create(scope, MemoryType.SEMANTIC, "m1", MemoryVisibility.PRIVATE), new float[384]);
        store.save(SharedMemory.create(scope, MemoryType.EPISODIC, "m2", MemoryVisibility.TENANT), new float[384]);

        var exported = governance.exportTeam(scope);

        assertThat(exported).hasSize(2);
        assertThat(exported).extracting(SharedMemory::content).containsExactlyInAnyOrder("m1", "m2");
    }

    private void insertArchived(MemoryScope scope) {
        jdbc.update("""
                INSERT INTO shared_memories_archive
                    (id, tenant_id, team_id, memory_type, content, visibility, embedding, strength,
                     access_count, contributor_count, created_at, last_accessed_at, archived_at)
                VALUES
                    (:id, :tenantId, :teamId, 'SEMANTIC', 'archived', 'PRIVATE', NULL, 0.05,
                     0, 1, :ts, :ts, :ts)
                """, new MapSqlParameterSource()
                .addValue("id", UUID.randomUUID())
                .addValue("tenantId", scope.tenantId())
                .addValue("teamId", scope.teamId())
                .addValue("ts", java.sql.Timestamp.from(Instant.now())));
    }

    private static MemoryScope uniqueScope() {
        return MemoryScope.of("tenant-" + UUID.randomUUID(), "team-" + UUID.randomUUID());
    }
}
