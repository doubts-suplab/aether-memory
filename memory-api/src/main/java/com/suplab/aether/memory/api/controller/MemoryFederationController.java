package com.suplab.aether.memory.api.controller;

import com.suplab.aether.memory.domain.FederationQuery;
import com.suplab.aether.memory.domain.MemoryType;
import com.suplab.aether.memory.ports.MemoryFederationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Privacy-preserving cross-instance memory federation.
 *
 * <p>{@code POST /api/v1/federation/query} searches only {@code FEDERATED}-visibility memories in
 * tenants that have opted into federation, and returns coarse, length-bounded projections — never
 * raw memories, team identity, or contributor identity. This is the sanctioned read path for any
 * consumer that needs signal from beyond its own tenant.</p>
 */
@RestController
@RequestMapping("/api/v1/federation")
public class MemoryFederationController {

    private static final Logger log = LoggerFactory.getLogger(MemoryFederationController.class);

    private final MemoryFederationPort federationPort;

    public MemoryFederationController(MemoryFederationPort federationPort) {
        this.federationPort = federationPort;
    }

    /**
     * Executes a federation query.
     *
     * <p>Request body: {@code {"originTenantId": "...", "queryText": "...", "type": "SEMANTIC",
     * "limit": 10}}. {@code type} is optional (null matches all types); {@code limit} is clamped
     * by the service.</p>
     *
     * @return 200 OK with the list of federated projections; 400 on missing required fields
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
        var results = federationPort.federatedSearch(query).stream()
                .map(fm -> Map.<String, Object>of(
                        "type", fm.type().name(),
                        "summary", fm.summary(),
                        "strength", fm.strength(),
                        "provenance", fm.provenance()))
                .toList();

        log.info("Federation query served originTenantId={} type={} results={}",
                originTenantId, type, results.size());
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
