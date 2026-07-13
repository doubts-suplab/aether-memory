package com.suplab.aether.memory.engine.federation;

import com.suplab.aether.memory.domain.FederationAuditEntry;
import com.suplab.aether.memory.domain.MemoryType;
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
class JdbcFederationAuditStoreIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("aether_memory_test")
            .withUsername("aether")
            .withPassword("aether");

    private JdbcFederationAuditStore store;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        store = new JdbcFederationAuditStore(new NamedParameterJdbcTemplate(dataSource));
    }

    @Test
    void record_thenRecentFor_roundTrip() {
        var tenantId = "tenant-" + UUID.randomUUID();
        store.record(FederationAuditEntry.record(tenantId, "how do we deploy", MemoryType.PROCEDURAL, 3, false));

        var recent = store.recentFor(tenantId, 10);

        assertThat(recent).hasSize(1);
        assertThat(recent.getFirst().queryText()).isEqualTo("how do we deploy");
        assertThat(recent.getFirst().type()).isEqualTo(MemoryType.PROCEDURAL);
        assertThat(recent.getFirst().resultCount()).isEqualTo(3);
        assertThat(recent.getFirst().localOnly()).isFalse();
    }

    @Test
    void record_persistsNullTypeForAllTypeQuery() {
        var tenantId = "tenant-" + UUID.randomUUID();
        store.record(FederationAuditEntry.record(tenantId, "anything", null, 0, true));

        var recent = store.recentFor(tenantId, 10);
        assertThat(recent).hasSize(1);
        assertThat(recent.getFirst().type()).isNull();
        assertThat(recent.getFirst().localOnly()).isTrue();
    }

    @Test
    void recentFor_isScopedToOriginTenant() {
        var tenantA = "tenant-" + UUID.randomUUID();
        var tenantB = "tenant-" + UUID.randomUUID();
        store.record(FederationAuditEntry.record(tenantA, "q1", MemoryType.SEMANTIC, 1, false));
        store.record(FederationAuditEntry.record(tenantB, "q2", MemoryType.SEMANTIC, 1, false));

        assertThat(store.recentFor(tenantA, 10)).hasSize(1);
        assertThat(store.recentFor(tenantA, 10).getFirst().queryText()).isEqualTo("q1");
    }

    @Test
    void recentFor_returnsNewestFirst() {
        var tenantId = "tenant-" + UUID.randomUUID();
        var older = java.time.Instant.now().minusSeconds(60);
        var newer = java.time.Instant.now();
        store.record(new FederationAuditEntry(UUID.randomUUID(), tenantId, "older",
                MemoryType.SEMANTIC, 1, false, older));
        store.record(new FederationAuditEntry(UUID.randomUUID(), tenantId, "newer",
                MemoryType.SEMANTIC, 2, false, newer));

        var recent = store.recentFor(tenantId, 10);
        assertThat(recent).hasSize(2);
        assertThat(recent.getFirst().queryText()).isEqualTo("newer");
    }
}
