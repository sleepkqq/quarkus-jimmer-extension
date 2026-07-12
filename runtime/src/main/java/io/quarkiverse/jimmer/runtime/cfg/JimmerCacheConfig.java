package io.quarkiverse.jimmer.runtime.cfg;

import java.time.Duration;
import java.util.List;

import io.quarkiverse.jimmer.runtime.cache.CacheMode;
import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Per-entity Jimmer cache configuration, e.g.
 *
 * <pre>
 * quarkus:
 *   jimmer:
 *     cache:
 *       entities:
 *         - type: Book
 *           mode: FULL
 *           remote-ttl: PT30M
 *           local-ttl: PT30S
 *         - type: Order
 *           mode: REMOTE_ONLY
 *           remote-ttl: PT30M
 * </pre>
 */
@ConfigMapping(prefix = "quarkus.jimmer.cache")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface JimmerCacheConfig {

    /**
     * Entities for which a Jimmer cache should be created. An entity without an entry here is not cached.
     */
    List<EntityCacheConfig> entities();

    @ConfigGroup
    interface EntityCacheConfig {

        /**
         * Entity simple class name, e.g. {@code Book}.
         */
        String type();

        /**
         * Cache mode: {@code LOCAL_ONLY} (Caffeine only, works without Redis), {@code REMOTE_ONLY}
         * (Redis only) or {@code FULL} (Caffeine local tier in front of Redis).
         */
        @WithDefault("REMOTE_ONLY")
        CacheMode mode();

        /**
         * TTL of the remote (Redis) tier.
         */
        @WithDefault("PT30M")
        Duration remoteTtl();

        /**
         * TTL of the local (Caffeine) tier. Applies to {@code FULL} and {@code LOCAL_ONLY} modes. Keep it
         * short — it is the backstop for a missed cross-instance invalidation message (and, for
         * {@code LOCAL_ONLY} without Redis, the only consistency mechanism).
         */
        @WithDefault("PT30S")
        Duration localTtl();

        /**
         * Maximum entries of the local (Caffeine) tier. Applies to {@code FULL} and {@code LOCAL_ONLY} modes.
         */
        @WithDefault("10000")
        int localMaxSize();

        /**
         * Whether to cache this entity's associations (one-to-many / many-to-one id mappings) in addition to the
         * object cache. Defaults to {@code true}. Set to {@code false} for entities whose association cache cannot
         * be invalidated reliably — e.g. a child shared by two cached parents (bidirectional to two entities),
         * where Jimmer only evicts one inverse side. The object cache still applies.
         */
        @WithDefault("true")
        boolean cacheAssociations();

        /**
         * Arms {@link io.quarkiverse.jimmer.runtime.cache.AssociationEvictionGuard} for this entity: on every
         * insert/update/delete, force-evicts every inverse association cache reachable from a real-FK reference
         * prop, instead of trusting Jimmer's own diff-based auto eviction. Defaults to {@code false} — only set
         * {@code true} on entities actually affected by the eviction-asymmetry bug (typically a child with two
         * real-FK columns pointing at two different cached parents, e.g. a composite-key join row), since the
         * guard duplicates eviction work Jimmer already does correctly for everyone else.
         */
        @WithDefault("false")
        boolean forceAssociationEvict();

        /**
         * Random jitter percent added to the remote TTL to avoid a synchronized mass expiry (cache stampede).
         */
        @WithDefault("25")
        int randomPercent();
    }
}
