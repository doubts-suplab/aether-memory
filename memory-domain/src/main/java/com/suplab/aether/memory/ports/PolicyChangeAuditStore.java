package com.suplab.aether.memory.ports;

import com.suplab.aether.memory.domain.PolicyChangeEvent;

import java.util.List;

/**
 * Port for the append-only policy-change audit log.
 *
 * <p>Records every accepted change to a tenant's {@link com.suplab.aether.memory.domain.MemoryPolicy}.
 * Write-once: {@link #record} appends, and there is deliberately no update or delete path.
 * Implementations live in {@code memory-engine}.</p>
 */
public interface PolicyChangeAuditStore {

    /**
     * Appends a policy-change audit event.
     *
     * @param event the applied-change record
     */
    void record(PolicyChangeEvent event);

    /**
     * Returns the most recent policy-change events for one tenant, newest first — a per-tenant
     * governance view of how its policy has evolved.
     *
     * @param tenantId the tenant whose history to read
     * @param limit    maximum number of events to return
     * @return recent change events (may be empty)
     */
    List<PolicyChangeEvent> recentForTenant(String tenantId, int limit);
}
