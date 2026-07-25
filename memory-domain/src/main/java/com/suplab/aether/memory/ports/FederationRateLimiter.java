package com.suplab.aether.memory.ports;

/**
 * Port for per-origin rate limiting of federation queries.
 *
 * <p>Federation is a cross-tenant read path, so a single origin must not be able to hammer it. The
 * limiter throttles by {@code originTenantId}: each call to {@link #tryAcquire} consumes one unit of
 * that origin's budget for the current window and reports whether the request may proceed. The
 * default implementation is in-memory (per-instance); a distributed limiter is a drop-in behind this
 * port. Implementations live in {@code memory-engine}.</p>
 */
public interface FederationRateLimiter {

    /**
     * Attempts to admit one federation query from an origin.
     *
     * @param originTenantId the querying tenant
     * @return {@code true} if the request is within budget and may proceed; {@code false} if the
     *         origin has exceeded its allowance for the current window
     */
    boolean tryAcquire(String originTenantId);

    /**
     * @return the maximum number of queries an origin may make per window (for {@code Retry-After}
     *         hints and diagnostics).
     */
    int maxPerWindow();

    /**
     * @return the window length in seconds.
     */
    int windowSeconds();
}
