package io.quarkiverse.jimmer.runtime.exception;

import java.sql.SQLException;

/**
 * Base type for the runtime exceptions produced by {@link SqlStateExceptionTranslator} when
 * {@code quarkus.jimmer.<datasource>.constraint-violation-translatable=false}.
 *
 * <p>Instead of Jimmer's {@code SaveException}, a raw {@link SQLException} is mapped to a concrete
 * subtype of this exception based on its {@code SQLState}. The original {@link SQLException} is kept
 * as the cause and exposed through {@link #getSqlException()} / {@link #getSqlState()}.
 */
public abstract class JimmerDataAccessException extends RuntimeException {

    private final String sqlState;

    protected JimmerDataAccessException(String message, SQLException cause) {
        super(message, cause);
        this.sqlState = cause.getSQLState();
    }

    /**
     * The {@code SQLState} of the underlying {@link SQLException} (e.g. {@code 23505} for a unique
     * violation), or {@code null} if the driver did not report one.
     */
    public String getSqlState() {
        return sqlState;
    }

    /**
     * The original driver exception that triggered this translation.
     */
    public SQLException getSqlException() {
        return (SQLException) getCause();
    }
}
