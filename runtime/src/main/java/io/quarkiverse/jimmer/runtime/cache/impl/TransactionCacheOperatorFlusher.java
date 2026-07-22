package io.quarkiverse.jimmer.runtime.cache.impl;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;

import org.babyfish.jimmer.sql.cache.TransactionCacheOperator;
import org.babyfish.jimmer.sql.event.DatabaseEvent;
import org.babyfish.jimmer.sql.kt.KSqlClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkiverse.jimmer.runtime.SqlClientInitializationAware;
import io.quarkiverse.jimmer.runtime.java.QuarkusJSqlClientContainer;
import io.quarkiverse.jimmer.runtime.kotlin.QuarkusKSqlClientContainer;
import io.quarkiverse.jimmer.runtime.util.QuarkusSqlClientContainerUtil;
import io.quarkus.agroal.DataSource;
import io.quarkus.arc.All;
import io.quarkus.arc.Arc;
import io.quarkus.arc.InstanceHandle;
import io.quarkus.datasource.common.runtime.DataSourceUtil;
import io.quarkus.scheduler.Scheduled;

@ApplicationScoped
public class TransactionCacheOperatorFlusher {

    private static final Logger LOGGER = LoggerFactory.getLogger(TransactionCacheOperatorFlusher.class);

    private final List<InstanceHandle<TransactionCacheOperator>> operatorHandles;

    private final ThreadLocal<Boolean> dirtyLocal = new ThreadLocal<>();

    public TransactionCacheOperatorFlusher(@All List<InstanceHandle<TransactionCacheOperator>> operatorHandles) {
        if (operatorHandles.isEmpty()) {
            throw new IllegalArgumentException("`operators` cannot be empty");
        }
        this.operatorHandles = operatorHandles;
    }

    public void beforeCommit(@Observes(during = TransactionPhase.IN_PROGRESS) DatabaseEvent e) {
        dirtyLocal.set(Boolean.TRUE);
    }

    public void afterCommit(@Observes(during = TransactionPhase.AFTER_COMPLETION) DatabaseEvent e) {
        if (dirtyLocal.get() != null) {
            dirtyLocal.remove();
            flush(resolveOperators(false));
        }
    }

    /**
     * The retry tick must never be the first user of a lazily initialized SqlClient: building the
     * Jimmer metadata graph from the scheduler thread while application startup is still creating
     * repository beans can stall on the static lock guarding lazy target-type resolution
     * ({@code ImmutablePropImpl.META_LOCK}). Operators whose SqlClient is not initialized yet have
     * nothing produced by this application instance to flush; their pending rows (e.g. left over by a previous crashed
     * instance) are picked up by the first tick after the SqlClient is initialized.
     */
    @Scheduled(every = "${quarkus.jimmer.transaction-cache-operator-fixed-delay}", identity = "jimmer.transaction-cache-operator-job")
    public void retry() {
        flush(resolveOperators(true));
    }

    private List<TransactionCacheOperator> resolveOperators(boolean onlyInitializedSqlClients) {
        List<TransactionCacheOperator> operators = new ArrayList<>(operatorHandles.size());
        for (InstanceHandle<TransactionCacheOperator> operatorHandle : operatorHandles) {
            if (onlyInitializedSqlClients && !isSqlClientInitialized(dataSourceNameOf(operatorHandle))) {
                continue;
            }
            operators.add(operatorHandle.get());
        }
        return operators;
    }

    private static String dataSourceNameOf(InstanceHandle<TransactionCacheOperator> operatorHandle) {
        for (Annotation qualifier : operatorHandle.getBean().getQualifiers()) {
            if (qualifier instanceof DataSource) {
                return ((DataSource) qualifier).value();
            }
        }
        return DataSourceUtil.DEFAULT_DATASOURCE_NAME;
    }

    private static boolean isSqlClientInitialized(String dataSourceName) {
        Annotation qualifier = QuarkusSqlClientContainerUtil.getQuarkusSqlClientContainerQualifier(dataSourceName);
        InstanceHandle<QuarkusJSqlClientContainer> jContainer = Arc.container()
                .instance(QuarkusJSqlClientContainer.class, qualifier);
        if (jContainer.isAvailable()) {
            return isInitialized(jContainer.get().getjSqlClient());
        }
        InstanceHandle<QuarkusKSqlClientContainer> kContainer = Arc.container()
                .instance(QuarkusKSqlClientContainer.class, qualifier);
        if (kContainer.isAvailable()) {
            KSqlClient kSqlClient = kContainer.get().getKSqlClient();
            return kSqlClient == null || isInitialized(kSqlClient.getJavaClient());
        }
        return true;
    }

    private static boolean isInitialized(Object sqlClient) {
        return !(sqlClient instanceof SqlClientInitializationAware)
                || ((SqlClientInitializationAware) sqlClient).isSqlClientInitialized();
    }

    private void flush(List<TransactionCacheOperator> operators) {
        if (operators.isEmpty()) {
            return;
        }
        if (operators.size() == 1) {
            TransactionCacheOperator operator = operators.get(0);
            operator.flush();
        } else {
            Throwable throwable = null;
            for (TransactionCacheOperator operator : operators) {
                try {
                    operator.flush();
                } catch (RuntimeException | Error ex) {
                    if (throwable == null) {
                        throwable = ex;
                    }
                }
            }
            if (throwable instanceof RuntimeException) {
                throw (RuntimeException) throwable;
            }
            if (throwable != null) {
                throw (Error) throwable;
            }
        }
    }
}
