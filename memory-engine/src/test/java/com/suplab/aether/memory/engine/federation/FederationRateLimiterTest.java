package com.suplab.aether.memory.engine.federation;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FederationRateLimiterTest {

    @Test
    void allowsUpToCapacityThenThrottles() {
        var limiter = new FederationRateLimiter(3, 1_000L, () -> 0L);

        assertThat(limiter.tryAcquire("tenant-1")).isTrue();
        assertThat(limiter.tryAcquire("tenant-1")).isTrue();
        assertThat(limiter.tryAcquire("tenant-1")).isTrue();
        assertThat(limiter.tryAcquire("tenant-1")).isFalse();  // 4th in window → throttled
    }

    @Test
    void resetsAfterWindowRollover() {
        var now = new AtomicLong(0L);
        var limiter = new FederationRateLimiter(2, 1_000L, now::get);

        assertThat(limiter.tryAcquire("tenant-1")).isTrue();
        assertThat(limiter.tryAcquire("tenant-1")).isTrue();
        assertThat(limiter.tryAcquire("tenant-1")).isFalse();

        now.set(1_000L); // window rolled over
        assertThat(limiter.tryAcquire("tenant-1")).isTrue();
    }

    @Test
    void quotaIsPerOrigin() {
        var limiter = new FederationRateLimiter(1, 1_000L, () -> 0L);

        assertThat(limiter.tryAcquire("tenant-1")).isTrue();
        assertThat(limiter.tryAcquire("tenant-1")).isFalse();
        // A different origin has its own independent bucket.
        assertThat(limiter.tryAcquire("tenant-2")).isTrue();
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThatThrownBy(() -> new FederationRateLimiter(0, 1_000L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("capacity must be > 0");
        assertThatThrownBy(() -> new FederationRateLimiter(1, 0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("windowMillis must be > 0");
    }
}
