package io.quarkiverse.jimmer.runtime.cfg.support;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Function;

import javax.sql.DataSource;

import org.babyfish.jimmer.sql.transaction.Propagation;
import org.babyfish.jimmer.sql.transaction.TxConnectionManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import io.quarkus.arc.Arc;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.narayana.jta.TransactionRunnerOptions;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.SystemException;
import jakarta.transaction.Transaction;
import jakarta.transaction.TransactionManager;
import jakarta.transaction.TransactionSynchronizationRegistry;

public class QuarkusConnectionManager implements DataSourceAwareConnectionManager, TxConnectionManager {

    private final Object connectionKey = new Object();

    private final DataSource dataSource;
    private final TransactionManager transactionManager;
    private final TransactionSynchronizationRegistry tsr;

    public QuarkusConnectionManager(DataSource dataSource) {
        this.dataSource = dataSource;
        this.transactionManager = Arc.container().instance(TransactionManager.class).get();
        this.tsr = Arc.container().instance(TransactionSynchronizationRegistry.class).get();
    }

    @NotNull
    @Override
    public DataSource getDataSource() {
        return dataSource;
    }

    @Override
    public final <R> R execute(Function<Connection, R> block) {
        return execute(null, block);
    }

    @Override
    public final <R> R execute(@Nullable Connection con, Function<Connection, R> block) {
        if (null != con) {
            return block.apply(con);
        }

        if (isTransactionActive()) {
            Connection txConn = (Connection) tsr.getResource(connectionKey);
            if (txConn != null) {
                return block.apply(txConn);
            }
            Connection conn;
            try {
                conn = dataSource.getConnection();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            tsr.putResource(connectionKey, conn);
            tsr.registerInterposedSynchronization(new Synchronization() {
                @Override
                public void beforeCompletion() {
                    try {
                        conn.close();
                    } catch (SQLException ignored) {
                    }
                }

                @Override
                public void afterCompletion(int status) {
                }
            });
            return block.apply(conn);
        }

        try (Connection newConnection = dataSource.getConnection()) {
            return block.apply(newConnection);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public <R> R executeTransaction(Propagation propagation, Function<Connection, R> block) {
        TransactionRunnerOptions transactionRunnerOptions = behavior(propagation);
        return transactionRunnerOptions.call(() -> execute(block));
    }

    private boolean isTransactionActive() {
        try {
            Transaction tx = transactionManager.getTransaction();
            return tx != null && tx.getStatus() == Status.STATUS_ACTIVE;
        } catch (SystemException e) {
            return false;
        }
    }

    private TransactionRunnerOptions behavior(Propagation propagation) {
        switch (propagation) {
            case REQUIRES_NEW:
                return QuarkusTransaction.requiringNew();
            case SUPPORTS:
                throw new UnsupportedOperationException("Quarkus does not support SUPPORTS");
            case NOT_SUPPORTED:
                return QuarkusTransaction.suspendingExisting();
            case MANDATORY:
                throw new UnsupportedOperationException("Quarkus does not support MANDATORY");
            case NEVER:
                throw new UnsupportedOperationException("Quarkus does not support NEVER");
            default:
                return QuarkusTransaction.joiningExisting();
        }
    }
}
