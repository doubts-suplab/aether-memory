package com.suplab.aether.memory.ports;

import com.suplab.aether.memory.domain.FederatedMemory;
import com.suplab.aether.memory.domain.FederationQuery;

import java.util.List;

/**
 * Port interface for querying a <em>remote</em> Aether Memory instance's federation endpoint.
 *
 * <p>This is the outbound half of cross-instance federation. The gateway fans a query out to each
 * configured peer through this port; the peer is called in <em>local-only</em> mode so it does not
 * recurse and fan out again. Implementations must be resilient — a slow or failing peer must not
 * fail the whole federated query.</p>
 */
public interface FederationPeerClient {

    /**
     * Queries a single peer instance and returns its privacy-preserving projections.
     *
     * @param peerBaseUrl the base URL of the peer Memory instance (e.g. {@code https://mem-eu:8083})
     * @param query       the federation query to forward
     * @return the peer's federated results, or an empty list if the peer is unreachable or errors
     */
    List<FederatedMemory> queryPeer(String peerBaseUrl, FederationQuery query);
}
