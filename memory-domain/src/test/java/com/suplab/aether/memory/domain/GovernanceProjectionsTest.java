package com.suplab.aether.memory.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GovernanceProjectionsTest {

    @Test
    void erasureResult_isEmptyOnlyWhenBothCountsZero() {
        assertThat(new MemoryErasureResult("t", "team", 0, 0).isEmpty()).isTrue();
        assertThat(new MemoryErasureResult("t", "team", 1, 0).isEmpty()).isFalse();
        assertThat(new MemoryErasureResult("t", "team", 0, 3).isEmpty()).isFalse();
    }

    @Test
    void erasureResult_rejectsNegativeCountsAndBlankScope() {
        assertThatThrownBy(() -> new MemoryErasureResult("t", "team", -1, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MemoryErasureResult("", "team", 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MemoryErasureResult("t", " ", 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void export_defaultsTimestampAndCopiesMemoriesDefensively() {
        var mutable = new ArrayList<ExportedMemory>();
        mutable.add(new ExportedMemory(UUID.randomUUID(), MemoryType.EPISODIC, "c",
                MemoryVisibility.PRIVATE, 0.5, 1, 1, Instant.now(), Instant.now(), true));
        var export = new MemoryExport("t", "team", 0, 1, null, mutable);

        assertThat(export.exportedAt()).isNotNull();
        // defensive copy — mutating the source list does not change the export
        mutable.clear();
        assertThat(export.memories()).hasSize(1);
    }

    @Test
    void export_nullMemoriesBecomesEmptyList() {
        var export = new MemoryExport("t", "team", 0, 0, Instant.now(), null);
        assertThat(export.memories()).isEmpty();
    }
}
