package io.quarkiverse.jimmer.runtime.cache;

/**
 * Jimmer entity cache mode.
 *
 * <ul>
 * <li>{@link #REMOTE_ONLY} — single remote (Redis) tier. No per-instance staleness, every read hits Redis.</li>
 * <li>{@link #FULL} — two tiers: local Caffeine (L1) in front of remote Redis (L2). Lowest latency on hot keys;
 * requires a {@link org.babyfish.jimmer.sql.cache.CacheTracker} to invalidate the local tier across instances.</li>
 * </ul>
 */
public enum CacheMode {
    REMOTE_ONLY,
    FULL
}
