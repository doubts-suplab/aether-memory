package com.suplab.aether.memory.ports;

import com.suplab.aether.memory.domain.FederatedMemory;
import com.suplab.aether.memory.domain.FederationQuery;

import java.util.List;

/**
 * Outbound port for querying <em>remote</em> Aether Memory instances (federation peers).
 *
 * <p>Where {@link MemoryFederationPort} serves this instance's federatable memories to callers, this
 * port reaches the other direction: it fans a {@link FederationQuery} out to configured peer
 * instances and returns their {@link FederatedMemory} projections. Peers are the only outbound
 * network dependency; a failing or slow peer must be tolerated (skipped), never allowed to fail the
 * whole query. With no peers configured the client returns an empty list, so Memory still runs
 * standalone. Implementations live in {@code memory-engine}.</p>
 */
public interface FederationPeerClient {

    /**
     * Queries every configured peer instance and aggregates their federated projections.
     *
     * @param query the federation query to forward (the origin tenant is preserved for peer audit)
     * @return the combined projections returned by reachable peers (may be empty; never {@code null})
     */
    List<FederatedMemory> queryPeers(FederationQuery query);
}
