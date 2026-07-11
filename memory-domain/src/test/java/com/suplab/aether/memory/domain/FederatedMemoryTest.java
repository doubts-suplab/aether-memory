package com.suplab.aether.memory.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FederatedMemoryTest {

    private static final MemoryScope SCOPE = MemoryScope.of("tenant-9", "team-secret");

    @Test
    void from_projectsShortContentUnchanged() {
        var memory = SharedMemory.create(SCOPE, MemoryType.SEMANTIC, "concise fact", MemoryVisibility.FEDERATED);

        var federated = FederatedMemory.from(memory, "tenant-9");

        assertThat(federated.type()).isEqualTo(MemoryType.SEMANTIC);
        assertThat(federated.summary()).isEqualTo("concise fact");
        assertThat(federated.strength()).isEqualTo(memory.strength());
        assertThat(federated.provenance()).isEqualTo("tenant-9");
    }

    @Test
    void from_truncatesLongContentToMaxSummaryLength() {
        var longContent = "x".repeat(500);
        var memory = SharedMemory.create(SCOPE, MemoryType.EPISODIC, longContent, MemoryVisibility.FEDERATED);

        var federated = FederatedMemory.from(memory, "tenant-9");

        assertThat(federated.summary()).hasSize(FederatedMemory.MAX_SUMMARY_LENGTH);
        assertThat(federated.summary()).endsWith("…");
    }

    @Test
    void from_neverLeaksTeamIdentity() {
        var memory = SharedMemory.create(SCOPE, MemoryType.SEMANTIC, "content", MemoryVisibility.FEDERATED);

        var federated = FederatedMemory.from(memory, "tenant-9");

        // Provenance is coarse (tenant), never the owning team.
        assertThat(federated.provenance()).doesNotContain("team-secret");
    }

    @Test
    void constructor_defaultsBlankProvenanceToFederated() {
        var federated = new FederatedMemory(MemoryType.SEMANTIC, "summary", 0.5, "  ");

        assertThat(federated.provenance()).isEqualTo("FEDERATED");
    }

    @Test
    void constructor_rejectsNullType() {
        assertThatThrownBy(() -> new FederatedMemory(null, "summary", 0.5, "tenant-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("type required");
    }

    @Test
    void constructor_rejectsStrengthOutOfRange() {
        assertThatThrownBy(() -> new FederatedMemory(MemoryType.SEMANTIC, "summary", 2.0, "tenant-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("strength must be 0-1");
    }
}
