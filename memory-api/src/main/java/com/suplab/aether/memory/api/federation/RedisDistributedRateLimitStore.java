package com.suplab.aether.memory.api.federation;

import com.suplab.aether.memory.ports.DistributedRateLimitStore;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/**
 * Redis-backed {@link DistributedRateLimitStore} — the shared counter for the distributed federation
 * rate limiter.
 *
 * <p>Implements the atomic fixed-window primitive with a Redis {@code INCR} followed by an
 * {@code EXPIRE} on the first increment of a key, so every {@code memory-api} instance sharing the
 * Redis increments one counter per {@code (origin, window)} and the key is reclaimed when the window
 * ends. Uses {@link StringRedisTemplate} (Lettuce), autoconfigured from {@code spring.data.redis.*};
 * credentials never live in source.</p>
 */
public class RedisDistributedRateLimitStore implements DistributedRateLimitStore {

    private final StringRedisTemplate redis;

    public RedisDistributedRateLimitStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public long incrementAndExpire(String key, int ttlSeconds) {
        Long count = redis.opsForValue().increment(key);
        long value = count == null ? 1L : count;
        if (value == 1L) {
            // First hit in this window — set the key to expire at the window boundary.
            redis.expire(key, Duration.ofSeconds(ttlSeconds));
        }
        return value;
    }
}
