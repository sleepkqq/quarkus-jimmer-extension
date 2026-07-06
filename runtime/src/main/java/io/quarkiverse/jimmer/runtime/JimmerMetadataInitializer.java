package io.quarkiverse.jimmer.runtime;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.interceptor.Interceptor;

import org.babyfish.jimmer.meta.ImmutableProp;
import org.babyfish.jimmer.meta.ImmutableType;
import org.babyfish.jimmer.meta.TargetLevel;
import org.babyfish.jimmer.sql.kt.KSqlClient;
import org.babyfish.jimmer.sql.runtime.EntityManager;
import org.babyfish.jimmer.sql.runtime.JSqlClientImplementor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkiverse.jimmer.runtime.cfg.JimmerRuntimeConfig;
import io.quarkiverse.jimmer.runtime.java.QuarkusJSqlClientContainer;
import io.quarkiverse.jimmer.runtime.java.UnConfiguredDataSourceQuarkusJSqlClientContainer;
import io.quarkiverse.jimmer.runtime.kotlin.QuarkusKSqlClientContainer;
import io.quarkiverse.jimmer.runtime.kotlin.UnConfiguredDataSourceQuarkusKSqlClientContainer;
import io.quarkus.arc.Arc;
import io.quarkus.arc.InstanceHandle;
import io.quarkus.runtime.StartupEvent;

/**
 * Eagerly initializes every configured SqlClient on the main thread during startup.
 * <p>
 * The observer priority is below {@code Interceptor.Priority.PLATFORM_BEFORE} on purpose: the
 * Quarkus scheduler starts its own StartupEvent observer at {@code PLATFORM_BEFORE} and interval
 * triggers fire their first execution immediately, so the metadata graph must already be built
 * when the first scheduled job runs. Otherwise a scheduled job racing repository class-init on
 * another thread can deadlock Jimmer metadata initialization (StaticCache read-write lock vs
 * ImmutablePropImpl target-type lock).
 * <p>
 * Building the EntityManager alone leaves association links (mappedBy/opposite/storage) to be
 * resolved lazily by the first request that touches them — e.g. reshape in findByIds resolving
 * every selectable reference prop. In native images that lazy path has produced a corrupted
 * half-resolved link ("Both `A.b` and `A.b` use `mappedBy` to reference `C.d`") under concurrent
 * traffic, so the whole association graph is force-resolved here, single-threaded, before the
 * application starts serving requests.
 */
@ApplicationScoped
public class JimmerMetadataInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger(JimmerMetadataInitializer.class);

    private final JimmerRuntimeConfig runtimeConfig;

    public JimmerMetadataInitializer(JimmerRuntimeConfig runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
    }

    void onStart(@Observes @Priority(Interceptor.Priority.PLATFORM_BEFORE - 100) StartupEvent event) {
        if (!runtimeConfig.eagerMetadataInitialization()) {
            return;
        }
        for (InstanceHandle<QuarkusJSqlClientContainer> handle : Arc.container()
                .listAll(QuarkusJSqlClientContainer.class)) {
            QuarkusJSqlClientContainer container = handle.get();
            if (container instanceof UnConfiguredDataSourceQuarkusJSqlClientContainer) {
                continue;
            }
            warmUp((JSqlClientImplementor) container.getjSqlClient());
        }
        for (InstanceHandle<QuarkusKSqlClientContainer> handle : Arc.container()
                .listAll(QuarkusKSqlClientContainer.class)) {
            QuarkusKSqlClientContainer container = handle.get();
            if (container instanceof UnConfiguredDataSourceQuarkusKSqlClientContainer) {
                continue;
            }
            KSqlClient kSqlClient = container.getKSqlClient();
            if (kSqlClient != null) {
                warmUp(kSqlClient.getJavaClient());
            }
        }
    }

    private void warmUp(JSqlClientImplementor sqlClient) {
        EntityManager entityManager = sqlClient.getEntityManager();
        for (ImmutableType type : entityManager.getAllTypes(sqlClient.getMicroServiceName())) {
            for (ImmutableProp prop : type.getProps().values()) {
                if (!prop.isAssociation(TargetLevel.ENTITY)) {
                    continue;
                }
                try {
                    prop.getTargetType();
                    prop.getMappedBy();
                    prop.getOpposite();
                } catch (RuntimeException ex) {
                    LOGGER.warn("Failed to eagerly resolve association \"{}\"", prop, ex);
                }
            }
            try {
                type.getSelectableReferenceProps();
                type.getSelectableScalarProps();
            } catch (RuntimeException ex) {
                LOGGER.warn("Failed to eagerly resolve selectable props of \"{}\"", type, ex);
            }
        }
    }
}
