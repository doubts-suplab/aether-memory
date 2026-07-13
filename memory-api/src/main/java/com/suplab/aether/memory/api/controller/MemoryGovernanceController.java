package com.suplab.aether.memory.api.controller;

import com.suplab.aether.memory.domain.MemoryScope;
import com.suplab.aether.memory.domain.SharedMemory;
import com.suplab.aether.memory.ports.MemoryGovernancePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * GDPR governance endpoints: right-to-erasure, data export, and erasure audit.
 *
 * <p>Erasure spans the active and archive tables and writes an immutable proof-of-erasure record
 * in the same transaction (see {@link MemoryGovernancePort}). Export returns a team's active
 * memories for the data-access / portability right.</p>
 */
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}")
public class MemoryGovernanceController {

    private static final Logger log = LoggerFactory.getLogger(MemoryGovernanceController.class);

    private final MemoryGovernancePort governance;

    public MemoryGovernanceController(MemoryGovernancePort governance) {
        this.governance = governance;
    }

    /**
     * Right to erasure — team scope. Erases all of a team's memories (active + archive).
     *
     * @return 200 OK with the counts removed from each table
     */
    @DeleteMapping("/teams/{teamId}")
    public ResponseEntity<Map<String, Object>> eraseTeam(@PathVariable String tenantId,
                                                         @PathVariable String teamId) {
        var result = governance.eraseTeam(new MemoryScope(tenantId, teamId));
        log.info("Erase team requested tenantId={} teamId={} active={} archived={}",
                tenantId, teamId, result.activeDeleted(), result.archivedDeleted());
        return ResponseEntity.ok(Map.of(
                "tenantId", tenantId,
                "teamId", teamId,
                "activeDeleted", result.activeDeleted(),
                "archivedDeleted", result.archivedDeleted()));
    }

    /**
     * Right to erasure — tenant scope. Erases every team's memories (active + archive) and the
     * tenant's federation-audit trail.
     *
     * @return 200 OK with the counts removed from each memory table
     */
    @DeleteMapping
    public ResponseEntity<Map<String, Object>> eraseTenant(@PathVariable String tenantId) {
        var result = governance.eraseTenant(tenantId);
        log.info("Erase tenant requested tenantId={} active={} archived={}",
                tenantId, result.activeDeleted(), result.archivedDeleted());
        return ResponseEntity.ok(Map.of(
                "tenantId", tenantId,
                "activeDeleted", result.activeDeleted(),
                "archivedDeleted", result.archivedDeleted()));
    }

    /**
     * Data access / portability — exports a team's active memories as JSON.
     *
     * @return 200 OK with the full list of the team's active memories
     */
    @GetMapping("/teams/{teamId}/export")
    public ResponseEntity<List<Map<String, Object>>> export(@PathVariable String tenantId,
                                                            @PathVariable String teamId) {
        var memories = governance.exportTeam(new MemoryScope(tenantId, teamId)).stream()
                .map(MemoryGovernanceController::toView)
                .toList();
        return ResponseEntity.ok(memories);
    }

    /**
     * Returns the recent erasure audit trail for a tenant (newest first).
     *
     * @param limit maximum number of records (default 20)
     * @return 200 OK with the erasure audit entries
     */
    @GetMapping("/erasures")
    public ResponseEntity<List<Map<String, Object>>> erasures(@PathVariable String tenantId,
                                                             @RequestParam(defaultValue = "20") int limit) {
        var entries = governance.recentErasures(tenantId, limit).stream()
                .map(entry -> Map.<String, Object>of(
                        "tenantId", entry.tenantId(),
                        "teamId", entry.teamId() != null ? entry.teamId() : "",
                        "scope", entry.scope().name(),
                        "activeDeleted", entry.activeDeleted(),
                        "archivedDeleted", entry.archivedDeleted(),
                        "occurredAt", entry.occurredAt().toString()))
                .toList();
        return ResponseEntity.ok(entries);
    }

    private static Map<String, Object> toView(SharedMemory memory) {
        return Map.of(
                "memoryId", memory.id().toString(),
                "type", memory.type().name(),
                "content", memory.content(),
                "visibility", memory.visibility().name(),
                "strength", memory.strength(),
                "accessCount", memory.accessCount(),
                "contributorCount", memory.contributorCount(),
                "createdAt", memory.createdAt().toString(),
                "lastAccessedAt", memory.lastAccessedAt().toString());
    }
}
