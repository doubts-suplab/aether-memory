package com.suplab.aether.memory.engine.store;

import com.suplab.aether.memory.domain.MemoryScope;
import com.suplab.aether.memory.domain.MemoryType;
import com.suplab.aether.memory.domain.MemoryVisibility;
import com.suplab.aether.memory.domain.SharedMemory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@Testcontainers
class PGVectorSharedMemoryStoreIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("aether_memory_test")
            .withUsername("aether")
            .withPassword("aether");

    private static final MemoryScope SCOPE = MemoryScope.of("tenant-1", "team-alpha");
    private static final double INCREMENT = 0.1;

    private PGVectorSharedMemoryStore store;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        var jdbc = new NamedParameterJdbcTemplate(dataSource);
        store = new PGVectorSharedMemoryStore(jdbc);
    }

    @Test
    void save_andFindByType_roundTrip() {
        var scope = uniqueScope();
        var memory = SharedMemory.create(scope, MemoryType.EPISODIC, "Q3 incident retro", MemoryVisibility.PRIVATE);

        store.save(memory, new float[384]);

        var found = store.findByType(scope, MemoryType.EPISODIC, 10, INCREMENT);
        assertThat(found).hasSize(1);
        assertThat(found.getFirst().content()).isEqualTo("Q3 incident retro");
        assertThat(found.getFirst().type()).isEqualTo(MemoryType.EPISODIC);
    }

    @Test
    void findByType_reinforcesOnRead() {
        var scope = uniqueScope();
        var memory = new SharedMemory(UUID.randomUUID(), scope.tenantId(), scope.teamId(),
                MemoryType.SEMANTIC, "SLA is 99.95%", MemoryVisibility.TENANT, 0.5, 0, 1,
                java.time.Instant.now(), java.time.Instant.now());
        store.save(memory, new float[384]);

        var first = store.findByType(scope, MemoryType.SEMANTIC, 10, INCREMENT);
        assertThat(first.getFirst().strength()).isCloseTo(0.6, within(0.001));
        assertThat(first.getFirst().accessCount()).isEqualTo(1);

        var second = store.findByType(scope, MemoryType.SEMANTIC, 10, INCREMENT);
        assertThat(second.getFirst().strength()).isCloseTo(0.7, within(0.001));
        assertThat(second.getFirst().accessCount()).isEqualTo(2);
    }

    @Test
    void findSimilar_returnsReinforcedMemories() {
        var scope = uniqueScope();
        store.save(SharedMemory.create(scope, MemoryType.EMOTIONAL, "team morale high", MemoryVisibility.PRIVATE),
                new float[384]);

        var found = store.findSimilar(scope, new float[384], 5, INCREMENT);
        assertThat(found).hasSize(1);
        assertThat(found.getFirst().accessCount()).isEqualTo(1);
        assertThat(found.getFirst().strength()).isEqualTo(1.0); // already capped
    }

    @Test
    void countByTeam_countsOnlyThatTeam() {
        var scope = uniqueScope();
        assertThat(store.countByTeam(scope)).isZero();

        store.save(SharedMemory.create(scope, MemoryType.EPISODIC, "m1", MemoryVisibility.PRIVATE), new float[384]);
        store.save(SharedMemory.create(scope, MemoryType.SEMANTIC, "m2", MemoryVisibility.PRIVATE), new float[384]);
        assertThat(store.countByTeam(scope)).isEqualTo(2);
    }

    @Test
    void findByType_isolatesPerTeam() {
        var teamA = uniqueScope();
        var teamB = new MemoryScope(teamA.tenantId(), teamA.teamId() + "-b");

        store.save(SharedMemory.create(teamA, MemoryType.EPISODIC, "team A memory", MemoryVisibility.PRIVATE),
                new float[384]);
        store.save(SharedMemory.create(teamB, MemoryType.EPISODIC, "team B memory", MemoryVisibility.PRIVATE),
                new float[384]);

        var resultA = store.findByType(teamA, MemoryType.EPISODIC, 10, INCREMENT);
        assertThat(resultA).hasSize(1);
        assertThat(resultA.getFirst().content()).isEqualTo("team A memory");
    }

    @Test
    void delete_doesNotDeleteOtherTeamsMemory() {
        var teamA = uniqueScope();
        var teamB = new MemoryScope(teamA.tenantId(), teamA.teamId() + "-b");
        var memoryA = SharedMemory.create(teamA, MemoryType.EPISODIC, "team A memory", MemoryVisibility.PRIVATE);
        store.save(memoryA, new float[384]);

        store.delete(memoryA.id(), teamB);

        assertThat(store.countByTeam(teamA)).isEqualTo(1);
    }

    @Test
    void findFederatable_returnsOnlyFederatedInEnabledTenants() {
        var scope = uniqueScope();
        enableFederation(scope.tenantId());

        store.save(SharedMemory.create(scope, MemoryType.SEMANTIC, "private fact", MemoryVisibility.PRIVATE),
                new float[384]);
        store.save(SharedMemory.create(scope, MemoryType.SEMANTIC, "federated fact", MemoryVisibility.FEDERATED),
                new float[384]);

        var federatable = store.findFederatable(new float[384], MemoryType.SEMANTIC, 10);
        assertThat(federatable).extracting(SharedMemory::content).contains("federated fact");
        assertThat(federatable).extracting(SharedMemory::content).doesNotContain("private fact");
    }

    @Test
    void findFederatable_excludesTenantsWithoutFederationEnabled() {
        var scope = uniqueScope();
        // No policy row → federation disabled by default.
        store.save(SharedMemory.create(scope, MemoryType.SEMANTIC, "federated but blocked", MemoryVisibility.FEDERATED),
                new float[384]);

        var federatable = store.findFederatable(new float[384], MemoryType.SEMANTIC, 10);
        assertThat(federatable).extracting(SharedMemory::content).doesNotContain("federated but blocked");
    }

    @Test
    void contribute_incrementsContributorCountAndReinforces() {
        var scope = uniqueScope();
        var memory = new SharedMemory(UUID.randomUUID(), scope.tenantId(), scope.teamId(),
                MemoryType.SEMANTIC, "shared decision", MemoryVisibility.TENANT, 0.5, 2, 1,
                java.time.Instant.now(), java.time.Instant.now());
        store.save(memory, new float[384]);

        var updated = store.contribute(memory.id(), scope, INCREMENT);

        assertThat(updated).isPresent();
        assertThat(updated.get().contributorCount()).isEqualTo(2);
        assertThat(updated.get().strength()).isCloseTo(0.6, within(0.001));
        assertThat(updated.get().accessCount()).isEqualTo(2); // contribute does not bump accessCount
    }

    @Test
    void contribute_returnsEmptyForOtherTeam() {
        var teamA = uniqueScope();
        var teamB = new MemoryScope(teamA.tenantId(), teamA.teamId() + "-b");
        var memoryA = SharedMemory.create(teamA, MemoryType.SEMANTIC, "team A memory", MemoryVisibility.PRIVATE);
        store.save(memoryA, new float[384]);

        assertThat(store.contribute(memoryA.id(), teamB, INCREMENT)).isEmpty();
        // untouched under its real scope
        assertThat(store.findByType(teamA, MemoryType.SEMANTIC, 10, 0.0).getFirst().contributorCount())
                .isEqualTo(1);
    }

    @Test
    void save_upsertUpdatesExistingRecord() {
        var scope = uniqueScope();
        var memory = SharedMemory.create(scope, MemoryType.SEMANTIC, "original", MemoryVisibility.PRIVATE);
        store.save(memory, new float[384]);

        var updated = new SharedMemory(memory.id(), scope.tenantId(), scope.teamId(), MemoryType.SEMANTIC,
                "updated", MemoryVisibility.TENANT, 0.9, 5, 3, memory.createdAt(), memory.lastAccessedAt());
        store.save(updated, new float[384]);

        var found = store.findByType(scope, MemoryType.SEMANTIC, 10, 0.0);
        assertThat(found).hasSize(1);
        assertThat(found.getFirst().content()).isEqualTo("updated");
        assertThat(found.getFirst().visibility()).isEqualTo(MemoryVisibility.TENANT);
    }

    private void enableFederation(String tenantId) {
        var jdbc = new NamedParameterJdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        jdbc.update("""
                INSERT INTO memory_policies (tenant_id, federation_enabled)
                VALUES (:tenantId, TRUE)
                ON CONFLICT (tenant_id) DO UPDATE SET federation_enabled = TRUE
                """, new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("tenantId", tenantId));
    }

    private static MemoryScope uniqueScope() {
        return MemoryScope.of("tenant-" + UUID.randomUUID(), "team-" + UUID.randomUUID());
    }
}
