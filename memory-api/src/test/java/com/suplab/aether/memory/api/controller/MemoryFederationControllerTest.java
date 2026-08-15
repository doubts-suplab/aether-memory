package com.suplab.aether.memory.api.controller;

import com.suplab.aether.memory.domain.FederatedMemory;
import com.suplab.aether.memory.domain.FederationAuditEvent;
import com.suplab.aether.memory.domain.FederationQuery;
import com.suplab.aether.memory.domain.MemoryType;
import com.suplab.aether.memory.ports.FederationAuditStore;
import com.suplab.aether.memory.ports.FederationRateLimiter;
import com.suplab.aether.memory.api.security.FederationAuthenticator;
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

    // Auth disabled — the default standalone posture.
    private static final FederationAuthenticator NO_AUTH = new FederationAuthenticator(false, "");

    private MemoryFederationController controller(boolean allow, FakeFederation fed) {
        return new MemoryFederationController(fed, new FixedLimiter(allow), new FakeAudit(), NO_AUTH);
    }

    private MemoryFederationController controller(boolean allow, FakeFederation fed,
                                                 FederationAuthenticator auth) {
        return new MemoryFederationController(fed, new FixedLimiter(allow), new FakeAudit(), auth);
    }

    @Test
    void query_returnsProjectionsWhenWithinBudget() {
        var res = controller(true, new FakeFederation())
                .query(null, Map.of("originTenantId", "tenant-c", "queryText", "insight"));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) res.getBody()).hasSize(1);
    }

    @Test
    void query_returns429WhenRateLimited() {
        var res = controller(false, new FakeFederation())
                .query(null, Map.of("originTenantId", "tenant-c", "queryText", "insight"));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void query_missingFieldsAreBadRequest() {
        var c = controller(true, new FakeFederation());
        assertThat(c.query(null, Map.of("queryText", "x")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(c.query(null, Map.of("originTenantId", "t")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void query_includePeersRoutesToFanout() {
        var fed = new FakeFederation();
        var res = controller(true, fed)
                .query(null, Map.of("originTenantId", "tenant-c", "queryText", "insight", "includePeers", true));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fed.fanoutCalled).isTrue();
    }

    @Test
    void query_requireAuth_rejectsMissingOrWrongToken() {
        var auth = new FederationAuthenticator(true, "s3cret");
        var c = controller(true, new FakeFederation(), auth);
        var body = Map.<String, Object>of("originTenantId", "tenant-c", "queryText", "insight");

        assertThat(c.query(null, body).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(c.query("Bearer wrong", body).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void query_requireAuth_acceptsCorrectToken() {
        var auth = new FederationAuthenticator(true, "s3cret");
        var res = controller(true, new FakeFederation(), auth)
                .query("Bearer s3cret", Map.of("originTenantId", "tenant-c", "queryText", "insight"));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void audit_returnsRecentEvents() {
        var res = controller(true, new FakeFederation()).audit(50);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) res.getBody()).hasSize(1);
    }
}
