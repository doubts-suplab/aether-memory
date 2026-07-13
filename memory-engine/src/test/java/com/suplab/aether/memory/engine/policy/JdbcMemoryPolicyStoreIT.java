package com.suplab.aether.memory.engine.policy;

import com.suplab.aether.memory.domain.MemoryPolicy;
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

@Testcontainers
class JdbcMemoryPolicyStoreIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("aether_memory_test")
            .withUsername("aether")
            .withPassword("aether");

    private JdbcMemoryPolicyStore store;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        store = new JdbcMemoryPolicyStore(new NamedParameterJdbcTemplate(dataSource));
    }

    @Test
    void resolve_returnsDefaultsWhenNoPolicyConfigured() {
        var tenantId = "tenant-" + UUID.randomUUID();

        var policy = store.resolve(tenantId);

        assertThat(policy).isEqualTo(MemoryPolicy.defaults(tenantId));
        assertThat(policy.federationEnabled()).isFalse();
    }

    @Test
    void save_thenResolve_returnsStoredPolicy() {
        var tenantId = "tenant-" + UUID.randomUUID();
        var custom = new MemoryPolicy(tenantId, 0.05, 3, 0.2, 0.15, 30, true, 120);

        store.save(custom);

        assertThat(store.resolve(tenantId)).isEqualTo(custom);
    }

    @Test
    void save_isUpsert() {
        var tenantId = "tenant-" + UUID.randomUUID();
        store.save(new MemoryPolicy(tenantId, 0.05, 3, 0.2, 0.15, 30, true, 120));
        store.save(new MemoryPolicy(tenantId, 0.02, 10, 0.1, 0.05, 60, false, 200));

        var resolved = store.resolve(tenantId);
        assertThat(resolved.decayRate()).isEqualTo(0.02);
        assertThat(resolved.federationEnabled()).isFalse();
        assertThat(resolved.federationMaxSummaryLength()).isEqualTo(200);
    }

    @Test
    void findAll_returnsOnlyConfiguredPolicies() {
        var tenantId = "tenant-" + UUID.randomUUID();
        store.save(new MemoryPolicy(tenantId, 0.05, 3, 0.2, 0.15, 30, true, 120));

        assertThat(store.findAll()).extracting(MemoryPolicy::tenantId).contains(tenantId);
    }
}
