package com.suplab.aether.memory.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MemoryScopeAndVisibilityTest {

    @Test
    void scope_of_buildsScope() {
        var scope = MemoryScope.of("tenant-1", "team-alpha");

        assertThat(scope.tenantId()).isEqualTo("tenant-1");
        assertThat(scope.teamId()).isEqualTo("team-alpha");
    }

    @Test
    void scope_rejectsBlankTenantId() {
        assertThatThrownBy(() -> MemoryScope.of("", "team-alpha"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId required");
    }

    @Test
    void scope_rejectsBlankTeamId() {
        assertThatThrownBy(() -> new MemoryScope("tenant-1", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("teamId required");
    }

    @Test
    void visibility_onlyFederatedIsFederatable() {
        assertThat(MemoryVisibility.FEDERATED.isFederatable()).isTrue();
        assertThat(MemoryVisibility.TENANT.isFederatable()).isFalse();
        assertThat(MemoryVisibility.PRIVATE.isFederatable()).isFalse();
    }

    @Test
    void federationQuery_defaultsNonPositiveLimit() {
        var query = new FederationQuery("tenant-1", MemoryType.SEMANTIC, "search", 0);

        assertThat(query.limit()).isEqualTo(10);
    }

    @Test
    void federationQuery_rejectsBlankOriginTenant() {
        assertThatThrownBy(() -> new FederationQuery("", null, "search", 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("originTenantId required");
    }

    @Test
    void federationQuery_rejectsBlankQueryText() {
        assertThatThrownBy(() -> new FederationQuery("tenant-1", null, "  ", 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("queryText required");
    }

    @Test
    void federationQuery_allowsNullTypeForAllTypes() {
        var query = new FederationQuery("tenant-1", null, "search", 5);

        assertThat(query.type()).isNull();
    }
}
