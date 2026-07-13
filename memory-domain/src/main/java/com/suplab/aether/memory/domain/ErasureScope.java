package com.suplab.aether.memory.domain;

/**
 * The breadth of a GDPR right-to-erasure request.
 *
 * <ul>
 *   <li>TEAM   — erase all memories owned by one {@code (tenantId, teamId)} scope</li>
 *   <li>TENANT — erase all memories across every team of a tenant, plus that tenant's
 *                federation-audit trail</li>
 * </ul>
 */
public enum ErasureScope {
    TEAM,
    TENANT
}
