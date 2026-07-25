package com.suplab.aether.memory.engine.federation;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryFederationRateLimiterTest {

    @Test
    void admitsUpToMaxPerWindowThenThrottles() {
        var clock = new AtomicLong(1000);
        var limiter = new InMemoryFederationRateLimiter(3, 60, clock::get);

        assertThat(limiter.tryAcquire("tenant-1")).isTrue();
        assertThat(limiter.tryAcquire("tenant-1")).isTrue();
        assertThat(limiter.tryAcquire("tenant-1")).isTrue();
        assertThat(limiter.tryAcquire("tenant-1")).isFalse(); // 4th in window → throttled
    }

    @Test
    void budgetIsPerOrigin() {
        var clock = new AtomicLong(1000);
        var limiter = new InMemoryFederationRateLimiter(1, 60, clock::get);

        assertThat(limiter.tryAcquire("tenant-1")).isTrue();
        assertThat(limiter.tryAcquire("tenant-1")).isFalse();
        // a different origin has its own budget
        assertThat(limiter.tryAcquire("tenant-2")).isTrue();
    }

    @Test
    void budgetResetsInTheNextWindow() {
        var clock = new AtomicLong(1000);
        var limiter = new InMemoryFederationRateLimiter(1, 60, clock::get);

        assertThat(limiter.tryAcquire("tenant-1")).isTrue();
        assertThat(limiter.tryAcquire("tenant-1")).isFalse();

        clock.addAndGet(60); // advance one window
        assertThat(limiter.tryAcquire("tenant-1")).isTrue();
    }

    @Test
    void exposesConfiguredBounds() {
        var limiter = new InMemoryFederationRateLimiter(60, 60);
        assertThat(limiter.maxPerWindow()).isEqualTo(60);
        assertThat(limiter.windowSeconds()).isEqualTo(60);
    }
}
