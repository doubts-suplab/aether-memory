package com.suplab.aether.memory.engine.federation;

import com.suplab.aether.memory.domain.FederatedMemory;
import com.suplab.aether.memory.domain.FederationQuery;
import com.suplab.aether.memory.engine.embedding.SharedEmbeddingService;
import com.suplab.aether.memory.ports.MemoryFederationPort;
import com.suplab.aether.memory.ports.SharedMemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * Default implementation of {@link MemoryFederationPort}.
 *
 * <p>Federation is privacy-preserving by construction:</p>
 * <ol>
 *   <li>Only memories at {@code FEDERATED} visibility in federation-enabled tenants are
 *       candidates — enforced in {@link SharedMemoryStore#findFederatable}.</li>
 *   <li>Results are projected to {@link FederatedMemory} — team identity, contributor identity,
 *       and raw IDs never cross the boundary; content is truncated to a bounded summary.</li>
 *   <li>The requested limit is clamped to {@link #MAX_FEDERATION_LIMIT} so a single query cannot
 *       exfiltrate an unbounded slice of the federated corpus.</li>
 * </ol>
 *
 * <p>The query text is embedded via {@link SharedEmbeddingService}; when embeddings are disabled
 * the search degrades to a zero-vector match rather than failing, consistent with the rest of the
 * ecosystem.</p>
 */
public class DefaultMemoryFederationService implements MemoryFederationPort {

    private static final Logger log = LoggerFactory.getLogger(DefaultMemoryFederationService.class);

    /** Hard ceiling on results returned by any single federation query. */
    public static final int MAX_FEDERATION_LIMIT = 25;
    private static final int EMBEDDING_DIM = 384;

    private final SharedMemoryStore memoryStore;
    private final Optional<SharedEmbeddingService> embeddingService;

    public DefaultMemoryFederationService(SharedMemoryStore memoryStore,
                                          Optional<SharedEmbeddingService> embeddingService) {
        this.memoryStore = memoryStore;
        this.embeddingService = embeddingService;
    }

    @Override
    public List<FederatedMemory> federatedSearch(FederationQuery query) {
        int limit = Math.min(query.limit(), MAX_FEDERATION_LIMIT);
        var embedding = embeddingService
                .map(svc -> svc.embed(query.queryText()))
                .orElseGet(() -> new float[EMBEDDING_DIM]);

        var candidates = memoryStore.findFederatable(embedding, query.type(), limit);
        var results = candidates.stream()
                // Provenance is coarse — the source tenant, never the owning team.
                .map(memory -> FederatedMemory.from(memory, memory.tenantId()))
                .toList();

        log.info("Federation query originTenantId={} type={} returned={} (limit={})",
                query.originTenantId(), query.type(), results.size(), limit);
        return results;
    }
}
