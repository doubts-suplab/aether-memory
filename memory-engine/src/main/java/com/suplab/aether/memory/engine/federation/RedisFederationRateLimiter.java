package com.suplab.aether.memory.engine.federation;

import com.suplab.aether.memory.ports.DistributedRateLimitStore;
import com.suplab.aether.memory.ports.FederationRateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.function.LongSupplier;

/**
 * Distributed, fixed-window {@link FederationRateLimiter} — the shared limiter the in-memory one is a
 * per-node approximation of.
 *
 * <p>It keys each origin's budget by a tumbling window bucket ({@code epochSecond / windowSeconds})
 * and counts admissions in a {@link DistributedRateLimitStore} (a Redis {@code INCR}+{@code EXPIRE} in
 * production), so every instance shares one counter and an origin's budget is enforced across the whole
 * fleet rather than per node. The window key carries the window's TTL, so counters expire on their own.</p>
 *
 * <p>Availability over strictness: if the shared store is unreachable (a Redis outage), the limiter
 * <strong>falls back to a local per-node limiter</strong> rather than failing open (no throttling) or
 * closed (all federation blocked) — throttling degrades to per-instance until the store recovers,
 * mirroring the fail-safe degradation used elsewhere in the ecosystem.</p>
 */
public class RedisFederationRateLimiter implements FederationRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisFederationRateLimiter.class);
    private static final String KEY_PREFIX = "fedrl:";

    private final DistributedRateLimitStore store;
    private final FederationRateLimiter fallback;
    private final int maxPerWindow;
    private final int windowSeconds;
    private final LongSupplier clockSeconds;

    public RedisFederationRateLimiter(DistributedRateLimitStore store, int maxPerWindow,
                                      int windowSeconds, FederationRateLimiter fallback) {
        this(store, maxPerWindow, windowSeconds, fallback, () -> Instant.now().getEpochSecond());
    }

    RedisFederationRateLimiter(DistributedRateLimitStore store, int maxPerWindow, int windowSeconds,
                               FederationRateLimiter fallback, LongSupplier clockSeconds) {
        if (store == null) throw new IllegalArgumentException("store required");
        if (fallback == null) throw new IllegalArgumentException("fallback limiter required");
        this.store = store;
        this.maxPerWindow = maxPerWindow < 1 ? 1 : maxPerWindow;
        this.windowSeconds = windowSeconds < 1 ? 1 : windowSeconds;
        this.fallback = fallback;
        this.clockSeconds = clockSeconds;
    }

    @Override
    public boolean tryAcquire(String originTenantId) {
        long bucket = clockSeconds.getAsLong() / windowSeconds;
        var key = KEY_PREFIX + originTenantId + ':' + bucket;
        try {
            long count = store.incrementAndExpire(key, windowSeconds);
            return count <= maxPerWindow;
        } catch (RuntimeException e) {
            // Shared store unavailable — degrade to per-node limiting rather than dropping throttling.
            log.warn("Distributed rate-limit store failed for origin={} — falling back to per-node limiter: {}",
                    originTenantId, e.getMessage());
            return fallback.tryAcquire(originTenantId);
        }
    }

    @Override
    public int maxPerWindow() {
        return maxPerWindow;
    }

    @Override
    public int windowSeconds() {
        return windowSeconds;
    }
}
