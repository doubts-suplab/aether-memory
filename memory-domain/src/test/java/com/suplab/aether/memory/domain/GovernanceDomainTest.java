package com.suplab.aether.memory.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GovernanceDomainTest {

    @Test
    void erasureAudit_teamScopeRequiresTeamId() {
        assertThatThrownBy(() -> new ErasureAuditEntry(UUID.randomUUID(), "tenant-1", null,
                ErasureScope.TEAM, 0, 0, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("teamId required for TEAM erasure");
    }

    @Test
    void erasureAudit_tenantScopeAllowsNullTeamId() {
        var entry = new ErasureAuditEntry(UUID.randomUUID(), "tenant-1", null,
                ErasureScope.TENANT, 3, 2, Instant.now());

        assertThat(entry.teamId()).isNull();
        assertThat(entry.scope()).isEqualTo(ErasureScope.TENANT);
        assertThat(entry.activeDeleted()).isEqualTo(3);
    }

    @Test
    void erasureAudit_rejectsNegativeCounts() {
        assertThatThrownBy(() -> new ErasureAuditEntry(UUID.randomUUID(), "tenant-1", "team-a",
                ErasureScope.TEAM, -1, 0, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("deleted counts must be >= 0");
    }

    @Test
    void policyChangeEntry_ofStampsNow_andRequiresPolicy() {
        var policy = MemoryPolicy.defaults("tenant-1");
        var entry = PolicyChangeEntry.of(policy);

        assertThat(entry.policy()).isEqualTo(policy);
        assertThat(entry.changedAt()).isNotNull();
        assertThat(entry.id()).isNotNull();

        assertThatThrownBy(() -> new PolicyChangeEntry(UUID.randomUUID(), null, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("policy required");
    }
}
