package io.quarkiverse.jimmer.runtime.cache;

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

import org.babyfish.jimmer.sql.cache.CacheFactory;
import org.babyfish.jimmer.sql.cache.CacheTracker;

import io.quarkiverse.jimmer.runtime.cfg.JimmerCacheConfig;
import io.quarkus.arc.Unremovable;
import io.quarkus.redis.datasource.RedisDataSource;

/**
 * Produces the Jimmer {@link CacheFactory} and {@link CacheTracker} beans when
 * {@code quarkus-redis-client} is on the classpath.
 */
public class JimmerRedisCacheProducer {

    @Produces
    @Singleton
    @Unremovable
    public CacheTracker cacheTracker(RedisDataSource redisDataSource) {
        return new QuarkusRedisCacheTracker(redisDataSource);
    }

    @Produces
    @Singleton
    @Unremovable
    public CacheFactory jimmerCacheFactory(
            RedisDataSource redisDataSource,
            JimmerCacheConfig config,
            Instance<CacheTracker> tracker) {
        return new JimmerRedisCacheFactory(
                redisDataSource,
                config,
                tracker.isResolvable() ? tracker.get() : null);
    }
}
