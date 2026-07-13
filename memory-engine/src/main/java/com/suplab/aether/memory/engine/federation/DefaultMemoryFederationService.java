package com.suplab.aether.memory.engine.federation;

import com.suplab.aether.memory.domain.FederatedMemory;
import com.suplab.aether.memory.domain.FederationQuery;
import com.suplab.aether.memory.domain.SharedMemory;
import com.suplab.aether.memory.engine.embedding.SharedEmbeddingService;
import com.suplab.aether.memory.ports.MemoryFederationPort;
import com.suplab.aether.memory.ports.MemoryPolicyStore;
import com.suplab.aether.memory.ports.SharedMemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Default implementation of {@link MemoryFederationPort} — the <em>local</em> half of federation.
 *
 * <p>Federation is privacy-preserving by construction:</p>
 * <ol>
 *   <li>Only memories at {@code FEDERATED} visibility in federation-enabled tenants are
 *       candidates — enforced in {@link SharedMemoryStore#findFederatable}.</li>
 *   <li>Results are projected to {@link FederatedMemory} — team identity, contributor identity,
 *       and raw IDs never cross the boundary; content is truncated to the <em>source tenant's</em>
 *       configured redaction depth ({@link com.suplab.aether.memory.domain.MemoryPolicy#federationMaxSummaryLength()}).</li>
 *   <li>The requested limit is clamped to {@link #MAX_FEDERATION_LIMIT} so a single query cannot
 *       exfiltrate an unbounded slice of the federated corpus.</li>
 * </ol>
 *
 * <p>The query text is embedded via {@link SharedEmbeddingService}; when embeddings are disabled
 * the search degrades to a zero-vector match rather than failing.</p>
 */
public class DefaultMemoryFederationService implements MemoryFederationPort {

    private static final Logger log = LoggerFactory.getLogger(DefaultMemoryFederationService.class);

    /** Hard ceiling on results returned by any single federation query. */
    public static final int MAX_FEDERATION_LIMIT = 25;
    private static final int EMBEDDING_DIM = 384;

    private final SharedMemoryStore memoryStore;
    private final MemoryPolicyStore policyStore;
    private final Optional<SharedEmbeddingService> embeddingService;

    public DefaultMemoryFederationService(SharedMemoryStore memoryStore,
                                          MemoryPolicyStore policyStore,
                                          Optional<SharedEmbeddingService> embeddingService) {
        this.memoryStore = memoryStore;
        this.policyStore = policyStore;
        this.embeddingService = embeddingService;
    }

    @Override
    public List<FederatedMemory> federatedSearch(FederationQuery query) {
        int limit = Math.min(query.limit(), MAX_FEDERATION_LIMIT);
        var embedding = embeddingService
                .map(svc -> svc.embed(query.queryText()))
                .orElseGet(() -> new float[EMBEDDING_DIM]);

        var candidates = memoryStore.findFederatable(embedding, query.type(), limit);

        // Resolve each source tenant's redaction depth once per tenant.
        Map<String, Integer> depthByTenant = new HashMap<>();
        var results = candidates.stream()
                .map(memory -> FederatedMemory.from(memory, memory.tenantId(), redactionDepth(depthByTenant, memory)))
                .toList();

        log.info("Local federation search originTenantId={} type={} returned={} (limit={})",
                query.originTenantId(), query.type(), results.size(), limit);
        return results;
    }

    private int redactionDepth(Map<String, Integer> cache, SharedMemory memory) {
        return cache.computeIfAbsent(memory.tenantId(),
                tenantId -> policyStore.resolve(tenantId).federationMaxSummaryLength());
    }
}
