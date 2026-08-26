package com.suplab.aether.memory.api.controller;

import com.suplab.aether.memory.domain.MemoryPolicy;
import com.suplab.aether.memory.domain.PolicyChangeEvent;
import com.suplab.aether.memory.ports.MemoryPolicyStore;
import com.suplab.aether.memory.ports.PolicyChangeAuditStore;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryPolicyControllerTest {

    /** In-memory policy store — starts empty (resolve returns defaults). */
    private static final class FakePolicyStore implements MemoryPolicyStore {
        MemoryPolicy saved;
        @Override public MemoryPolicy resolve(String tenantId) {
            return saved != null ? saved : MemoryPolicy.defaults(tenantId);
        }
        @Override public void save(MemoryPolicy policy) { this.saved = policy; }
        @Override public List<MemoryPolicy> findAll() {
            return Optional.ofNullable(saved).map(List::of).orElseGet(List::of);
        }
    }

    /** Captures recorded audit events and serves them back. */
    private static final class FakeAuditStore implements PolicyChangeAuditStore {
        final List<PolicyChangeEvent> recorded = new ArrayList<>();
        @Override public void record(PolicyChangeEvent event) { recorded.add(event); }
        @Override public List<PolicyChangeEvent> recentForTenant(String tenantId, int limit) {
            return recorded.stream().filter(e -> e.tenantId().equals(tenantId)).limit(limit).toList();
        }
    }

    @Test
    void replace_recordsAnAuditEventWithDeltaAndActor() {
        var policyStore = new FakePolicyStore();
        var auditStore = new FakeAuditStore();
        var controller = new MemoryPolicyController(policyStore, auditStore);

        var res = controller.replace("tenant-a", "alice",
                Map.of("federationEnabled", true, "retentionDays", 30));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(auditStore.recorded).hasSize(1);
        var event = auditStore.recorded.getFirst();
        assertThat(event.actor()).isEqualTo("alice");
        assertThat(event.summary()).contains("federationEnabled: false → true");
        assertThat(event.summary()).contains("retentionDays: 90 → 30");
    }

    @Test
    void replace_withInvalidValue_isBadRequestAndRecordsNothing() {
        var policyStore = new FakePolicyStore();
        var auditStore = new FakeAuditStore();
        var controller = new MemoryPolicyController(policyStore, auditStore);

        var res = controller.replace("tenant-a", null, Map.of("decayRate", 5.0)); // out of 0-1 range

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(auditStore.recorded).isEmpty();
    }

    @Test
    void audit_returnsRecordedEventsForTenant() {
        var policyStore = new FakePolicyStore();
        var auditStore = new FakeAuditStore();
        var controller = new MemoryPolicyController(policyStore, auditStore);
        controller.replace("tenant-a", "bob", Map.of("federationEnabled", true));

        var res = controller.audit("tenant-a", 50);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).hasSize(1);
        assertThat(res.getBody().getFirst()).containsEntry("actor", "bob");
    }

    @Test
    void audit_unattributedWhenNoActorHeader() {
        var policyStore = new FakePolicyStore();
        var auditStore = new FakeAuditStore();
        var controller = new MemoryPolicyController(policyStore, auditStore);
        controller.replace("tenant-a", null, Map.of("retentionDays", 10));

        var body = controller.audit("tenant-a", 50).getBody();

        assertThat(body).hasSize(1);
        assertThat(body.getFirst()).containsEntry("actor", "unattributed");
    }
}
