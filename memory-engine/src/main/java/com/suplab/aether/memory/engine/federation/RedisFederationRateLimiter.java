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
 * <p>Unreachable-store behaviour is configurable. By default the limiter favours
 * <strong>availability</strong>: on a Redis outage it <strong>falls back to a local per-node
 * limiter</strong> rather than failing open (no throttling) or closed (all federation blocked), so
 * throttling degrades to per-instance until the store recovers. When {@code failClosed} is set it
 * favours <strong>strictness</strong> instead: an unreachable store <strong>rejects</strong> the
 * request (never silently unlimited), matching the fail-closed posture of the per-peer federation auth
 * gate for deployments that require it.</p>
 */
public class RedisFederationRateLimiter implements FederationRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisFederationRateLimiter.class);
    private static final String KEY_PREFIX = "fedrl:";

    private final DistributedRateLimitStore store;
    private final FederationRateLimiter fallback;
    private final int maxPerWindow;
    private final int windowSeconds;
    private final boolean failClosed;
    private final LongSupplier clockSeconds;

    /** Availability-preserving default: an unreachable store degrades to the per-node fallback. */
    public RedisFederationRateLimiter(DistributedRateLimitStore store, int maxPerWindow,
                                      int windowSeconds, FederationRateLimiter fallback) {
        this(store, maxPerWindow, windowSeconds, fallback, false);
    }

    /**
     * @param failClosed when {@code true}, an unreachable store rejects the request (strict) instead of
     *                   degrading to the per-node {@code fallback} limiter (available).
     */
    public RedisFederationRateLimiter(DistributedRateLimitStore store, int maxPerWindow,
                                      int windowSeconds, FederationRateLimiter fallback,
                                      boolean failClosed) {
        this(store, maxPerWindow, windowSeconds, fallback, failClosed,
                () -> Instant.now().getEpochSecond());
    }

    RedisFederationRateLimiter(DistributedRateLimitStore store, int maxPerWindow, int windowSeconds,
                               FederationRateLimiter fallback, boolean failClosed,
                               LongSupplier clockSeconds) {
        if (store == null) throw new IllegalArgumentException("store required");
        if (fallback == null) throw new IllegalArgumentException("fallback limiter required");
        this.store = store;
        this.maxPerWindow = maxPerWindow < 1 ? 1 : maxPerWindow;
        this.windowSeconds = windowSeconds < 1 ? 1 : windowSeconds;
        this.fallback = fallback;
        this.failClosed = failClosed;
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
            if (failClosed) {
                // Strict: an unreachable store rejects rather than admitting (never silently unlimited).
                log.warn("Distributed rate-limit store failed for origin={} — failing closed (rejecting): {}",
                        originTenantId, e.getMessage());
                return false;
            }
            // Available: degrade to per-node limiting rather than dropping throttling.
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
