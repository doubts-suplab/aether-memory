package com.suplab.aether.memory.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class SharedMemoryTest {

    private static final MemoryScope SCOPE = MemoryScope.of("tenant-1", "team-alpha");

    @Test
    void create_setsInitialStrengthAndFoundingContributor() {
        var memory = SharedMemory.create(SCOPE, MemoryType.SEMANTIC, "Our SLA is 99.95%", MemoryVisibility.TENANT);

        assertThat(memory.strength()).isEqualTo(1.0);
        assertThat(memory.accessCount()).isZero();
        assertThat(memory.contributorCount()).isEqualTo(1);
        assertThat(memory.tenantId()).isEqualTo("tenant-1");
        assertThat(memory.teamId()).isEqualTo("team-alpha");
        assertThat(memory.type()).isEqualTo(MemoryType.SEMANTIC);
        assertThat(memory.visibility()).isEqualTo(MemoryVisibility.TENANT);
        assertThat(memory.id()).isNotNull();
    }

    @Test
    void create_assignsDistinctIdsForEachCall() {
        var a = SharedMemory.create(SCOPE, MemoryType.SEMANTIC, "same", MemoryVisibility.PRIVATE);
        var b = SharedMemory.create(SCOPE, MemoryType.SEMANTIC, "same", MemoryVisibility.PRIVATE);

        assertThat(a.id()).isNotEqualTo(b.id());
    }

    @Test
    void scope_returnsOwningTenantAndTeam() {
        var memory = SharedMemory.create(SCOPE, MemoryType.EPISODIC, "the incident", MemoryVisibility.PRIVATE);

        assertThat(memory.scope()).isEqualTo(SCOPE);
    }

    @Test
    void reinforce_increasesStrengthByIncrementAndCountsAccess() {
        var memory = new SharedMemory(UUID.randomUUID(), "tenant-1", "team-alpha", MemoryType.PROCEDURAL,
                "deploy runbook", MemoryVisibility.PRIVATE, 0.5, 3, 2, Instant.now(), Instant.now());

        var reinforced = memory.reinforce(0.1);

        assertThat(reinforced.strength()).isCloseTo(0.6, within(0.001));
        assertThat(reinforced.accessCount()).isEqualTo(4);
        assertThat(reinforced.contributorCount()).isEqualTo(2);
    }

    @Test
    void reinforce_capsStrengthAtOne() {
        var memory = new SharedMemory(UUID.randomUUID(), "tenant-1", "team-alpha", MemoryType.SEMANTIC,
                "fact", MemoryVisibility.PRIVATE, 0.95, 10, 1, Instant.now(), Instant.now());

        assertThat(memory.reinforce(0.1).strength()).isEqualTo(1.0);
    }

    @Test
    void reinforce_rejectsNegativeIncrement() {
        var memory = SharedMemory.create(SCOPE, MemoryType.SEMANTIC, "fact", MemoryVisibility.PRIVATE);

        assertThatThrownBy(() -> memory.reinforce(-0.1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("increment must be >= 0");
    }

    @Test
    void contribute_incrementsContributorCountAndStrength() {
        var memory = new SharedMemory(UUID.randomUUID(), "tenant-1", "team-alpha", MemoryType.EMOTIONAL,
                "team morale high", MemoryVisibility.TENANT, 0.5, 0, 1, Instant.now(), Instant.now());

        var contributed = memory.contribute(0.1);

        assertThat(contributed.contributorCount()).isEqualTo(2);
        assertThat(contributed.strength()).isCloseTo(0.6, within(0.001));
        assertThat(contributed.accessCount()).isZero();
    }

    @Test
    void withVisibility_promotesToFederated() {
        var memory = SharedMemory.create(SCOPE, MemoryType.SEMANTIC, "shareable fact", MemoryVisibility.TENANT);

        var promoted = memory.withVisibility(MemoryVisibility.FEDERATED);

        assertThat(promoted.visibility()).isEqualTo(MemoryVisibility.FEDERATED);
        assertThat(promoted.id()).isEqualTo(memory.id());
        assertThat(promoted.content()).isEqualTo(memory.content());
    }

    @Test
    void withVisibility_rejectsNull() {
        var memory = SharedMemory.create(SCOPE, MemoryType.SEMANTIC, "fact", MemoryVisibility.TENANT);

        assertThatThrownBy(() -> memory.withVisibility(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("visibility required");
    }

    @Test
    void constructor_defaultsNullVisibilityToPrivate() {
        var memory = new SharedMemory(UUID.randomUUID(), "tenant-1", "team-alpha", MemoryType.SEMANTIC,
                "fact", null, 1.0, 0, 1, Instant.now(), Instant.now());

        assertThat(memory.visibility()).isEqualTo(MemoryVisibility.PRIVATE);
    }

    @Test
    void constructor_rejectsBlankTenantId() {
        assertThatThrownBy(() -> new SharedMemory(UUID.randomUUID(), "", "team-alpha", MemoryType.SEMANTIC,
                "content", MemoryVisibility.PRIVATE, 0.5, 0, 1, Instant.now(), Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId required");
    }

    @Test
    void constructor_rejectsBlankTeamId() {
        assertThatThrownBy(() -> new SharedMemory(UUID.randomUUID(), "tenant-1", "  ", MemoryType.SEMANTIC,
                "content", MemoryVisibility.PRIVATE, 0.5, 0, 1, Instant.now(), Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("teamId required");
    }

    @Test
    void constructor_rejectsNullType() {
        assertThatThrownBy(() -> new SharedMemory(UUID.randomUUID(), "tenant-1", "team-alpha", null,
                "content", MemoryVisibility.PRIVATE, 0.5, 0, 1, Instant.now(), Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("type required");
    }

    @Test
    void constructor_rejectsBlankContent() {
        assertThatThrownBy(() -> new SharedMemory(UUID.randomUUID(), "tenant-1", "team-alpha", MemoryType.SEMANTIC,
                "  ", MemoryVisibility.PRIVATE, 0.5, 0, 1, Instant.now(), Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("content required");
    }

    @Test
    void constructor_rejectsStrengthOutOfRange() {
        assertThatThrownBy(() -> new SharedMemory(UUID.randomUUID(), "tenant-1", "team-alpha", MemoryType.SEMANTIC,
                "content", MemoryVisibility.PRIVATE, 1.5, 0, 1, Instant.now(), Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("strength must be 0-1");
    }

    @Test
    void constructor_rejectsContributorCountBelowOne() {
        assertThatThrownBy(() -> new SharedMemory(UUID.randomUUID(), "tenant-1", "team-alpha", MemoryType.SEMANTIC,
                "content", MemoryVisibility.PRIVATE, 0.5, 0, 0, Instant.now(), Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("contributorCount must be >= 1");
    }

    @Test
    void allMemoryTypes_areValid() {
        for (var type : MemoryType.values()) {
            var memory = SharedMemory.create(SCOPE, type, "content", MemoryVisibility.PRIVATE);
            assertThat(memory.type()).isEqualTo(type);
        }
    }
}
