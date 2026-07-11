package com.suplab.aether.memory.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MemoryPolicyTest {

    @Test
    void defaults_matchEcosystemLifecycleConstants() {
        var policy = MemoryPolicy.defaults("tenant-1");

        assertThat(policy.tenantId()).isEqualTo("tenant-1");
        assertThat(policy.decayRate()).isEqualTo(0.01);
        assertThat(policy.decayAfterDays()).isEqualTo(7);
        assertThat(policy.reinforcementIncrement()).isEqualTo(0.1);
        assertThat(policy.archiveThreshold()).isEqualTo(0.1);
        assertThat(policy.retentionDays()).isEqualTo(90);
        assertThat(policy.federationEnabled()).isFalse();
    }

    @Test
    void constructor_acceptsValidCustomPolicy() {
        var policy = new MemoryPolicy("tenant-2", 0.05, 3, 0.2, 0.15, 30, true);

        assertThat(policy.decayRate()).isEqualTo(0.05);
        assertThat(policy.federationEnabled()).isTrue();
    }

    @Test
    void constructor_rejectsBlankTenantId() {
        assertThatThrownBy(() -> new MemoryPolicy("", 0.01, 7, 0.1, 0.1, 90, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId required");
    }

    @Test
    void constructor_rejectsDecayRateOutOfRange() {
        assertThatThrownBy(() -> new MemoryPolicy("tenant-1", 1.5, 7, 0.1, 0.1, 90, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("decayRate must be 0-1");
    }

    @Test
    void constructor_rejectsNegativeDecayAfterDays() {
        assertThatThrownBy(() -> new MemoryPolicy("tenant-1", 0.01, -1, 0.1, 0.1, 90, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("decayAfterDays must be >= 0");
    }

    @Test
    void constructor_rejectsReinforcementIncrementOutOfRange() {
        assertThatThrownBy(() -> new MemoryPolicy("tenant-1", 0.01, 7, 2.0, 0.1, 90, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reinforcementIncrement must be 0-1");
    }

    @Test
    void constructor_rejectsArchiveThresholdOutOfRange() {
        assertThatThrownBy(() -> new MemoryPolicy("tenant-1", 0.01, 7, 0.1, -0.5, 90, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("archiveThreshold must be 0-1");
    }

    @Test
    void constructor_rejectsNegativeRetentionDays() {
        assertThatThrownBy(() -> new MemoryPolicy("tenant-1", 0.01, 7, 0.1, 0.1, -1, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("retentionDays must be >= 0");
    }
}
