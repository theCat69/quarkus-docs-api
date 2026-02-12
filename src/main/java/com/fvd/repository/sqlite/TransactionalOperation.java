package com.fvd.repository.sqlite;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Functional interface for database operations requiring a transaction.
 *
 * @param <T> the return type of the operation (use Void for no return)
 */
@FunctionalInterface
public interface TransactionalOperation<T> {
    T execute(Connection conn) throws SQLException;
}
