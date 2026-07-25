package com.suplab.aether.memory.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FederationAuditEventTest {

    @Test
    void of_buildsEventWithIdAndTimestamp() {
        var event = FederationAuditEvent.of("tenant-1", MemoryType.SEMANTIC, "how to deploy", 4);

        assertThat(event.id()).isNotNull();
        assertThat(event.originTenantId()).isEqualTo("tenant-1");
        assertThat(event.type()).isEqualTo(MemoryType.SEMANTIC);
        assertThat(event.queryLabel()).isEqualTo("how to deploy");
        assertThat(event.resultCount()).isEqualTo(4);
        assertThat(event.occurredAt()).isNotNull();
    }

    @Test
    void queryLabelIsTruncatedToBound() {
        var longText = "q".repeat(500);
        var event = FederationAuditEvent.of("tenant-1", null, longText, 0);

        assertThat(event.queryLabel()).hasSize(FederationAuditEvent.MAX_QUERY_LABEL);
    }

    @Test
    void nullTypeIsAllowed() {
        assertThat(FederationAuditEvent.of("tenant-1", null, "all types", 2).type()).isNull();
    }

    @Test
    void rejectsBlankOriginAndNegativeCount() {
        assertThatThrownBy(() -> FederationAuditEvent.of(" ", null, "q", 1))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("originTenantId");
        assertThatThrownBy(() -> FederationAuditEvent.of("t", null, "q", -1))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("resultCount");
    }
}
