package com.suplab.aether.memory.ports;

import com.suplab.aether.memory.domain.FederationAuditEntry;

import java.util.List;

/**
 * Port interface for persisting {@link FederationAuditEntry} records.
 *
 * <p>Every federation query is recorded so operators can detect abuse and account for usage
 * without weakening the privacy of federated content — the audit captures the query, never the
 * matched memories.</p>
 */
public interface FederationAuditStore {

    /**
     * Persists one audit entry.
     *
     * @param entry the entry to store
     */
    void record(FederationAuditEntry entry);

    /**
     * Returns the most recent audit entries for a tenant, newest first.
     *
     * @param originTenantId the querying tenant
     * @param limit          maximum number of entries to return
     * @return audit entries (may be empty)
     */
    List<FederationAuditEntry> recentFor(String originTenantId, int limit);
}
