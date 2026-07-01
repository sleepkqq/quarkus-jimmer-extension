package io.quarkiverse.jimmer.runtime.cache;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

import org.babyfish.jimmer.sql.cache.CacheTracker;
import org.babyfish.jimmer.sql.cache.redisson.RedissonCacheTracker;
import org.redisson.api.RedissonClient;

import io.quarkus.arc.Unremovable;

/**
 * Produces the Redisson-backed {@link CacheTracker} (cross-instance local-tier invalidation via Redis pub/sub).
 * Registered only when {@code redisson-quarkus} is on the classpath; consumed by the {@code FULL} cache mode.
 */
public class RedissonCacheSupportProducer {

    @Produces
    @Singleton
    @Unremovable
    public CacheTracker cacheTracker(RedissonClient redissonClient) {
        return new RedissonCacheTracker(redissonClient);
    }
}
