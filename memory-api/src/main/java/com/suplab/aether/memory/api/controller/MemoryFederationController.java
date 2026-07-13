package com.suplab.aether.memory.api.controller;

import com.suplab.aether.memory.domain.FederationQuery;
import com.suplab.aether.memory.domain.MemoryType;
import com.suplab.aether.memory.engine.federation.FederationRateLimiter;
import com.suplab.aether.memory.ports.MemoryFederationGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Privacy-preserving cross-instance memory federation.
 *
 * <p>{@code POST /api/v1/federation/query} searches only {@code FEDERATED}-visibility memories in
 * tenants that have opted into federation, and returns coarse, length-bounded projections — never
 * raw memories, team identity, or contributor identity.</p>
 *
 * <ul>
 *   <li>By default the query <em>fans out</em> to configured peer instances and merges results.</li>
 *   <li>Inbound peer-to-peer calls pass {@code ?localOnly=true} so the query is answered locally
 *       and never recurses.</li>
 *   <li>Requests are rate-limited per origin tenant; exceeding the quota yields {@code 429}.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/federation")
public class MemoryFederationController {

    private static final Logger log = LoggerFactory.getLogger(MemoryFederationController.class);

    private final MemoryFederationGateway federationGateway;
    private final FederationRateLimiter rateLimiter;

    public MemoryFederationController(MemoryFederationGateway federationGateway,
                                      FederationRateLimiter rateLimiter) {
        this.federationGateway = federationGateway;
        this.rateLimiter = rateLimiter;
    }

    /**
     * Executes a federation query.
     *
     * <p>Request body: {@code {"originTenantId": "...", "queryText": "...", "type": "SEMANTIC",
     * "limit": 10}}. {@code type} is optional (null matches all types); {@code limit} is clamped
     * by the service.</p>
     *
     * @param localOnly when {@code true}, answer from the local corpus only (peer-to-peer mode)
     * @return 200 with federated projections; 400 on missing fields; 429 when rate-limited
     */
    @PostMapping("/query")
    public ResponseEntity<Object> query(@RequestBody Map<String, Object> body,
                                        @RequestParam(defaultValue = "false") boolean localOnly) {
        var originTenantId = asString(body.get("originTenantId"));
        var queryText = asString(body.get("queryText"));
        if (originTenantId == null || originTenantId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "originTenantId is required"));
        }
        if (queryText == null || queryText.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "queryText is required"));
        }

        if (!rateLimiter.tryAcquire(originTenantId)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "federation rate limit exceeded for origin tenant"));
        }

        MemoryType type = null;
        var typeRaw = asString(body.get("type"));
        if (typeRaw != null && !typeRaw.isBlank()) {
            try {
                type = MemoryType.valueOf(typeRaw.toUpperCase());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "invalid type; valid values: EPISODIC, SEMANTIC, PROCEDURAL, EMOTIONAL"));
            }
        }

        int limit = asInt(body.get("limit"), 10);
        var query = new FederationQuery(originTenantId, type, queryText, limit);
        var results = federationGateway.search(query, !localOnly).stream()
                .map(fm -> Map.<String, Object>of(
                        "type", fm.type().name(),
                        "summary", fm.summary(),
                        "strength", fm.strength(),
                        "provenance", fm.provenance()))
                .toList();

        log.info("Federation query served originTenantId={} type={} localOnly={} results={}",
                originTenantId, type, localOnly, results.size());
        return ResponseEntity.ok(results);
    }

    private static String asString(Object value) {
        return value != null ? value.toString() : null;
    }

    private static int asInt(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(value.toString());
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
}
