package com.suplab.aether.memory.api.controller;

import com.suplab.aether.memory.domain.MemoryPolicy;
import com.suplab.aether.memory.domain.PolicyChangeEvent;
import com.suplab.aether.memory.ports.MemoryPolicyStore;
import com.suplab.aether.memory.ports.PolicyChangeAuditStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Read/replace a tenant's shared-memory governance {@link MemoryPolicy}.
 *
 * <p>A {@code GET} always returns a policy — the tenant's configured one, or the ecosystem
 * defaults when none is set — so consumers never see a 404. A {@code PUT} replaces the tenant's
 * policy wholesale (upsert semantics) and appends an entry to the append-only
 * <strong>policy-change audit log</strong>; {@code GET .../audit} exposes that history.</p>
 */
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/memory-policy")
public class MemoryPolicyController {

    private static final Logger log = LoggerFactory.getLogger(MemoryPolicyController.class);

    private final MemoryPolicyStore policyStore;
    private final PolicyChangeAuditStore auditStore;

    public MemoryPolicyController(MemoryPolicyStore policyStore, PolicyChangeAuditStore auditStore) {
        this.policyStore = policyStore;
        this.auditStore = auditStore;
    }

    /**
     * Returns the effective policy for the tenant (configured or default).
     *
     * @return 200 OK with the policy fields
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> get(@PathVariable String tenantId) {
        return ResponseEntity.ok(toView(policyStore.resolve(tenantId)));
    }

    /**
     * Replaces the tenant's policy. Omitted fields fall back to the current default values so a
     * partial body still yields a valid policy. On success, appends a bounded delta (previous →
     * new) to the policy-change audit log, attributed to the optional {@code X-Actor} header.
     *
     * @return 200 OK with the stored policy; 400 if any value is out of range
     */
    @PutMapping
    public ResponseEntity<Map<String, Object>> replace(@PathVariable String tenantId,
                                                       @RequestHeader(value = "X-Actor", required = false) String actor,
                                                       @RequestBody Map<String, Object> body) {
        var defaults = MemoryPolicy.defaults(tenantId);
        try {
            var previous = policyStore.resolve(tenantId);
            var policy = new MemoryPolicy(
                    tenantId,
                    asDouble(body.get("decayRate"), defaults.decayRate()),
                    asInt(body.get("decayAfterDays"), defaults.decayAfterDays()),
                    asDouble(body.get("reinforcementIncrement"), defaults.reinforcementIncrement()),
                    asDouble(body.get("archiveThreshold"), defaults.archiveThreshold()),
                    asInt(body.get("retentionDays"), defaults.retentionDays()),
                    asBoolean(body.get("federationEnabled"), defaults.federationEnabled()),
                    asInt(body.get("federationSummaryChars"), defaults.federationSummaryChars()));
            policyStore.save(policy);
            auditStore.record(PolicyChangeEvent.of(tenantId, actor,
                    PolicyChangeEvent.describe(previous, policy)));
            log.info("Replaced memory policy tenantId={} federationEnabled={}",
                    tenantId, policy.federationEnabled());
            return ResponseEntity.ok(toView(policy));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Returns the tenant's recent policy-change history (who changed it, what changed, when),
     * newest first.
     *
     * @return 200 OK with the bounded audit view
     */
    @GetMapping("/audit")
    public ResponseEntity<List<Map<String, Object>>> audit(@PathVariable String tenantId,
                                                           @RequestParam(defaultValue = "50") int limit) {
        var body = auditStore.recentForTenant(tenantId, limit).stream()
                .map(MemoryPolicyController::toAuditView)
                .toList();
        return ResponseEntity.ok(body);
    }

    private static Map<String, Object> toAuditView(PolicyChangeEvent event) {
        var view = new java.util.LinkedHashMap<String, Object>();
        view.put("id", event.id().toString());
        view.put("tenantId", event.tenantId());
        view.put("actor", event.actor() != null ? event.actor() : "unattributed");
        view.put("summary", event.summary());
        view.put("occurredAt", event.occurredAt().toString());
        return view;
    }

    private static Map<String, Object> toView(MemoryPolicy policy) {
        return Map.of(
                "tenantId", policy.tenantId(),
                "decayRate", policy.decayRate(),
                "decayAfterDays", policy.decayAfterDays(),
                "reinforcementIncrement", policy.reinforcementIncrement(),
                "archiveThreshold", policy.archiveThreshold(),
                "retentionDays", policy.retentionDays(),
                "federationEnabled", policy.federationEnabled(),
                "federationSummaryChars", policy.federationSummaryChars());
    }

    private static double asDouble(Object value, double defaultValue) {
        if (value instanceof Number number) return number.doubleValue();
        if (value != null) {
            try {
                return Double.parseDouble(value.toString());
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private static int asInt(Object value, int defaultValue) {
        if (value instanceof Number number) return number.intValue();
        if (value != null) {
            try {
                return Integer.parseInt(value.toString());
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private static boolean asBoolean(Object value, boolean defaultValue) {
        if (value instanceof Boolean bool) return bool;
        if (value != null) return Boolean.parseBoolean(value.toString());
        return defaultValue;
    }
}
