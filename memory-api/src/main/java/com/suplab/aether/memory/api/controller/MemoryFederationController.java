package com.suplab.aether.memory.api.controller;

import com.suplab.aether.memory.domain.FederationQuery;
import com.suplab.aether.memory.domain.MemoryType;
import com.suplab.aether.memory.ports.FederationAuditStore;
import com.suplab.aether.memory.ports.FederationRateLimiter;
import com.suplab.aether.memory.ports.MemoryFederationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Privacy-preserving cross-instance memory federation, hardened for Phase 2.
 *
 * <p>{@code POST /api/v1/federation/query} searches only {@code FEDERATED}-visibility memories in
 * federation-enabled tenants and returns coarse, length-bounded projections at each source tenant's
 * redaction depth — never raw memories, team identity, or contributor identity. Every query is
 * <strong>rate-limited per origin</strong> (429 when an origin exceeds its budget) and written to an
 * <strong>append-only audit log</strong>. Set {@code "includePeers": true} to fan the query out to
 * configured peer instances as well. {@code GET /api/v1/federation/audit} exposes recent activity for
 * governance.</p>
 */
@RestController
@RequestMapping("/api/v1/federation")
public class MemoryFederationController {

    private static final Logger log = LoggerFactory.getLogger(MemoryFederationController.class);

    private final MemoryFederationPort federationPort;
    private final FederationRateLimiter rateLimiter;
    private final FederationAuditStore auditStore;

    public MemoryFederationController(MemoryFederationPort federationPort,
                                      FederationRateLimiter rateLimiter,
                                      FederationAuditStore auditStore) {
        this.federationPort = federationPort;
        this.rateLimiter = rateLimiter;
        this.auditStore = auditStore;
    }

    /**
     * Executes a federation query (local, or local + peers when {@code includePeers} is true).
     *
     * <p>Request body: {@code {"originTenantId": "...", "queryText": "...", "type": "SEMANTIC",
     * "limit": 10, "includePeers": false}}.</p>
     *
     * @return 200 OK with the projections; 400 on missing fields; 429 when the origin is rate-limited
     */
    @PostMapping("/query")
    public ResponseEntity<Object> query(@RequestBody Map<String, Object> body) {
        var originTenantId = asString(body.get("originTenantId"));
        var queryText = asString(body.get("queryText"));
        if (originTenantId == null || originTenantId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "originTenantId is required"));
        }
        if (queryText == null || queryText.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "queryText is required"));
        }

        if (!rateLimiter.tryAcquire(originTenantId)) {
            log.warn("Federation query rate-limited originTenantId={} (max {}/{}s)",
                    originTenantId, rateLimiter.maxPerWindow(), rateLimiter.windowSeconds());
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header(HttpHeaders.RETRY_AFTER, String.valueOf(rateLimiter.windowSeconds()))
                    .body(Map.of("error", "federation rate limit exceeded",
                            "maxPerWindow", rateLimiter.maxPerWindow(),
                            "windowSeconds", rateLimiter.windowSeconds()));
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
        boolean includePeers = Boolean.TRUE.equals(body.get("includePeers"));
        var query = new FederationQuery(originTenantId, type, queryText, limit);
        var projections = includePeers
                ? federationPort.federatedFanout(query)
                : federationPort.federatedSearch(query);

        var results = projections.stream()
                .map(fm -> Map.<String, Object>of(
                        "type", fm.type().name(),
                        "summary", fm.summary(),
                        "strength", fm.strength(),
                        "provenance", fm.provenance()))
                .toList();

        log.info("Federation query served originTenantId={} type={} includePeers={} results={}",
                originTenantId, type, includePeers, results.size());
        return ResponseEntity.ok(results);
    }

    /**
     * Returns recent federation-audit events (who queried, what type, how many results), newest first.
     *
     * @return 200 OK with the audit view
     */
    @GetMapping("/audit")
    public ResponseEntity<Object> audit(@RequestParam(defaultValue = "50") int limit) {
        var body = auditStore.recent(limit).stream()
                .map(e -> Map.<String, Object>of(
                        "originTenantId", e.originTenantId(),
                        "type", e.type() != null ? e.type().name() : "ALL",
                        "queryLabel", e.queryLabel(),
                        "resultCount", e.resultCount(),
                        "occurredAt", e.occurredAt().toString()))
                .toList();
        return ResponseEntity.ok(body);
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
