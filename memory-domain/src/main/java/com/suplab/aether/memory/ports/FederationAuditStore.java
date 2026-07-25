package com.suplab.aether.memory.ports;

import com.suplab.aether.memory.domain.FederationAuditEvent;

import java.util.List;

/**
 * Port for the append-only federation audit log.
 *
 * <p>Records who queried the federation boundary and how much they received. Write-once:
 * {@link #record} appends, and there is deliberately no update or delete path. Implementations live
 * in {@code memory-engine}.</p>
 */
public interface FederationAuditStore {

    /**
     * Appends a federation-query audit event.
     *
     * @param event the served-query record
     */
    void record(FederationAuditEvent event);

    /**
     * Returns the most recent federation-audit events across all origins, newest first — a
     * governance view of federation activity.
     *
     * @param limit maximum number of events to return
     * @return recent audit events (may be empty)
     */
    List<FederationAuditEvent> recent(int limit);
}
