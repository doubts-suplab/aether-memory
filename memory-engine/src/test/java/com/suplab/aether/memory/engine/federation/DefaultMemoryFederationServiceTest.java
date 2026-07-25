package com.suplab.aether.memory.engine.federation;

import com.suplab.aether.memory.domain.FederatedMemory;
import com.suplab.aether.memory.domain.FederationAuditEvent;
import com.suplab.aether.memory.domain.FederationQuery;
import com.suplab.aether.memory.domain.MemoryPolicy;
import com.suplab.aether.memory.domain.MemoryScope;
import com.suplab.aether.memory.domain.MemoryType;
import com.suplab.aether.memory.domain.MemoryVisibility;
import com.suplab.aether.memory.domain.SharedMemory;
import com.suplab.aether.memory.ports.FederationAuditStore;
import com.suplab.aether.memory.ports.FederationPeerClient;
import com.suplab.aether.memory.ports.MemoryPolicyStore;
import com.suplab.aether.memory.ports.SharedMemoryStore;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultMemoryFederationServiceTest {

    /** In-memory store recording the requested limit/type and returning a fixed corpus. */
    private static final class RecordingStore implements SharedMemoryStore {
        private final List<SharedMemory> corpus;
        int lastRequestedLimit = -1;
        MemoryType lastRequestedType;

        RecordingStore(List<SharedMemory> corpus) { this.corpus = corpus; }

        @Override public void save(SharedMemory memory, float[] embedding) { throw new UnsupportedOperationException(); }
        @Override public List<SharedMemory> findSimilar(MemoryScope s, float[] q, int l, double i) { throw new UnsupportedOperationException(); }
        @Override public List<SharedMemory> findByType(MemoryScope s, MemoryType t, int l, double i) { throw new UnsupportedOperationException(); }
        @Override public List<SharedMemory> findFederatable(float[] queryEmbedding, MemoryType type, int limit) {
            this.lastRequestedLimit = limit; this.lastRequestedType = type;
            return corpus.stream().limit(limit).toList();
        }
        @Override public Optional<SharedMemory> contribute(UUID id, MemoryScope s, double i) { throw new UnsupportedOperationException(); }
        @Override public void delete(UUID id, MemoryScope s) { throw new UnsupportedOperationException(); }
        @Override public long countByTeam(MemoryScope s) { throw new UnsupportedOperationException(); }
    }

    /** Policy store returning per-tenant redaction depth from a map (default policy otherwise). */
    private static final class FakePolicyStore implements MemoryPolicyStore {
        final Map<String, Integer> depthByTenant = new HashMap<>();
        @Override public MemoryPolicy resolve(String tenantId) {
            var d = depthByTenant.get(tenantId);
            return d == null ? MemoryPolicy.defaults(tenantId)
                    : new MemoryPolicy(tenantId, 0.01, 7, 0.1, 0.1, 90, true, d);
        }
        @Override public void save(MemoryPolicy policy) { }
        @Override public List<MemoryPolicy> findAll() { return List.of(); }
    }

    private static final class RecordingAudit implements FederationAuditStore {
        final List<FederationAuditEvent> events = new ArrayList<>();
        @Override public void record(FederationAuditEvent event) { events.add(event); }
        @Override public List<FederationAuditEvent> recent(int limit) { return List.copyOf(events); }
    }

    private static SharedMemory federated(String tenantId, String teamId, String content) {
        return SharedMemory.create(MemoryScope.of(tenantId, teamId), MemoryType.SEMANTIC, content,
                MemoryVisibility.FEDERATED);
    }

    private static DefaultMemoryFederationService service(SharedMemoryStore store, MemoryPolicyStore policies,
                                                          FederationAuditStore audit, FederationPeerClient peer) {
        return new DefaultMemoryFederationService(store, Optional.empty(), policies, audit,
                Optional.ofNullable(peer));
    }

    @Test
    void federatedSearch_projectsToPrivacyPreservingResults_andAudits() {
        var store = new RecordingStore(List.of(
                federated("tenant-a", "team-x", "shared insight A"),
                federated("tenant-b", "team-y", "shared insight B")));
        var audit = new RecordingAudit();
        var service = service(store, new FakePolicyStore(), audit, null);

        var results = service.federatedSearch(new FederationQuery("tenant-c", null, "insight", 10));

        assertThat(results).extracting(FederatedMemory::summary)
                .containsExactly("shared insight A", "shared insight B");
        // Provenance is the source tenant, never the owning team.
        assertThat(results).extracting(FederatedMemory::provenance).containsExactly("tenant-a", "tenant-b");
        // The served query is audited (origin + result count, no team identity).
        assertThat(audit.events).hasSize(1);
        assertThat(audit.events.getFirst().originTenantId()).isEqualTo("tenant-c");
        assertThat(audit.events.getFirst().resultCount()).isEqualTo(2);
    }

    @Test
    void federatedSearch_appliesPerOwnerRedactionDepth() {
        var store = new RecordingStore(List.of(
                federated("tenant-a", "team-x", "the quick brown fox jumps over the lazy dog")));
        var policies = new FakePolicyStore();
        policies.depthByTenant.put("tenant-a", 9); // tenant-a permits only 9 chars to leak

        var results = service(store, policies, new RecordingAudit(), null)
                .federatedSearch(new FederationQuery("tenant-c", null, "fox", 10));

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().summary()).hasSizeLessThanOrEqualTo(9);
        assertThat(results.getFirst().summary()).endsWith("…");
    }

    @Test
    void federatedSearch_clampsLimitToMaximum() {
        var store = new RecordingStore(List.of());
        service(store, new FakePolicyStore(), new RecordingAudit(), null)
                .federatedSearch(new FederationQuery("tenant-c", null, "insight",
                        DefaultMemoryFederationService.MAX_FEDERATION_LIMIT + 50));
        assertThat(store.lastRequestedLimit).isEqualTo(DefaultMemoryFederationService.MAX_FEDERATION_LIMIT);
    }

    @Test
    void federatedSearch_passesTypeFilterThrough() {
        var store = new RecordingStore(List.of());
        service(store, new FakePolicyStore(), new RecordingAudit(), null)
                .federatedSearch(new FederationQuery("tenant-c", MemoryType.PROCEDURAL, "how-to", 5));
        assertThat(store.lastRequestedType).isEqualTo(MemoryType.PROCEDURAL);
    }

    @Test
    void federatedFanout_mergesLocalAndPeerResults_orderedByStrength() {
        var store = new RecordingStore(List.of(federated("tenant-a", "team-x", "local memory")));
        FederationPeerClient peer = q -> List.of(
                new FederatedMemory(MemoryType.SEMANTIC, "weak peer", 0.30, "tenant-z"),
                new FederatedMemory(MemoryType.SEMANTIC, "strong peer", 1.00, "tenant-w"));

        var results = service(store, new FakePolicyStore(), new RecordingAudit(), peer)
                .federatedFanout(new FederationQuery("tenant-c", null, "memory", 10));

        assertThat(results).hasSize(3);
        // merged local + peers, strongest first; the weak peer sorts last
        assertThat(results.getLast().summary()).isEqualTo("weak peer");
        assertThat(results).extracting(FederatedMemory::summary)
                .contains("local memory", "strong peer", "weak peer");
    }

    @Test
    void federatedFanout_withNoPeerClient_isLocalOnly() {
        var store = new RecordingStore(List.of(federated("tenant-a", "team-x", "local memory")));
        var results = service(store, new FakePolicyStore(), new RecordingAudit(), null)
                .federatedFanout(new FederationQuery("tenant-c", null, "memory", 10));
        assertThat(results).extracting(FederatedMemory::summary).containsExactly("local memory");
    }
}
