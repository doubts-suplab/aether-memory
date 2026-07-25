package com.suplab.aether.memory.engine.federation;

import com.suplab.aether.memory.ports.FederationRateLimiter;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * In-memory, fixed-window {@link FederationRateLimiter} keyed by origin tenant.
 *
 * <p>Each origin gets {@code maxPerWindow} admissions per {@code windowSeconds}; the window is a
 * tumbling bucket (the current epoch-second divided by the window length). Counts are held per key
 * in a {@link ConcurrentHashMap} and updated atomically via {@code compute}, so the limiter is
 * thread-safe without external locking. This is a per-instance limiter — adequate to shield a single
 * node; a shared/distributed limiter is a drop-in behind the {@link FederationRateLimiter} port.</p>
 */
public class InMemoryFederationRateLimiter implements FederationRateLimiter {

    private record Window(long bucket, int count) {}

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final int maxPerWindow;
    private final int windowSeconds;
    private final LongSupplier clockSeconds;

    public InMemoryFederationRateLimiter(int maxPerWindow, int windowSeconds) {
        this(maxPerWindow, windowSeconds, () -> Instant.now().getEpochSecond());
    }

    InMemoryFederationRateLimiter(int maxPerWindow, int windowSeconds, LongSupplier clockSeconds) {
        this.maxPerWindow = maxPerWindow < 1 ? 1 : maxPerWindow;
        this.windowSeconds = windowSeconds < 1 ? 1 : windowSeconds;
        this.clockSeconds = clockSeconds;
    }

    @Override
    public boolean tryAcquire(String originTenantId) {
        long bucket = clockSeconds.getAsLong() / windowSeconds;
        var updated = windows.compute(originTenantId, (k, w) ->
                (w == null || w.bucket() != bucket) ? new Window(bucket, 1) : new Window(bucket, w.count() + 1));
        return updated.count() <= maxPerWindow;
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
