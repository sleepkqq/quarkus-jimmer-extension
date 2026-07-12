package io.quarkiverse.jimmer.runtime.cache;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.babyfish.jimmer.meta.ImmutableProp;
import org.babyfish.jimmer.meta.ImmutableType;
import org.babyfish.jimmer.sql.cache.CacheTracker;
import org.babyfish.jimmer.sql.cache.spi.AbstractCacheTracker;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.api.listener.BaseStatusListener;
import org.redisson.codec.TypedJsonJacksonCodec;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;

/**
 * Redisson-backed {@link CacheTracker} equivalent to Jimmer's own {@code RedissonCacheTracker}, but
 * usable in a GraalVM native image: the pub/sub topic gets an explicit {@link TypedJsonJacksonCodec}
 * instead of the client's default codec. Redisson's default (Kryo) instantiates message objects via
 * Objenesis, which needs {@code sun.reflect.ReflectionFactory} — absent under GraalVM — while the
 * global {@code JsonJacksonCodec} enables Jackson default typing, which Jimmer's hand-written
 * message serializer does not support ("Type id handling not implemented"). A per-topic typed codec
 * avoids both without touching the client-wide codec.
 *
 * <p>The channel is intentionally distinct from Jimmer's {@code _jimmer_:invalidate}: the wire
 * format differs (plain JSON of {@link InvalidationMessage} vs Jimmer's package-private message),
 * and instances of one application must all run the same extension version anyway.</p>
 */
public class QuarkusRedissonCacheTracker extends AbstractCacheTracker {

    private static final String CHANNEL = "_quarkus_jimmer_:invalidate";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final UUID trackerId = UUID.randomUUID();

    private final RTopic topic;

    public QuarkusRedissonCacheTracker(RedissonClient redissonClient) {
        topic = redissonClient.getTopic(CHANNEL, new TypedJsonJacksonCodec(InvalidationMessage.class));
        topic.addListener(InvalidationMessage.class, (channel, msg) -> {
            if (!trackerId.equals(msg.trackerId)) {
                firer().invalidate(msg.toEvent());
            }
        });
        topic.addListener(new BaseStatusListener() {
            @Override
            public void onSubscribe(String channel) {
                firer().reconnect();
            }
        });
    }

    @Override
    protected void publishInvalidationEvent(CacheTracker.InvalidateEvent event) {
        topic.publish(new InvalidationMessage(trackerId, event));
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
