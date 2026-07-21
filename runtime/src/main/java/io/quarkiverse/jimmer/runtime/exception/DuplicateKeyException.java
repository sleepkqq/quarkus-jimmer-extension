package io.quarkiverse.jimmer.runtime.exception;

import java.sql.SQLException;

/**
 * Primary key / unique constraint / unique index violation (SQLState {@code 23505}).
 */
public class DuplicateKeyException extends JimmerDataAccessException {

    public DuplicateKeyException(SQLException cause) {
        super("Unique or primary key violation: " + cause.getMessage(), cause);
    }
}
