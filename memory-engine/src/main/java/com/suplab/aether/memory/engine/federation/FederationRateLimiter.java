package com.suplab.aether.memory.engine.federation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Per-origin fixed-window rate limiter for federation queries.
 *
 * <p>Keyed by {@code originTenantId}: each origin may issue up to {@code capacity} queries per
 * {@code windowMillis}. When the window rolls over the counter resets. Thread-safe via
 * {@link ConcurrentHashMap#compute}. The clock is injectable so behaviour is deterministic under
 * test.</p>
 *
 * <p>This limiter is <em>per instance</em> — appropriate for a scaffold and single-node
 * deployments. A distributed limiter (e.g. Redis-backed) is a future hardening step; the port
 * boundary here does not change when that arrives.</p>
 */
public class FederationRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(FederationRateLimiter.class);

    private final int capacity;
    private final long windowMillis;
    private final LongSupplier clock;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    /**
     * @param capacity     maximum queries allowed per origin per window (must be > 0)
     * @param windowMillis window length in milliseconds (must be > 0)
     */
    public FederationRateLimiter(int capacity, long windowMillis) {
        this(capacity, windowMillis, System::currentTimeMillis);
    }

    FederationRateLimiter(int capacity, long windowMillis, LongSupplier clock) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be > 0");
        if (windowMillis <= 0) throw new IllegalArgumentException("windowMillis must be > 0");
        this.capacity = capacity;
        this.windowMillis = windowMillis;
        this.clock = clock;
    }

    /**
     * Attempts to consume one unit of quota for the given origin.
     *
     * @param originTenantId the querying tenant
     * @return {@code true} if the request is within quota, {@code false} if the origin is throttled
     */
    public boolean tryAcquire(String originTenantId) {
        long now = clock.getAsLong();
        // Single-element holder captured by the compute lambda to report this call's outcome.
        boolean[] allowed = {false};
        windows.compute(originTenantId, (key, window) -> {
            if (window == null || now - window.startMillis >= windowMillis) {
                allowed[0] = true;
                return new Window(now, 1);           // fresh window
            }
            if (window.count < capacity) {
                allowed[0] = true;
                return new Window(window.startMillis, window.count + 1);
            }
            allowed[0] = false;
            return window;                            // unchanged — over quota
        });
        if (!allowed[0]) {
            log.warn("Federation rate limit exceeded originTenantId={} capacity={} windowMillis={}",
                    originTenantId, capacity, windowMillis);
        }
        return allowed[0];
    }

    private record Window(long startMillis, int count) {}
}
