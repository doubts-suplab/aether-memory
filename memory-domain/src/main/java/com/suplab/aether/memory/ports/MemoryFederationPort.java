package com.suplab.aether.memory.ports;

import com.suplab.aether.memory.domain.FederatedMemory;
import com.suplab.aether.memory.domain.FederationQuery;

import java.util.List;

/**
 * Port interface for privacy-preserving cross-instance memory federation.
 *
 * <p>This is the only sanctioned way to read memories that a caller's own tenant does not own.
 * Implementations must enforce that results are drawn solely from {@code FEDERATED}-visibility
 * memories in tenants that permit federation, and must return {@link FederatedMemory}
 * projections — never raw {@link com.suplab.aether.memory.domain.SharedMemory} aggregates — so
 * team identity and full content never cross the boundary.</p>
 */
public interface MemoryFederationPort {

    /**
     * Executes a federation query and returns privacy-preserving projections ordered by
     * semantic relevance.
     *
     * @param query the federation request (origin tenant, optional type, query text, limit)
     * @return ordered list of federated projections (may be empty); never {@code null}
     */
    List<FederatedMemory> federatedSearch(FederationQuery query);
}
