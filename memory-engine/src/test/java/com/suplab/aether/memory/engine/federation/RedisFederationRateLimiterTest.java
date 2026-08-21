package com.suplab.aether.memory.engine.federation;

import com.suplab.aether.memory.ports.DistributedRateLimitStore;
import com.suplab.aether.memory.ports.FederationRateLimiter;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RedisFederationRateLimiterTest {

    /** In-memory stand-in for the shared Redis counter: atomic increment per key. */
    private static final class FakeStore implements DistributedRateLimitStore {
        final Map<String, Long> counts = new HashMap<>();
        @Override public long incrementAndExpire(String key, int ttlSeconds) {
            return counts.merge(key, 1L, Long::sum);
        }
    }

    /** Counting fallback limiter — records that it was consulted. */
    private static final class CountingFallback implements FederationRateLimiter {
        int calls = 0;
        @Override public boolean tryAcquire(String originTenantId) { calls++; return true; }
        @Override public int maxPerWindow() { return 1; }
        @Override public int windowSeconds() { return 1; }
    }

    @Test
    void admitsUpToMax_thenRejects_withinAWindow() {
        var limiter = new RedisFederationRateLimiter(new FakeStore(), 3, 60,
                new CountingFallback(), () -> 1_000L);

        assertThat(limiter.tryAcquire("origin")).isTrue();   // 1
        assertThat(limiter.tryAcquire("origin")).isTrue();   // 2
        assertThat(limiter.tryAcquire("origin")).isTrue();   // 3
        assertThat(limiter.tryAcquire("origin")).isFalse();  // 4 — over budget
    }

    @Test
    void budgetResetsOnNextWindow() {
        var now = new AtomicLong(1_000L);
        var limiter = new RedisFederationRateLimiter(new FakeStore(), 2, 60,
                new CountingFallback(), now::get);

        assertThat(limiter.tryAcquire("o")).isTrue();
        assertThat(limiter.tryAcquire("o")).isTrue();
        assertThat(limiter.tryAcquire("o")).isFalse();

        now.addAndGet(60);                 // advance one window → new bucket key
        assertThat(limiter.tryAcquire("o")).isTrue();
    }

    @Test
    void separatesOriginsIntoDistinctBudgets() {
        var limiter = new RedisFederationRateLimiter(new FakeStore(), 1, 60,
                new CountingFallback(), () -> 1_000L);

        assertThat(limiter.tryAcquire("a")).isTrue();
        assertThat(limiter.tryAcquire("b")).isTrue();   // b has its own budget
        assertThat(limiter.tryAcquire("a")).isFalse();  // a is now exhausted
    }

    @Test
    void storeFailure_fallsBackToLocalLimiter() {
        DistributedRateLimitStore failing = (key, ttl) -> {
            throw new IllegalStateException("redis down");
        };
        var fallback = new CountingFallback();
        var limiter = new RedisFederationRateLimiter(failing, 5, 60, fallback, () -> 1_000L);

        assertThat(limiter.tryAcquire("origin")).isTrue();  // fallback returns true
        assertThat(fallback.calls).isEqualTo(1);            // and it was actually consulted
    }

    @Test
    void exposesConfiguredBudgetAndWindow() {
        var limiter = new RedisFederationRateLimiter(new FakeStore(), 42, 30, new CountingFallback());
        assertThat(limiter.maxPerWindow()).isEqualTo(42);
        assertThat(limiter.windowSeconds()).isEqualTo(30);
    }

    @Test
    void rejectsNullStoreOrFallback() {
        assertThatThrownBy(() -> new RedisFederationRateLimiter(null, 1, 1, new CountingFallback()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RedisFederationRateLimiter(new FakeStore(), 1, 1, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
