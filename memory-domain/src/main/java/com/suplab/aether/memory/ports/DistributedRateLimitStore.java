package com.suplab.aether.memory.ports;

/**
 * Port for the shared counter a distributed rate limiter needs — an atomic increment with a
 * time-to-live, keyed by an arbitrary string.
 *
 * <p>This is the one primitive a fixed-window limiter requires to be <em>cross-instance</em>: bump a
 * per-window key and learn the running count, with the key expiring at the end of the window so it
 * never leaks. A Redis {@code INCR} + {@code EXPIRE} satisfies it exactly; the interface stays
 * framework-free so the limiter logic can be unit-tested against a fake and the real Redis adapter
 * lives in the API module. Implementations must be atomic per key so concurrent callers across
 * instances see a consistent count.</p>
 */
public interface DistributedRateLimitStore {

    /**
     * Atomically increments the counter at {@code key} and returns the new value. On the first
     * increment of a key the implementation sets an expiry of {@code ttlSeconds} so the window key is
     * reclaimed automatically.
     *
     * @param key        the window key (e.g. {@code "fedrl:<origin>:<bucket>"})
     * @param ttlSeconds how long the key should live (the window length)
     * @return the counter value after this increment (>= 1)
     */
    long incrementAndExpire(String key, int ttlSeconds);
}
