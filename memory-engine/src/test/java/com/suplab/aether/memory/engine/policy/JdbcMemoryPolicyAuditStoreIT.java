package com.suplab.aether.memory.engine.policy;

import com.suplab.aether.memory.domain.MemoryPolicy;
import com.suplab.aether.memory.domain.PolicyChangeEntry;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
class JdbcMemoryPolicyAuditStoreIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("aether_memory_test")
            .withUsername("aether")
            .withPassword("aether");

    private JdbcMemoryPolicyAuditStore store;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        store = new JdbcMemoryPolicyAuditStore(new NamedParameterJdbcTemplate(dataSource));
    }

    @Test
    void record_thenRecentFor_returnsSnapshot() {
        var tenantId = "tenant-" + UUID.randomUUID();
        var policy = new MemoryPolicy(tenantId, 0.05, 3, 0.2, 0.15, 30, true, 120);
        store.record(PolicyChangeEntry.of(policy));

        var recent = store.recentFor(tenantId, 10);

        assertThat(recent).hasSize(1);
        assertThat(recent.getFirst().policy()).isEqualTo(policy);
    }

    @Test
    void recentFor_returnsNewestFirst() {
        var tenantId = "tenant-" + UUID.randomUUID();
        var older = new MemoryPolicy(tenantId, 0.01, 7, 0.1, 0.1, 90, false, 280);
        var newer = new MemoryPolicy(tenantId, 0.02, 5, 0.2, 0.2, 60, true, 100);
        store.record(new PolicyChangeEntry(UUID.randomUUID(), older, Instant.now().minusSeconds(60)));
        store.record(new PolicyChangeEntry(UUID.randomUUID(), newer, Instant.now()));

        var recent = store.recentFor(tenantId, 10);

        assertThat(recent).hasSize(2);
        assertThat(recent.getFirst().policy().federationEnabled()).isTrue(); // the newer one
    }

    @Test
    void recentFor_isScopedToTenant() {
        var tenantA = "tenant-" + UUID.randomUUID();
        var tenantB = "tenant-" + UUID.randomUUID();
        store.record(PolicyChangeEntry.of(MemoryPolicy.defaults(tenantA)));
        store.record(PolicyChangeEntry.of(MemoryPolicy.defaults(tenantB)));

        assertThat(store.recentFor(tenantA, 10)).hasSize(1);
        assertThat(store.recentFor(tenantA, 10).getFirst().policy().tenantId()).isEqualTo(tenantA);
    }
}
