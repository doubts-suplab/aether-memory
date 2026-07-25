package com.suplab.aether.memory.api.controller;

import com.suplab.aether.memory.domain.FederatedMemory;
import com.suplab.aether.memory.domain.FederationAuditEvent;
import com.suplab.aether.memory.domain.FederationQuery;
import com.suplab.aether.memory.domain.MemoryType;
import com.suplab.aether.memory.ports.FederationAuditStore;
import com.suplab.aether.memory.ports.FederationRateLimiter;
import com.suplab.aether.memory.ports.MemoryFederationPort;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryFederationControllerTest {

    private static final class FakeFederation implements MemoryFederationPort {
        boolean fanoutCalled;
        @Override public List<FederatedMemory> federatedSearch(FederationQuery q) {
            return List.of(new FederatedMemory(MemoryType.SEMANTIC, "local", 0.5, "tenant-a"));
        }
        @Override public List<FederatedMemory> federatedFanout(FederationQuery q) {
            fanoutCalled = true;
            return List.of(new FederatedMemory(MemoryType.SEMANTIC, "merged", 0.9, "tenant-z"));
        }
    }

    private static final class FixedLimiter implements FederationRateLimiter {
        private final boolean allow;
        FixedLimiter(boolean allow) { this.allow = allow; }
        @Override public boolean tryAcquire(String originTenantId) { return allow; }
        @Override public int maxPerWindow() { return 60; }
        @Override public int windowSeconds() { return 60; }
    }

    private static final class FakeAudit implements FederationAuditStore {
        @Override public void record(FederationAuditEvent event) { }
        @Override public List<FederationAuditEvent> recent(int limit) {
            return List.of(FederationAuditEvent.of("tenant-c", MemoryType.SEMANTIC, "insight", 2));
        }
    }

    private MemoryFederationController controller(boolean allow, FakeFederation fed) {
        return new MemoryFederationController(fed, new FixedLimiter(allow), new FakeAudit());
    }

    @Test
    void query_returnsProjectionsWhenWithinBudget() {
        var res = controller(true, new FakeFederation())
                .query(Map.of("originTenantId", "tenant-c", "queryText", "insight"));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) res.getBody()).hasSize(1);
    }

    @Test
    void query_returns429WhenRateLimited() {
        var res = controller(false, new FakeFederation())
                .query(Map.of("originTenantId", "tenant-c", "queryText", "insight"));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void query_missingFieldsAreBadRequest() {
        var c = controller(true, new FakeFederation());
        assertThat(c.query(Map.of("queryText", "x")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(c.query(Map.of("originTenantId", "t")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void query_includePeersRoutesToFanout() {
        var fed = new FakeFederation();
        var res = controller(true, fed)
                .query(Map.of("originTenantId", "tenant-c", "queryText", "insight", "includePeers", true));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fed.fanoutCalled).isTrue();
    }

    @Test
    void audit_returnsRecentEvents() {
        var res = controller(true, new FakeFederation()).audit(50);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) res.getBody()).hasSize(1);
    }
}
