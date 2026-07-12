package io.quarkiverse.jimmer.runtime.cache;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.babyfish.jimmer.jackson.codec.JsonCodec;
import org.babyfish.jimmer.meta.ImmutableProp;
import org.babyfish.jimmer.meta.ImmutableType;
import org.babyfish.jimmer.sql.cache.CacheTracker;
import org.babyfish.jimmer.sql.cache.RemoteKeyPrefixProvider;
import org.babyfish.jimmer.sql.cache.spi.AbstractRemoteValueBinder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.GetExArgs;
import io.quarkus.redis.datasource.value.ValueCommands;

/**
 * Redis tier of the Jimmer object/reference cache on the Quarkus Redis (Vert.x) client.
 * Ported from Jimmer's deprecated {@code org.babyfish.jimmer.sql.cache.redis.quarkus} package —
 * framework-specific binders are the extension's responsibility.
 */
public class RedisValueBinder<K, V> extends AbstractRemoteValueBinder<K, V> {

    private final ValueCommands<String, byte[]> operations;

    protected RedisValueBinder(
            @Nullable ImmutableType type,
            @Nullable ImmutableProp prop,
            @Nullable CacheTracker tracker,
            @NotNull JsonCodec<?> jsonCodec,
            @Nullable RemoteKeyPrefixProvider keyPrefixProvider,
            @NotNull Duration duration,
            int randomPercent,
            @NotNull RedisDataSource redisDataSource) {
        super(type, prop, tracker, jsonCodec, keyPrefixProvider, duration, randomPercent);
        this.operations = redisDataSource.value(byte[].class);
    }

    @Override
    protected List<byte[]> read(Collection<String> keys) {
        if (keys.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, byte[]> values = operations.mget(keys.toArray(new String[0]));
        return new ArrayList<>(values.values());
    }

    @Override
    protected void write(Map<String, byte[]> map) {
        operations.mset(map);
        for (String key : map.keySet()) {
            operations.getex(key, new GetExArgs().px(nextExpireMillis()));
        }
    }

    @Override
    protected void deleteAllSerializedKeys(List<String> serializedKeys) {
        for (String key : serializedKeys) {
            operations.getdel(key);
        }
    }

    @Override
    protected boolean matched(@Nullable Object reason) {
        return "redis".equals(reason);
    }

    @NotNull
    public static <K, V> Builder<K, V> forObject(ImmutableType type, @NotNull JsonCodec<?> jsonCodec) {
        return new Builder<>(type, null, jsonCodec);
    }

    @NotNull
    public static <K, V> Builder<K, V> forProp(ImmutableProp prop, @NotNull JsonCodec<?> jsonCodec) {
        return new Builder<>(null, prop, jsonCodec);
    }

    public static class Builder<K, V> extends AbstractBuilder<K, V, Builder<K, V>> {

        private RedisDataSource redisDataSource;

        protected Builder(ImmutableType type, ImmutableProp prop, JsonCodec<?> jsonCodec) {
            super(type, prop, jsonCodec);
        }

        public Builder<K, V> redis(RedisDataSource redisDataSource) {
            this.redisDataSource = redisDataSource;
            return this;
        }

        public RedisValueBinder<K, V> build() {
            if (redisDataSource == null) {
                throw new IllegalStateException("RedisDataSource has not been specified");
            }
            return new RedisValueBinder<>(
                    type, prop, tracker, jsonCodec, keyPrefixProvider, duration, randomPercent, redisDataSource);
        }
    }
}
