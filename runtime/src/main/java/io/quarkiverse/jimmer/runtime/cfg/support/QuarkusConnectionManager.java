package io.quarkiverse.jimmer.runtime.cfg.support;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.function.Function;

import javax.sql.DataSource;

import org.babyfish.jimmer.sql.transaction.Propagation;
import org.babyfish.jimmer.sql.transaction.TxConnectionManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.narayana.jta.TransactionRunnerOptions;

public class QuarkusConnectionManager implements DataSourceAwareConnectionManager, TxConnectionManager {

    private final DataSource dataSource;

    private final String defaultSchema;

    public QuarkusConnectionManager(DataSource dataSource) {
        this(dataSource, null);
    }

    public QuarkusConnectionManager(DataSource dataSource, String defaultSchema) {
        this.dataSource = dataSource;
        this.defaultSchema = defaultSchema;
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
            return block.apply(SchemaAwareConnectionWrapper.wrap(con, defaultSchema));
        }
        try (Connection newConnection = dataSource.getConnection()) {
            applyDefaultSchema(newConnection);
            return block.apply(SchemaAwareConnectionWrapper.wrap(newConnection, defaultSchema));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public <R> R executeTransaction(Propagation propagation, Function<Connection, R> block) {
        TransactionRunnerOptions transactionRunnerOptions = behavior(propagation);
        return transactionRunnerOptions.call(() -> execute(block));
    }

    private void applyDefaultSchema(Connection connection) throws SQLException {
        if (defaultSchema != null && !defaultSchema.isEmpty()) {
            connection.setSchema(defaultSchema);
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
                // REQUIRED:
                return QuarkusTransaction.joiningExisting();
        }
    }
}
