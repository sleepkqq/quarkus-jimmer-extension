package io.quarkiverse.jimmer.runtime.cache;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import org.babyfish.jimmer.meta.ImmutableProp;
import org.babyfish.jimmer.meta.ImmutableType;
import org.babyfish.jimmer.meta.TargetLevel;
import org.babyfish.jimmer.runtime.ImmutableSpi;
import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.event.EntityEvent;
import org.babyfish.jimmer.sql.event.Triggers;
import org.babyfish.jimmer.sql.kt.KSqlClient;
import org.babyfish.jimmer.sql.meta.MetadataStrategy;
import org.babyfish.jimmer.sql.meta.SingleColumn;
import org.babyfish.jimmer.sql.runtime.JSqlClientImplementor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkiverse.jimmer.runtime.cfg.JimmerCacheConfig;
import io.quarkus.runtime.StartupEvent;

/**
 * Forces eviction of every inverse association cache reachable from a real-FK reference prop, instead of relying
 * on Jimmer's diff-based auto eviction ({@link EntityEvent#getChangedRef}). That diff misses one side when an
 * entity has two {@code @ManyToOne} real-FK columns pointing at two different cached parents (e.g. a join row
 * with composite key) — only one inverse {@code @OneToMany(mappedBy=...)} collection ends up loaded on the
 * post-save entity snapshot Jimmer hands to triggers, so only that side gets evicted; the other serves stale
 * data forever.
 * <p>
 * Armed only for entity types configured with {@code force-association-evict: true} — this duplicates eviction
 * work Jimmer already does correctly for everyone else, so it's opt-in per entity rather than blanket-applied to
 * every {@code cache-associations: true} entity. When a real-FK prop isn't loaded on the snapshot, the current
 * FK value is re-read with a single-column SELECT on the same connection/transaction instead of being skipped.
 */
@ApplicationScoped
public class AssociationEvictionGuard {

    private static final Logger LOGGER = LoggerFactory.getLogger(AssociationEvictionGuard.class);

    private final JSqlClient sqlClient;
    private final Set<String> guardedTypes;

    @Inject
    public AssociationEvictionGuard(
            Instance<JSqlClient> jSqlClients,
            Instance<KSqlClient> kSqlClients,
            JimmerCacheConfig config) {
        this.sqlClient = jSqlClients.isResolvable() ? jSqlClients.get() : kSqlClients.get().getJavaClient();
        this.guardedTypes = new LinkedHashSet<>();
        for (JimmerCacheConfig.EntityCacheConfig entity : config.entities()) {
            if (entity.forceAssociationEvict()) {
                guardedTypes.add(entity.type());
            }
        }
    }

    void onStart(@Observes StartupEvent event) {
        if (guardedTypes.isEmpty()) {
            return;
        }
        sqlClient.getTriggers(true).addEntityListener(this::onEntityChanged);
        LOGGER.info("Association eviction guard armed for entities {}", guardedTypes);
    }

    private void onEntityChanged(EntityEvent<?> event) {
        if (event.isEvict() || !guardedTypes.contains(event.getImmutableType().getJavaClass().getSimpleName())) {
            return;
        }
        evictOpposites(event, event.getOldEntity());
        evictOpposites(event, event.getNewEntity());
    }

    private void evictOpposites(EntityEvent<?> event, Object entity) {
        if (entity == null) {
            return;
        }
        ImmutableSpi spi = (ImmutableSpi) entity;
        Triggers triggers = sqlClient.getTriggers(true);
        MetadataStrategy strategy = ((JSqlClientImplementor) sqlClient).getMetadataStrategy();
        for (ImmutableProp prop : event.getImmutableType().getProps().values()) {
            if (!prop.isReference(TargetLevel.PERSISTENT) || !prop.isColumnDefinition()) {
                continue;
            }
            ImmutableProp opposite = prop.getOpposite();
            if (opposite == null) {
                continue;
            }
            Object targetId = spi.__isLoaded(prop.getId())
                    ? targetIdOf(spi.__get(prop.getId()), prop)
                    : fetchForeignKey(event, prop, strategy);
            if (targetId == null) {
                continue;
            }
            triggers.fireAssociationEvict(opposite, targetId, event.getConnection(), event.getReason());
        }
    }

    private Object targetIdOf(Object ref, ImmutableProp prop) {
        if (ref == null) {
            return null;
        }
        return ((ImmutableSpi) ref).__get(prop.getTargetType().getIdProp().getId());
    }

    /**
     * Fallback for when the FK prop isn't loaded on the entity snapshot Jimmer fired the trigger with — reads the
     * current column value directly, on the same connection/transaction, so it sees the just-written row.
     */
    private Object fetchForeignKey(EntityEvent<?> event, ImmutableProp prop, MetadataStrategy strategy) {
        Connection con = event.getConnection();
        if (con == null) {
            return null;
        }
        if (!(prop.getStorage(strategy) instanceof SingleColumn)
                || !(event.getImmutableType().getIdProp().getStorage(strategy) instanceof SingleColumn)) {
            return null;
        }
        ImmutableType type = event.getImmutableType();
        String fkColumn = ((SingleColumn) prop.getStorage(strategy)).getName();
        String idColumn = ((SingleColumn) type.getIdProp().getStorage(strategy)).getName();
        String sql = "select " + fkColumn + " from " + type.getTableName(strategy) + " where " + idColumn + " = ?";
        try (PreparedStatement stmt = con.prepareStatement(sql)) {
            stmt.setObject(1, event.getId());
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getObject(1) : null;
            }
        } catch (SQLException ex) {
            LOGGER.warn("Association eviction guard could not resolve FK \"{}\" for id {}", prop, event.getId(), ex);
            return null;
        }
    }
}
