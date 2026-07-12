package io.quarkiverse.jimmer.runtime.cache;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.babyfish.jimmer.meta.ImmutableProp;
import org.babyfish.jimmer.meta.ImmutableType;
import org.babyfish.jimmer.sql.cache.Cache;
import org.babyfish.jimmer.sql.cache.CacheCreator;
import org.babyfish.jimmer.sql.cache.CacheFactory;
import org.babyfish.jimmer.sql.cache.CacheTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkiverse.jimmer.runtime.cfg.JimmerCacheConfig;
import io.quarkus.redis.datasource.RedisDataSource;

/**
 * {@link CacheFactory} backed by Jimmer's Quarkus Redis cache binders. Builds a per-entity chain cache from
 * {@link JimmerCacheConfig}: {@code REMOTE_ONLY} yields a Redis-only cache, {@code FULL} adds a Caffeine local tier
 * kept consistent across instances by the (optional) {@link CacheTracker}.
 */
public class JimmerRedisCacheFactory implements CacheFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(JimmerRedisCacheFactory.class);

    private final RedisDataSource redisDataSource;
    private final CacheTracker tracker;
    private final Map<String, JimmerCacheConfig.EntityCacheConfig> configByType;

    public JimmerRedisCacheFactory(
            RedisDataSource redisDataSource,
            JimmerCacheConfig config,
            CacheTracker tracker) {
        this.redisDataSource = redisDataSource;
        this.tracker = tracker;
        this.configByType = new LinkedHashMap<>();
        for (JimmerCacheConfig.EntityCacheConfig entity : config.entities()) {
            configByType.put(entity.type(), entity);
            LOGGER.info("Jimmer cache enabled for entity {} [mode={}, remoteTtl={}]",
                    entity.type(), entity.mode(), entity.remoteTtl());
        }
    }

    @Override
    public Cache<?, ?> createObjectCache(ImmutableType type) {
        JimmerCacheConfig.EntityCacheConfig config = configByType.get(type.getJavaClass().getSimpleName());
        if (config == null) {
            return null;
        }
        return creator(config).createForObject(type);
    }

    @Override
    public Cache<?, ?> createAssociatedIdCache(ImmutableProp prop) {
        JimmerCacheConfig.EntityCacheConfig config = configByType.get(prop.getDeclaringType().getJavaClass().getSimpleName());
        if (config == null || !config.cacheAssociations() || !isCachedAssociation(prop)) {
            return null;
        }
        return creator(config).createForProp(prop, false);
    }

    @Override
    public Cache<?, List<?>> createAssociatedIdListCache(ImmutableProp prop) {
        JimmerCacheConfig.EntityCacheConfig config = configByType.get(prop.getDeclaringType().getJavaClass().getSimpleName());
        if (config == null || !config.cacheAssociations() || !isCachedAssociation(prop)) {
            return null;
        }
        return creator(config).createForProp(prop, false);
    }

    @Override
    public Cache<?, ?> createResolverCache(ImmutableProp prop) {
        return null;
    }

    private CacheCreator creator(JimmerCacheConfig.EntityCacheConfig config) {
        CacheCreator creator = new RedisCacheCreator(redisDataSource)
                .withRemoteDuration(config.remoteTtl(), config.randomPercent());

        if (config.mode() == CacheMode.FULL) {
            creator = creator.withLocalCache(config.localMaxSize(), config.localTtl());
            if (tracker != null) {
                creator = creator.withTracking(tracker);
            } else {
                LOGGER.warn("Entity {} uses FULL cache but no CacheTracker is available; "
                        + "the local tier may serve stale data across instances", config.type());
            }
        } else {
            creator = creator.withoutLocalCache();
        }
        return creator;
    }

    private boolean isCachedAssociation(ImmutableProp prop) {
        String sourceType = prop.getDeclaringType().getJavaClass().getSimpleName();
        ImmutableType targetType = prop.getTargetType();
        if (targetType == null) {
            return false;
        }
        return configByType.containsKey(sourceType) && configByType.containsKey(targetType.getJavaClass().getSimpleName());
    }
}
