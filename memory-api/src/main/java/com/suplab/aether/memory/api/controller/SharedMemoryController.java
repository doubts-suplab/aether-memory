package com.suplab.aether.memory.api.controller;

import com.suplab.aether.memory.domain.MemoryScope;
import com.suplab.aether.memory.domain.MemoryType;
import com.suplab.aether.memory.domain.MemoryVisibility;
import com.suplab.aether.memory.domain.SharedMemory;
import com.suplab.aether.memory.engine.embedding.SharedEmbeddingService;
import com.suplab.aether.memory.ports.MemoryPolicyStore;
import com.suplab.aether.memory.ports.SharedMemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * CRUD operations for team-scoped shared memories.
 *
 * <p>Every path is scoped by {@code tenantId} + {@code teamId} — the multi-tenancy boundary of
 * Aether Memory. On write, content is embedded via Ollama and stored alongside the 384-dimension
 * vector for future semantic retrieval. When the embedding service is disabled
 * ({@code aether.memory.embedding.enabled=false}), a zero vector is stored and the memory is
 * still persisted.</p>
 *
 * <p>Reads reinforce on access using the tenant's configured reinforcement increment (shared
 * reinforcement).</p>
 */
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/teams/{teamId}/memories")
public class SharedMemoryController {

    private static final Logger log = LoggerFactory.getLogger(SharedMemoryController.class);
    private static final int EMBEDDING_DIM = 384;

    private final SharedMemoryStore memoryStore;
    private final MemoryPolicyStore policyStore;
    private final Optional<SharedEmbeddingService> embeddingService;

    public SharedMemoryController(SharedMemoryStore memoryStore,
                                  MemoryPolicyStore policyStore,
                                  Optional<SharedEmbeddingService> embeddingService) {
        this.memoryStore = memoryStore;
        this.policyStore = policyStore;
        this.embeddingService = embeddingService;
    }

