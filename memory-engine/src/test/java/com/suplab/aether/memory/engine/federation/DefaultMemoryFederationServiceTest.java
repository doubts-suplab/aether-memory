package com.suplab.aether.memory.engine.federation;

import com.suplab.aether.memory.domain.FederatedMemory;
import com.suplab.aether.memory.domain.FederationQuery;
import com.suplab.aether.memory.domain.MemoryScope;
import com.suplab.aether.memory.domain.MemoryType;
import com.suplab.aether.memory.domain.MemoryVisibility;
import com.suplab.aether.memory.domain.SharedMemory;
import com.suplab.aether.memory.ports.SharedMemoryStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultMemoryFederationServiceTest {

    /**
     * Minimal in-memory store that records the requested limit and returns a fixed corpus.
     * Only {@code findFederatable} is exercised by the federation service.
     */
    private static final class RecordingStore implements SharedMemoryStore {
        private final List<SharedMemory> corpus;
        int lastRequestedLimit = -1;
        MemoryType lastRequestedType;

        RecordingStore(List<SharedMemory> corpus) {
            this.corpus = corpus;
        }

        @Override
        public void save(SharedMemory memory, float[] embedding) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<SharedMemory> findSimilar(MemoryScope scope, float[] q, int limit, double inc) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<SharedMemory> findByType(MemoryScope scope, MemoryType type, int limit, double inc) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<SharedMemory> findFederatable(float[] queryEmbedding, MemoryType type, int limit) {
            this.lastRequestedLimit = limit;
            this.lastRequestedType = type;
            return corpus.stream().limit(limit).toList();
        }

        @Override
        public void delete(UUID memoryId, MemoryScope scope) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long countByTeam(MemoryScope scope) {
            throw new UnsupportedOperationException();
        }
    }

    private static SharedMemory federated(String tenantId, String teamId, String content) {
        return SharedMemory.create(MemoryScope.of(tenantId, teamId), MemoryType.SEMANTIC, content,
                MemoryVisibility.FEDERATED);
    }

    @Test
    void federatedSearch_projectsToPrivacyPreservingResults() {
        var store = new RecordingStore(List.of(
                federated("tenant-a", "team-x", "shared insight A"),
                federated("tenant-b", "team-y", "shared insight B")));
        var service = new DefaultMemoryFederationService(store, Optional.empty());

        var results = service.federatedSearch(new FederationQuery("tenant-c", null, "insight", 10));

        assertThat(results).hasSize(2);
        assertThat(results).extracting(FederatedMemory::summary)
                .containsExactly("shared insight A", "shared insight B");
        // Provenance is the source tenant, never the owning team.
        assertThat(results).extracting(FederatedMemory::provenance)
                .containsExactly("tenant-a", "tenant-b");
    }

    @Test
    void federatedSearch_clampsLimitToMaximum() {
        var store = new RecordingStore(List.of());
        var service = new DefaultMemoryFederationService(store, Optional.empty());

        service.federatedSearch(new FederationQuery("tenant-c", null, "insight",
                DefaultMemoryFederationService.MAX_FEDERATION_LIMIT + 50));

        assertThat(store.lastRequestedLimit).isEqualTo(DefaultMemoryFederationService.MAX_FEDERATION_LIMIT);
    }

    @Test
    void federatedSearch_passesTypeFilterThrough() {
        var store = new RecordingStore(List.of());
        var service = new DefaultMemoryFederationService(store, Optional.empty());

        service.federatedSearch(new FederationQuery("tenant-c", MemoryType.PROCEDURAL, "how-to", 5));

        assertThat(store.lastRequestedType).isEqualTo(MemoryType.PROCEDURAL);
    }

    @Test
    void federatedSearch_returnsEmptyWhenNoFederatableMemories() {
        var store = new RecordingStore(List.of());
        var service = new DefaultMemoryFederationService(store, Optional.empty());

        var results = service.federatedSearch(new FederationQuery("tenant-c", null, "insight", 10));

        assertThat(results).isEmpty();
    }
}
