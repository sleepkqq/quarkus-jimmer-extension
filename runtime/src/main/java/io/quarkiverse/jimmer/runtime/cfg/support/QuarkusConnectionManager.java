package io.quarkiverse.jimmer.runtime.cfg.support;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Function;

import javax.sql.DataSource;

import org.babyfish.jimmer.sql.transaction.Propagation;
import org.babyfish.jimmer.sql.transaction.TxConnectionManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.narayana.jta.TransactionRunnerOptions;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.SystemException;
import jakarta.transaction.Transaction;
import jakarta.transaction.TransactionManager;
import jakarta.transaction.TransactionSynchronizationRegistry;

public class QuarkusConnectionManager implements DataSourceAwareConnectionManager, TxConnectionManager {

    private static final ThreadLocal<Connection> TX_CONNECTION = new ThreadLocal<>();

    private final DataSource dataSource;
    private final TransactionManager transactionManager;
    private final TransactionSynchronizationRegistry tsr;

    public QuarkusConnectionManager(DataSource dataSource,
            TransactionManager transactionManager,
            TransactionSynchronizationRegistry tsr) {
        this.dataSource = dataSource;
        this.transactionManager = transactionManager;
        this.tsr = tsr;
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

        Connection txConn = TX_CONNECTION.get();
        if (txConn != null) {
            return block.apply(txConn);
        }

        if (isTransactionActive()) {
            Connection conn;
            try {
                conn = dataSource.getConnection();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            TX_CONNECTION.set(conn);
            try {
                tsr.registerInterposedSynchronization(new Synchronization() {
                    @Override
                    public void beforeCompletion() {
                    }

                    @Override
                    public void afterCompletion(int status) {
                        TX_CONNECTION.remove();
                    }
                });
            } catch (Exception e) {
                TX_CONNECTION.remove();
                try {
                    conn.close();
                } catch (SQLException ignored) {
                }
                throw new RuntimeException(e);
            }
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
