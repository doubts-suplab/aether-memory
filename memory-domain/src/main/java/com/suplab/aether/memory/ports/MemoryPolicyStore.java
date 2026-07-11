package com.suplab.aether.memory.ports;

import com.suplab.aether.memory.domain.MemoryPolicy;

import java.util.List;

/**
 * Port interface for per-tenant {@link MemoryPolicy} persistence.
 *
 * <p>A tenant that has never configured a policy must still resolve to one — implementations
 * return {@link MemoryPolicy#defaults(String)} rather than {@code null} or an empty optional,
 * so lifecycle and reinforcement code always has concrete parameters to work with.</p>
 */
public interface MemoryPolicyStore {

    /**
     * Resolves the effective policy for a tenant, falling back to
     * {@link MemoryPolicy#defaults(String)} when none has been configured.
     *
     * @param tenantId the tenant whose policy to resolve
     * @return the tenant's policy, never {@code null}
     */
    MemoryPolicy resolve(String tenantId);

    /**
     * Persists (inserts or replaces) a tenant's policy.
     *
     * @param policy the policy to store
     */
    void save(MemoryPolicy policy);

    /**
     * Returns every explicitly configured policy. Tenants relying on defaults are not listed.
     * Used by the lifecycle scheduler to iterate tenants with custom decay parameters.
     *
     * @return all stored policies (may be empty)
     */
    List<MemoryPolicy> findAll();
}
