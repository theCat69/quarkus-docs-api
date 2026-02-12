package com.fvd.repository.sqlite;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Functional interface for void database operations requiring a transaction.
 */
@FunctionalInterface
public interface VoidTransactionalOperation {
    void execute(Connection conn) throws SQLException;
}
