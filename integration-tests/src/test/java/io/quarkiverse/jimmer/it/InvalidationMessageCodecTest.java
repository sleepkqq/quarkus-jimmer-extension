package io.quarkiverse.jimmer.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.UUID;

import org.babyfish.jimmer.sql.cache.CacheTracker;
import org.junit.jupiter.api.Test;
import org.redisson.codec.TypedJsonJacksonCodec;

import io.netty.buffer.ByteBuf;
import io.quarkiverse.jimmer.it.entity.Book;
import io.quarkiverse.jimmer.runtime.cache.QuarkusRedissonCacheTracker;

/**
 * Round-trips the tracker's pub/sub message through the exact codec the topic uses. Guards the
 * two failure classes seen with client-default codecs: Kryo's Objenesis instantiation (breaks
 * in native images) and JsonJacksonCodec's default typing (unsupported by custom serializers).
 * No Redis needed — encode/decode is where both failed.
 */
class InvalidationMessageCodecTest {

    private final TypedJsonJacksonCodec codec =
            new TypedJsonJacksonCodec(QuarkusRedissonCacheTracker.InvalidationMessage.class);

    @Test
    void roundTripsObjectInvalidationWithTypedIds() throws Exception {
        UUID trackerId = UUID.randomUUID();
        QuarkusRedissonCacheTracker.InvalidationMessage message =
                new QuarkusRedissonCacheTracker.InvalidationMessage();
        message.trackerId = trackerId;
        message.typeName = Book.class.getName();
        message.propName = null;
        message.ids = List.of(1L, 2L, 3L);

        QuarkusRedissonCacheTracker.InvalidationMessage decoded = roundTrip(message);

        assertEquals(trackerId, decoded.trackerId);
        assertEquals(Book.class.getName(), decoded.typeName);
        assertNull(decoded.propName);

        CacheTracker.InvalidateEvent event = toEvent(decoded);
        // JSON narrows longs to ints — toEvent must rebuild ids as the entity id type
        assertEquals(List.of(1L, 2L, 3L), List.copyOf(event.getIds()));
    }

    @Test
    void roundTripsAssociationInvalidation() throws Exception {
        QuarkusRedissonCacheTracker.InvalidationMessage message =
                new QuarkusRedissonCacheTracker.InvalidationMessage();
        message.trackerId = UUID.randomUUID();
        message.typeName = Book.class.getName();
        message.propName = "authors";
        message.ids = List.of(42L);

        CacheTracker.InvalidateEvent event = toEvent(roundTrip(message));

        assertEquals("authors", event.getProp().getName());
        assertEquals(List.of(42L), List.copyOf(event.getIds()));
    }

    private QuarkusRedissonCacheTracker.InvalidationMessage roundTrip(
            QuarkusRedissonCacheTracker.InvalidationMessage message) throws Exception {
        ByteBuf buf = codec.getValueEncoder().encode(message);
        try {
            return (QuarkusRedissonCacheTracker.InvalidationMessage) codec.getValueDecoder().decode(buf, null);
        } finally {
            buf.release();
        }
    }

    private CacheTracker.InvalidateEvent toEvent(
            QuarkusRedissonCacheTracker.InvalidationMessage message) throws Exception {
        var method = message.getClass().getDeclaredMethod("toEvent");
        method.setAccessible(true);
        return (CacheTracker.InvalidateEvent) method.invoke(message);
    }
}
