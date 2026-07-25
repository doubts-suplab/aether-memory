package com.suplab.aether.memory.engine.federation;

import com.suplab.aether.memory.domain.FederatedMemory;
import com.suplab.aether.memory.domain.FederationAuditEvent;
import com.suplab.aether.memory.domain.FederationQuery;
import com.suplab.aether.memory.domain.SharedMemory;
import com.suplab.aether.memory.engine.embedding.SharedEmbeddingService;
import com.suplab.aether.memory.ports.FederationAuditStore;
import com.suplab.aether.memory.ports.FederationPeerClient;
import com.suplab.aether.memory.ports.MemoryFederationPort;
import com.suplab.aether.memory.ports.MemoryPolicyStore;
import com.suplab.aether.memory.ports.SharedMemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Default implementation of {@link MemoryFederationPort}, hardened for Phase 2.
 *
 * <p>Federation is privacy-preserving by construction and now also governed:</p>
 * <ol>
 *   <li>Only {@code FEDERATED}-visibility memories in federation-enabled tenants are candidates
 *       (enforced in {@link SharedMemoryStore#findFederatable}).</li>
 *   <li>Each candidate is projected at its <strong>owning tenant's redaction depth</strong>
 *       ({@link com.suplab.aether.memory.domain.MemoryPolicy#federationSummaryChars()}) — a tenant
 *       controls exactly how much of its content may leak. Team identity, contributor identity, and
 *       raw IDs never cross the boundary.</li>
 *   <li>The requested limit is clamped to {@link #MAX_FEDERATION_LIMIT}.</li>
 *   <li>Every served query is written to the append-only {@link FederationAuditStore} — who queried,
 *       what type, a bounded query label, and how many results.</li>
 *   <li>{@link #federatedFanout} additionally reaches configured peer instances via a
 *       {@link FederationPeerClient} and merges their projections.</li>
 * </ol>
 *
 * <p>The query text is embedded via {@link SharedEmbeddingService}; when embeddings are disabled the
 * search degrades to a zero-vector match rather than failing.</p>
 */
public class DefaultMemoryFederationService implements MemoryFederationPort {

    private static final Logger log = LoggerFactory.getLogger(DefaultMemoryFederationService.class);

    /** Hard ceiling on results returned by any single federation query. */
    public static final int MAX_FEDERATION_LIMIT = 25;
    private static final int EMBEDDING_DIM = 384;

    private final SharedMemoryStore memoryStore;
    private final Optional<SharedEmbeddingService> embeddingService;
    private final MemoryPolicyStore policyStore;
    private final FederationAuditStore auditStore;
    private final Optional<FederationPeerClient> peerClient;

    public DefaultMemoryFederationService(SharedMemoryStore memoryStore,
                                          Optional<SharedEmbeddingService> embeddingService,
                                          MemoryPolicyStore policyStore,
                                          FederationAuditStore auditStore,
                                          Optional<FederationPeerClient> peerClient) {
        this.memoryStore = memoryStore;
        this.embeddingService = embeddingService;
        this.policyStore = policyStore;
        this.auditStore = auditStore;
        this.peerClient = peerClient;
    }

    @Override
    public List<FederatedMemory> federatedSearch(FederationQuery query) {
        var results = localSearch(query);
        audit(query, results.size());
        log.info("Federation query originTenantId={} type={} returned={}",
                query.originTenantId(), query.type(), results.size());
        return results;
    }

    @Override
    public List<FederatedMemory> federatedFanout(FederationQuery query) {
        var merged = new ArrayList<>(localSearch(query));
        peerClient.ifPresent(client -> merged.addAll(client.queryPeers(query)));
        var results = merged.stream()
                .sorted(Comparator.comparingDouble(FederatedMemory::strength).reversed())
                .limit(Math.min(query.limit(), MAX_FEDERATION_LIMIT))
                .toList();
        audit(query, results.size());
        log.info("Federation fan-out originTenantId={} type={} localPlusPeers={} returned={}",
                query.originTenantId(), query.type(), merged.size(), results.size());
        return results;
    }

    /** Local federatable search + per-owner redaction projection (no audit — the caller audits). */
    private List<FederatedMemory> localSearch(FederationQuery query) {
        int limit = Math.min(query.limit(), MAX_FEDERATION_LIMIT);
        var embedding = embeddingService
                .map(svc -> svc.embed(query.queryText()))
                .orElseGet(() -> new float[EMBEDDING_DIM]);

        var candidates = memoryStore.findFederatable(embedding, query.type(), limit);
        Map<String, Integer> depthByTenant = new HashMap<>();
        var results = new ArrayList<FederatedMemory>(candidates.size());
        for (SharedMemory memory : candidates) {
            int depth = depthByTenant.computeIfAbsent(memory.tenantId(),
                    t -> policyStore.resolve(t).federationSummaryChars());
            // Provenance is coarse — the source tenant, never the owning team.
            results.add(FederatedMemory.from(memory, memory.tenantId(), depth));
        }
        return results;
    }

    private void audit(FederationQuery query, int resultCount) {
        try {
            auditStore.record(FederationAuditEvent.of(
                    query.originTenantId(), query.type(), query.queryText(), resultCount));
        } catch (RuntimeException e) {
            log.warn("Failed to write federation audit for originTenantId={}: {}",
                    query.originTenantId(), e.getMessage());
        }
    }
}
