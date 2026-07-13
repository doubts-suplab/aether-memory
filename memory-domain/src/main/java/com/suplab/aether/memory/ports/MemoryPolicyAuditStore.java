package com.suplab.aether.memory.ports;

import com.suplab.aether.memory.domain.PolicyChangeEntry;

import java.util.List;

/**
 * Port for persisting {@link PolicyChangeEntry} snapshots — one per policy replacement — so the
 * evolution of a tenant's governance settings is auditable.
 */
public interface MemoryPolicyAuditStore {

    /**
     * Records a policy-change snapshot.
     *
     * @param entry the change to record
     */
    void record(PolicyChangeEntry entry);

    /**
     * Returns the most recent policy changes for a tenant, newest first.
     *
     * @param tenantId the tenant to report on
     * @param limit    maximum number of entries
     * @return change entries (may be empty)
     */
    List<PolicyChangeEntry> recentFor(String tenantId, int limit);
}
