package com.suplab.aether.memory.api.controller;

import com.suplab.aether.memory.domain.ExportedMemory;
import com.suplab.aether.memory.domain.MemoryScope;
import com.suplab.aether.memory.ports.MemoryErasurePort;
import com.suplab.aether.memory.ports.MemoryExportPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Data-subject governance for a team's shared memories — right-to-erasure (GDPR Art. 17) and
 * data-portability export (Art. 20).
 *
 * <p>Scoped by {@code tenantId} + {@code teamId}. Both operations span the active store and the
 * archive so faded memories are not overlooked. Erasure is idempotent; export is read-only and
 * non-reinforcing.</p>
 */
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/teams/{teamId}/memories")
public class MemoryGovernanceController {

    private static final Logger log = LoggerFactory.getLogger(MemoryGovernanceController.class);

    private final MemoryErasurePort erasure;
    private final MemoryExportPort export;

    public MemoryGovernanceController(MemoryErasurePort erasure, MemoryExportPort export) {
        this.erasure = erasure;
        this.export = export;
    }

    /**
     * Erases every active and archived memory for a team (right to erasure).
     *
     * @return 200 OK with the counts removed from the active store and the archive
     */
    @DeleteMapping
    public ResponseEntity<Map<String, Object>> erase(@PathVariable String tenantId,
                                                     @PathVariable String teamId) {
        var result = erasure.eraseTeam(new MemoryScope(tenantId, teamId));
        log.info("Erased team memories tenantId={} teamId={} active={} archived={}",
                tenantId, teamId, result.activeErased(), result.archivedErased());
        return ResponseEntity.ok(Map.of(
                "tenantId", result.tenantId(),
                "teamId", result.teamId(),
                "activeErased", result.activeErased(),
                "archivedErased", result.archivedErased()));
    }

    /**
     * Exports every active and archived memory for a team (data portability). Read-only and
     * non-reinforcing — the export never raises a memory's strength.
     *
     * @return 200 OK with the export (counts + memory snapshots)
     */
    @GetMapping("/export")
    public ResponseEntity<Map<String, Object>> export(@PathVariable String tenantId,
                                                      @PathVariable String teamId) {
        var result = export.exportTeam(new MemoryScope(tenantId, teamId));
        log.info("Exported team memories tenantId={} teamId={} active={} archived={}",
                tenantId, teamId, result.activeCount(), result.archivedCount());
        return ResponseEntity.ok(Map.of(
                "tenantId", result.tenantId(),
                "teamId", result.teamId(),
                "activeCount", result.activeCount(),
                "archivedCount", result.archivedCount(),
                "exportedAt", result.exportedAt().toString(),
                "memories", result.memories().stream().map(MemoryGovernanceController::toView).toList()));
    }

    private static Map<String, Object> toView(ExportedMemory m) {
        var view = new java.util.HashMap<String, Object>();
        view.put("id", m.id().toString());
        view.put("memoryType", m.memoryType().name());
        view.put("content", m.content());
        view.put("visibility", m.visibility().name());
        view.put("strength", m.strength());
        view.put("accessCount", m.accessCount());
        view.put("contributorCount", m.contributorCount());
        view.put("createdAt", m.createdAt().toString());
        view.put("lastAccessedAt", m.lastAccessedAt().toString());
        view.put("archived", m.archived());
        return view;
    }
}
