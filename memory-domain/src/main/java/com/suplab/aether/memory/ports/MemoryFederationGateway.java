package com.suplab.aether.memory.ports;

import com.suplab.aether.memory.domain.FederatedMemory;
import com.suplab.aether.memory.domain.FederationQuery;

import java.util.List;

/**
 * Orchestrates a federation query across the local instance and configured peers.
 *
 * <p>Where {@link MemoryFederationPort} answers from the <em>local</em> corpus only, the gateway
 * is the distributed entry point: it runs the local search and, when {@code fanOut} is requested,
 * forwards the query to each peer via {@link FederationPeerClient}, merges and de-duplicates the
 * results, and records an audit entry. Peer-to-peer calls invoke the gateway with
 * {@code fanOut = false} so federation never recurses.</p>
 */
public interface MemoryFederationGateway {

    /**
     * Runs a federation query.
     *
     * @param query  the federation request
     * @param fanOut when {@code true}, also query configured peers; when {@code false}, local-only
     *               (the mode used for inbound peer-to-peer calls)
     * @return merged, de-duplicated federated projections ordered by strength (may be empty)
     */
    List<FederatedMemory> search(FederationQuery query, boolean fanOut);
}
