package com.suplab.aether.memory.engine.policy;

import com.suplab.aether.memory.domain.PolicyChangeEvent;
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
class JdbcPolicyChangeAuditStoreIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("aether_memory_test")
            .withUsername("aether")
            .withPassword("aether");

    private JdbcPolicyChangeAuditStore store;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        store = new JdbcPolicyChangeAuditStore(new NamedParameterJdbcTemplate(dataSource));
    }

    @Test
    void record_thenRecentForTenant_returnsNewestFirstAndIsTenantScoped() {
        var tenant = "tenant-" + UUID.randomUUID();
        var other = "tenant-" + UUID.randomUUID();
        store.record(PolicyChangeEvent.of(tenant, "alice", "retentionDays: 90 → 30"));
        store.record(PolicyChangeEvent.of(tenant, null, "federationEnabled: false → true"));
        store.record(PolicyChangeEvent.of(other, "carol", "decayRate: 0.01 → 0.02"));

        var recent = store.recentForTenant(tenant, 10);

        assertThat(recent).hasSize(2); // the other tenant's event is excluded
        assertThat(recent.getFirst().summary()).isEqualTo("federationEnabled: false → true"); // newest first
        assertThat(recent.getFirst().actor()).isNull();
        assertThat(recent.get(1).actor()).isEqualTo("alice");
    }

    @Test
    void recentForTenant_isBoundedByLimit() {
        var tenant = "tenant-" + UUID.randomUUID();
        for (int i = 0; i < 5; i++) {
            store.record(PolicyChangeEvent.of(tenant, "actor", "change " + i));
        }
        assertThat(store.recentForTenant(tenant, 2)).hasSize(2);
    }
}
