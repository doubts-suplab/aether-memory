package com.suplab.aether.memory.api.controller;

import com.suplab.aether.memory.domain.ExportedMemory;
import com.suplab.aether.memory.domain.MemoryErasureResult;
import com.suplab.aether.memory.domain.MemoryExport;
import com.suplab.aether.memory.domain.MemoryScope;
import com.suplab.aether.memory.domain.MemoryType;
import com.suplab.aether.memory.domain.MemoryVisibility;
import com.suplab.aether.memory.ports.MemoryErasurePort;
import com.suplab.aether.memory.ports.MemoryExportPort;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryGovernanceControllerTest {

    private static final class FakeErasure implements MemoryErasurePort {
        MemoryScope lastScope;
        @Override public MemoryErasureResult eraseTeam(MemoryScope scope) {
            lastScope = scope;
            return new MemoryErasureResult(scope.tenantId(), scope.teamId(), 4, 2);
        }
    }

    private static final class FakeExport implements MemoryExportPort {
        MemoryScope lastScope;
        @Override public MemoryExport exportTeam(MemoryScope scope) {
            lastScope = scope;
            var m = new ExportedMemory(UUID.randomUUID(), MemoryType.SEMANTIC, "content",
                    MemoryVisibility.TENANT, 0.9, 3, 2, Instant.now(), Instant.now(), false);
            return new MemoryExport(scope.tenantId(), scope.teamId(), 1, 0, Instant.now(), List.of(m));
        }
    }

    @Test
    void erase_returns200WithCounts() {
        var erasure = new FakeErasure();
        var controller = new MemoryGovernanceController(erasure, new FakeExport());

        var res = controller.erase("acme", "team-a");

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).containsEntry("activeErased", 4).containsEntry("archivedErased", 2);
        assertThat(erasure.lastScope).isEqualTo(new MemoryScope("acme", "team-a"));
    }

    @Test
    void export_returns200WithSnapshot() {
        var export = new FakeExport();
        var controller = new MemoryGovernanceController(new FakeErasure(), export);

        var res = controller.export("acme", "team-a");

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).containsEntry("activeCount", 1).containsEntry("archivedCount", 0);
        assertThat((List<?>) res.getBody().get("memories")).hasSize(1);
        assertThat(export.lastScope).isEqualTo(new MemoryScope("acme", "team-a"));
    }
}
