package io.quarkiverse.jimmer.runtime.exception;

import java.sql.SQLException;

import org.babyfish.jimmer.sql.runtime.ExceptionTranslator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Built-in translator that maps a raw {@link SQLException} to a {@link JimmerDataAccessException}
 * subtype by its {@code SQLState}.
 *
 * <p>Registered by default when
 * {@code quarkus.jimmer.<datasource>.sql-state-exception-translator=true} (the default). It only
 * fires when Jimmer does not translate the failure itself, i.e. when
 * {@code constraint-violation-translatable=false} — otherwise constraint violations become
 * {@code SaveException} before ever reaching an {@code ExceptionTranslator<SQLException>}.
 *
 * <p>SQLState is the SQL-standard, DB-agnostic error code. The class {@code 23} (integrity
 * constraint violation) is standardized; finer states below are PostgreSQL/H2 aligned. Databases
 * that only report the generic {@code 23000} (e.g. MySQL) fall through to
 * {@link DataIntegrityViolationException}.
 *
 * <p>Returning {@code null} for an unmapped exception leaves it untouched, so any user-registered
 * {@link ExceptionTranslator} still gets its turn.
 */
public class SqlStateExceptionTranslator implements ExceptionTranslator<SQLException> {

    @Override
    @Nullable
    public Exception translate(@NotNull SQLException exception, @NotNull Args args) {
        String sqlState = exception.getSQLState();
        if (sqlState == null) {
            return null;
        }
        switch (sqlState) {
            case "23505":
                return new DuplicateKeyException(exception);
            case "23503":
                return new ForeignKeyViolationException(exception);
            case "23502":
                return new NotNullViolationException(exception);
            case "23514":
                return new CheckViolationException(exception);
            case "40001":
                return new SerializationFailureException(exception);
            case "40P01":
                return new DeadlockException(exception);
            default:
                // Class 23 == integrity constraint violation, but the vendor collapsed the detail.
                return sqlState.startsWith("23") ? new DataIntegrityViolationException(exception) : null;
        }
    }
}
