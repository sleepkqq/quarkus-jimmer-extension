package io.quarkiverse.jimmer.runtime.cache;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.babyfish.jimmer.meta.ImmutableProp;
import org.babyfish.jimmer.meta.ImmutableType;
import org.babyfish.jimmer.sql.cache.CacheTracker;
import org.babyfish.jimmer.sql.cache.spi.AbstractCacheTracker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.pubsub.PubSubCommands;

/**
 * {@link CacheTracker} on plain Redis PUB/SUB via the Quarkus Redis (Vert.x) client — cross-instance
 * invalidation of the local tier of {@code FULL} caches without a Redisson dependency. The message
 * is a plain-JSON DTO serialized by the client's Jackson codec, so it also works in a GraalVM
 * native image (Redisson's default Kryo codec needs Objenesis / {@code sun.reflect.ReflectionFactory},
 * which is absent there).
 */
public class QuarkusRedisCacheTracker extends AbstractCacheTracker {

    private static final String CHANNEL = "_quarkus_jimmer_:invalidate";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final UUID trackerId = UUID.randomUUID();

    private final PubSubCommands<InvalidationMessage> pubSub;

    @SuppressWarnings("unused") // keeps the subscription alive for the application's lifetime
    private final PubSubCommands.RedisSubscriber subscriber;

    public QuarkusRedisCacheTracker(RedisDataSource redisDataSource) {
        pubSub = redisDataSource.pubsub(InvalidationMessage.class);
        subscriber = pubSub.subscribe(CHANNEL, msg -> {
            if (!trackerId.equals(msg.trackerId)) {
                firer().invalidate(msg.toEvent());
            }
        });
    }

    @Override
    protected void publishInvalidationEvent(CacheTracker.InvalidateEvent event) {
        pubSub.publish(CHANNEL, new InvalidationMessage(trackerId, event));
    }

    public static final class InvalidationMessage {

        public UUID trackerId;

        public String typeName;

        public String propName;

        public Collection<?> ids;

        public InvalidationMessage() {
        }

        InvalidationMessage(UUID trackerId, CacheTracker.InvalidateEvent event) {
            this.trackerId = trackerId;
            this.typeName = event.getType().toString();
            this.propName = event.getProp() != null ? event.getProp().getName() : null;
            this.ids = event.getIds();
        }

        CacheTracker.InvalidateEvent toEvent() {
            ImmutableType type = resolveType();
            // JSON carries no id type info — rebuild the collection as the entity's id type
            // (UUIDs and longs otherwise arrive as strings/ints and never match cache keys).
            CollectionType idsType = MAPPER.getTypeFactory()
                    .constructCollectionType(List.class, type.getIdProp().getReturnClass());
            Collection<?> typedIds = MAPPER.convertValue(ids, idsType);
            if (propName != null) {
                ImmutableProp prop = type.getProp(propName);
                return new CacheTracker.InvalidateEvent(prop, typedIds);
            }
            return new CacheTracker.InvalidateEvent(type, typedIds);
        }

        private ImmutableType resolveType() {
            Class<?> javaType;
            try {
                javaType = Class.forName(typeName, true, Thread.currentThread().getContextClassLoader());
            } catch (ClassNotFoundException ex) {
                throw new IllegalStateException("Cannot resolve the type name \"" + typeName + "\"", ex);
            }
            return ImmutableType.get(javaType);
        }
    }
}
