package com.suplab.aether.memory.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PolicyChangeEventTest {

    @Test
    void of_populatesIdAndTimestamp() {
        var event = PolicyChangeEvent.of("tenant-a", "alice", "federationEnabled: false → true");

        assertThat(event.id()).isNotNull();
        assertThat(event.tenantId()).isEqualTo("tenant-a");
        assertThat(event.actor()).isEqualTo("alice");
        assertThat(event.summary()).isEqualTo("federationEnabled: false → true");
        assertThat(event.occurredAt()).isNotNull();
    }

    @Test
    void blankTenantIsRejected() {
        assertThatThrownBy(() -> PolicyChangeEvent.of("  ", "actor", "x"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void summaryAndActorAreTruncated() {
        var longSummary = "x".repeat(PolicyChangeEvent.MAX_SUMMARY + 50);
        var longActor = "a".repeat(PolicyChangeEvent.MAX_ACTOR + 50);

        var event = PolicyChangeEvent.of("tenant-a", longActor, longSummary);

        assertThat(event.summary()).hasSize(PolicyChangeEvent.MAX_SUMMARY);
        assertThat(event.actor()).hasSize(PolicyChangeEvent.MAX_ACTOR);
    }

    @Test
    void nullActorIsAllowed() {
        var event = PolicyChangeEvent.of("tenant-a", null, "no changes");
        assertThat(event.actor()).isNull();
    }

    @Test
    void describe_listsOnlyChangedFields() {
        var before = MemoryPolicy.defaults("tenant-a");
        var after = new MemoryPolicy("tenant-a", before.decayRate(), before.decayAfterDays(),
                before.reinforcementIncrement(), before.archiveThreshold(), 30, true,
                before.federationSummaryChars());

        var summary = PolicyChangeEvent.describe(before, after);

        assertThat(summary).contains("retentionDays: 90 → 30");
        assertThat(summary).contains("federationEnabled: false → true");
        assertThat(summary).doesNotContain("decayRate");
    }

    @Test
    void describe_identicalPoliciesReportNoChanges() {
        var policy = MemoryPolicy.defaults("tenant-a");
        assertThat(PolicyChangeEvent.describe(policy, policy)).isEqualTo("no changes");
    }
}
