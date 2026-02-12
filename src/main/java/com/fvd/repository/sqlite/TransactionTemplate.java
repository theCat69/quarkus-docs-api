package com.fvd.repository.sqlite;

import com.fvd.repository.exceptions.RepositoryException;
import lombok.experimental.UtilityClass;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Template for executing database operations within a transaction.
 * Handles connection management, commit, and rollback automatically.
 */
@UtilityClass
public class TransactionTemplate {

    /**
     * Executes an operation within a transaction with automatic rollback on failure.
     */
    public static <T> T executeInTransaction(
            DataSource dataSource,
            TransactionalOperation<T> operation,
            String errorMessage) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                T result = operation.execute(conn);
                conn.commit();
                return result;
            } catch (SQLException e) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    e.addSuppressed(rollbackEx);
                }
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RepositoryException(errorMessage, e);
        }
    }

    /**
     * Executes a void operation within a transaction.
     */
    public static void executeInTransactionVoid(
            DataSource dataSource,
            VoidTransactionalOperation operation,
            String errorMessage) {
        executeInTransaction(dataSource, conn -> {
            operation.execute(conn);
            return null;
        }, errorMessage);
    }
}
