package com.suplab.aether.memory.engine.federation;

import com.suplab.aether.memory.domain.FederationAuditEvent;
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
    void record_thenRecent_returnsNewestFirst() {
        var origin = "tenant-" + UUID.randomUUID();
        store.record(FederationAuditEvent.of(origin, MemoryType.SEMANTIC, "first query", 3));
        store.record(FederationAuditEvent.of(origin, null, "second query", 0));

        var recent = store.recent(10).stream()
                .filter(e -> e.originTenantId().equals(origin)).toList();

        assertThat(recent).hasSize(2);
        assertThat(recent.getFirst().queryLabel()).isEqualTo("second query"); // newest first
        assertThat(recent.getFirst().type()).isNull();
        assertThat(recent.get(1).type()).isEqualTo(MemoryType.SEMANTIC);
        assertThat(recent.get(1).resultCount()).isEqualTo(3);
    }

    @Test
    void recent_isBoundedByLimit() {
        var origin = "tenant-" + UUID.randomUUID();
        for (int i = 0; i < 5; i++) {
            store.record(FederationAuditEvent.of(origin, null, "q" + i, i));
        }
        assertThat(store.recent(2)).hasSize(2);
    }
}