    /**
     * Stores a new shared memory and its embedding vector.
     *
     * <p>Request body: {@code {"type": "SEMANTIC", "content": "...", "visibility": "TEAM"}}.
     * Type defaults to {@code SEMANTIC}; visibility defaults to {@code PRIVATE}.</p>
     *
     * @return 201 Created with {@code memoryId}, {@code type}, {@code visibility}; 400 on bad input
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> store(
            @PathVariable String tenantId,
            @PathVariable String teamId,
            @RequestBody Map<String, String> body) {

        var content = body.get("content");
        if (content == null || content.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "content is required"));
        }

        MemoryType type;
        try {
            type = MemoryType.valueOf(body.getOrDefault("type", "SEMANTIC").toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "invalid type; valid values: EPISODIC, SEMANTIC, PROCEDURAL, EMOTIONAL"));
        }

        MemoryVisibility visibility;
        try {
            visibility = MemoryVisibility.valueOf(body.getOrDefault("visibility", "PRIVATE").toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "invalid visibility; valid values: PRIVATE, TENANT, FEDERATED"));
        }

        var scope = new MemoryScope(tenantId, teamId);
        var memory = SharedMemory.create(scope, type, content, visibility);
        var embedding = embeddingService.map(svc -> svc.embed(content)).orElseGet(() -> new float[EMBEDDING_DIM]);
        memoryStore.save(memory, embedding);

        log.info("Stored {} shared memory id={} tenantId={} teamId={} visibility={} embeddingEnabled={}",
                type, memory.id(), tenantId, teamId, visibility, embeddingService.isPresent());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "memoryId", memory.id().toString(),
                "type", type.name(),
                "visibility", visibility.name()));
    }

    /**
     * Returns memories of a given type for the team, reinforced on read.
     *
     * @param type  the memory type to filter by (required)
     * @param limit maximum number of results (default 10)
     * @return 200 OK with the list of memories; 400 on invalid type
     */
    @GetMapping
    public ResponseEntity<Object> listByType(
            @PathVariable String tenantId,
            @PathVariable String teamId,
            @RequestParam String type,
            @RequestParam(defaultValue = "10") int limit) {

        MemoryType memoryType;
        try {
            memoryType = MemoryType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "invalid type; valid values: EPISODIC, SEMANTIC, PROCEDURAL, EMOTIONAL"));
        }

        var scope = new MemoryScope(tenantId, teamId);
        var increment = policyStore.resolve(tenantId).reinforcementIncrement();
        var memories = memoryStore.findByType(scope, memoryType, limit, increment);

        var body = memories.stream().map(SharedMemoryController::toView).toList();
        return ResponseEntity.ok(body);
    }

    /**
     * Semantic search: returns the memories most similar to a free-text query, reinforced on read.
     *
     * <p>The query is embedded via Ollama; when embeddings are disabled the search degrades to a
     * zero-vector match rather than failing.</p>
     *
     * @param q     the natural-language query text (required)
     * @param limit maximum number of results (default 10)
     * @return 200 OK with the ordered list of memories (nearest first); 400 if {@code q} is blank
     */
    @GetMapping("/search")
    public ResponseEntity<Object> search(
            @PathVariable String tenantId,
            @PathVariable String teamId,
            @RequestParam String q,
            @RequestParam(defaultValue = "10") int limit) {

        if (q == null || q.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "q (query text) is required"));
        }

        var scope = new MemoryScope(tenantId, teamId);
        var increment = policyStore.resolve(tenantId).reinforcementIncrement();
        var embedding = embeddingService.map(svc -> svc.embed(q)).orElseGet(() -> new float[EMBEDDING_DIM]);
        var memories = memoryStore.findSimilar(scope, embedding, limit, increment);

        return ResponseEntity.ok(memories.stream().map(SharedMemoryController::toView).toList());
    }

    /**
     * Records an additional distinct contributor re-asserting a memory (shared reinforcement):
     * {@code contributorCount} is incremented and strength reinforced by the tenant's configured
     * increment, atomically.
     *
     * @return 200 OK with the updated memory view; 404 if the memory does not exist in this team
     */
    @PostMapping("/{memoryId}/contribute")
    public ResponseEntity<Map<String, Object>> contribute(
            @PathVariable String tenantId,
            @PathVariable String teamId,
            @PathVariable UUID memoryId) {

        var scope = new MemoryScope(tenantId, teamId);
        var increment = policyStore.resolve(tenantId).reinforcementIncrement();
        return memoryStore.contribute(memoryId, scope, increment)
                .map(memory -> {
                    log.info("Contribution recorded memoryId={} tenantId={} teamId={} contributorCount={}",
                            memoryId, tenantId, teamId, memory.contributorCount());
                    return ResponseEntity.ok(toView(memory));
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "memory not found for this team")));
    }

    /**
     * Returns the total count of active memories for the team.
     *
     * @return 200 OK with {@code tenantId}, {@code teamId}, {@code count}
     */
    @GetMapping("/count")
    public ResponseEntity<Map<String, Object>> count(@PathVariable String tenantId,
                                                     @PathVariable String teamId) {
        long count = memoryStore.countByTeam(new MemoryScope(tenantId, teamId));
        return ResponseEntity.ok(Map.of("tenantId", tenantId, "teamId", teamId, "count", count));
    }

    /**
     * Deletes a specific memory. The tenant + team scoping prevents cross-team deletion.
     *
     * @return 204 No Content
     */
    @DeleteMapping("/{memoryId}")
    public ResponseEntity<Void> delete(@PathVariable String tenantId,
                                       @PathVariable String teamId,
                                       @PathVariable UUID memoryId) {
        memoryStore.delete(memoryId, new MemoryScope(tenantId, teamId));
        log.info("Deleted shared memory memoryId={} tenantId={} teamId={}", memoryId, tenantId, teamId);
        return ResponseEntity.noContent().build();
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
                "lastAccessedAt", memory.lastAccessedAt().toString());
    }
}
