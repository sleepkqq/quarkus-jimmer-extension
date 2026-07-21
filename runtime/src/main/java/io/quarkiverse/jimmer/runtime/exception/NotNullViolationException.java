package io.quarkiverse.jimmer.runtime.exception;

import java.sql.SQLException;

/**
 * NOT NULL constraint violation (SQLState {@code 23502}).
 */
public class NotNullViolationException extends JimmerDataAccessException {

    public NotNullViolationException(SQLException cause) {
        super("Not-null violation: " + cause.getMessage(), cause);
    }
}
