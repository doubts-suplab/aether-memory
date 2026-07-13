package com.suplab.aether.memory.engine.federation;

import com.suplab.aether.memory.domain.FederatedMemory;
import com.suplab.aether.memory.domain.FederationAuditEntry;
import com.suplab.aether.memory.domain.FederationQuery;
import com.suplab.aether.memory.domain.MemoryType;
import com.suplab.aether.memory.ports.FederationAuditStore;
import com.suplab.aether.memory.ports.FederationPeerClient;
import com.suplab.aether.memory.ports.MemoryFederationPort;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultMemoryFederationGatewayTest {

    private static FederatedMemory fm(String summary, double strength, String provenance) {
        return new FederatedMemory(MemoryType.SEMANTIC, summary, strength, provenance);
    }

    private record RecordingAudit(List<FederationAuditEntry> entries) implements FederationAuditStore {
        RecordingAudit() { this(new ArrayList<>()); }
        @Override public void record(FederationAuditEntry entry) { entries.add(entry); }
        @Override public List<FederationAuditEntry> recentFor(String o, int limit) { return List.copyOf(entries); }
    }

    private static FederationQuery query(int limit) {
        return new FederationQuery("tenant-origin", null, "insight", limit);
    }

    @Test
    void localOnly_doesNotCallPeers_andAuditsLocalOnlyTrue() {
        MemoryFederationPort local = q -> List.of(fm("local A", 0.9, "tenant-a"));
        FederationPeerClient peers = (url, q) -> { throw new AssertionError("peers must not be called"); };
        var audit = new RecordingAudit();
        var gateway = new DefaultMemoryFederationGateway(local, peers, audit, List.of("http://peer:8083"));

        var results = gateway.search(query(10), false);

        assertThat(results).extracting(FederatedMemory::summary).containsExactly("local A");
        assertThat(audit.entries()).hasSize(1);
        assertThat(audit.entries().getFirst().localOnly()).isTrue();
        assertThat(audit.entries().getFirst().resultCount()).isEqualTo(1);
    }

    @Test
    void fanOut_mergesLocalAndPeerResults_rankedByStrength() {
        MemoryFederationPort local = q -> List.of(fm("local", 0.5, "tenant-a"));
        var peerResults = Map.of(
                "http://peer1:8083", List.of(fm("peer strong", 0.95, "tenant-b")),
                "http://peer2:8083", List.of(fm("peer weak", 0.2, "tenant-c")));
        FederationPeerClient peers = (url, q) -> peerResults.getOrDefault(url, List.of());
        var gateway = new DefaultMemoryFederationGateway(local, peers, new RecordingAudit(),
                List.of("http://peer1:8083", "http://peer2:8083"));

        var results = gateway.search(query(10), true);

        assertThat(results).extracting(FederatedMemory::summary)
                .containsExactly("peer strong", "local", "peer weak"); // strength desc
    }

    @Test
    void fanOut_dedupesBySummaryAndProvenance_keepingStrongest() {
        MemoryFederationPort local = q -> List.of(fm("dup", 0.4, "tenant-a"));
        FederationPeerClient peers = (url, q) -> List.of(fm("dup", 0.8, "tenant-a"));
        var gateway = new DefaultMemoryFederationGateway(local, peers, new RecordingAudit(),
                List.of("http://peer:8083"));

        var results = gateway.search(query(10), true);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().strength()).isEqualTo(0.8);
    }

    @Test
    void search_clampsToQueryLimit() {
        MemoryFederationPort local = q -> List.of(
                fm("a", 0.9, "t1"), fm("b", 0.8, "t2"), fm("c", 0.7, "t3"));
        FederationPeerClient peers = (url, q) -> List.of();
        var gateway = new DefaultMemoryFederationGateway(local, peers, new RecordingAudit(), List.of());

        var results = gateway.search(query(2), true);

        assertThat(results).hasSize(2);
        assertThat(results).extracting(FederatedMemory::summary).containsExactly("a", "b");
    }

    @Test
    void fanOut_withNoPeersConfigured_isLocalOnly() {
        MemoryFederationPort local = q -> List.of(fm("local", 0.5, "tenant-a"));
        FederationPeerClient peers = (url, q) -> { throw new AssertionError("no peers configured"); };
        var gateway = new DefaultMemoryFederationGateway(local, peers, new RecordingAudit(), List.of());

        var results = gateway.search(query(10), true);

        assertThat(results).extracting(FederatedMemory::summary).containsExactly("local");
    }
}
