package com.fvd.repository.sqlite;

import com.fvd.repository.exceptions.RepositoryException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TransactionTemplateTest {

    private DataSource dataSource;
    private Connection connection;

    @BeforeEach
    void setUp() throws SQLException {
        dataSource = mock(DataSource.class);
        connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
    }

    @Test
    void executeInTransactionCommitsOnSuccess() throws SQLException {
        String result = TransactionTemplate.executeInTransaction(dataSource, conn -> {
            return "success";
        }, "should not fail");

        assertThat(result).isEqualTo("success");

        var order = inOrder(connection);
        order.verify(connection).setAutoCommit(false);
        order.verify(connection).commit();
        order.verify(connection).setAutoCommit(true);
        verify(connection, never()).rollback();
    }

    @Test
    void executeInTransactionRollsBackOnSqlException() throws SQLException {
        assertThatThrownBy(() -> TransactionTemplate.executeInTransaction(dataSource, conn -> {
            throw new SQLException("db error");
        }, "Operation failed"))
                .isInstanceOf(RepositoryException.class)
                .hasMessage("Operation failed")
                .hasCauseInstanceOf(SQLException.class);

        var order = inOrder(connection);
        order.verify(connection).setAutoCommit(false);
        order.verify(connection).rollback();
        order.verify(connection).setAutoCommit(true);
        verify(connection, never()).commit();
    }

    @Test
    void executeInTransactionWrapsExceptionInRepositoryException() {
        assertThatThrownBy(() -> TransactionTemplate.executeInTransaction(dataSource, conn -> {
            throw new SQLException("underlying cause");
        }, "Custom error message"))
                .isInstanceOf(RepositoryException.class)
                .hasMessage("Custom error message")
                .hasCauseInstanceOf(SQLException.class)
                .cause().hasMessage("underlying cause");
    }

    @Test
    void executeInTransactionThrowsRepositoryExceptionOnConnectionFailure() throws SQLException {
        when(dataSource.getConnection()).thenThrow(new SQLException("connection failed"));

        assertThatThrownBy(() -> TransactionTemplate.executeInTransaction(dataSource, conn -> {
            return "value";
        }, "Connection error"))
                .isInstanceOf(RepositoryException.class)
                .hasMessage("Connection error")
                .hasCauseInstanceOf(SQLException.class);
    }

    @Test
    void executeInTransactionReturnsNullForVoidOperation() throws SQLException {
        Void result = TransactionTemplate.executeInTransaction(dataSource, conn -> {
            return null;
        }, "should not fail");

        assertThat(result).isNull();
        verify(connection).commit();
    }

    @Test
    void executeInTransactionVoidCommitsOnSuccess() throws SQLException {
        TransactionTemplate.executeInTransactionVoid(dataSource, conn -> {
            // no-op
        }, "should not fail");

        var order = inOrder(connection);
        order.verify(connection).setAutoCommit(false);
        order.verify(connection).commit();
        order.verify(connection).setAutoCommit(true);
        verify(connection, never()).rollback();
    }

    @Test
    void executeInTransactionVoidRollsBackOnSqlException() throws SQLException {
        assertThatThrownBy(() -> TransactionTemplate.executeInTransactionVoid(dataSource, conn -> {
            throw new SQLException("void db error");
        }, "Void operation failed"))
                .isInstanceOf(RepositoryException.class)
                .hasMessage("Void operation failed")
                .hasCauseInstanceOf(SQLException.class);

        verify(connection).rollback();
        verify(connection, never()).commit();
    }

    @Test
    void executeInTransactionRestoresAutoCommitAfterRollback() throws SQLException {
        assertThatThrownBy(() -> TransactionTemplate.executeInTransaction(dataSource, conn -> {
            throw new SQLException("error");
        }, "error"));

        var order = inOrder(connection);
        order.verify(connection).setAutoCommit(false);
        order.verify(connection).rollback();
        order.verify(connection).setAutoCommit(true);
    }

    @Test
    void executeInTransactionRollbackFailureStillThrowsRepositoryException() throws SQLException {
        doThrow(new SQLException("rollback failed")).when(connection).rollback();

        assertThatThrownBy(() -> TransactionTemplate.executeInTransaction(dataSource, conn -> {
            throw new SQLException("original error");
        }, "Operation failed"))
                .isInstanceOf(RepositoryException.class)
                .hasMessage("Operation failed");
    }

    @Test
    void shouldPreserveOriginalCauseWhenRollbackFails() throws SQLException {
        SQLException originalEx = new SQLException("operation failed");
        SQLException rollbackEx = new SQLException("rollback failed");

        doThrow(rollbackEx).when(connection).rollback();

        assertThatThrownBy(() -> TransactionTemplate.executeInTransaction(dataSource, conn -> {
            throw originalEx;
        }, "Operation failed"))
                .isInstanceOf(RepositoryException.class)
                .hasCauseReference(originalEx)
                .satisfies(ex -> assertThat(ex.getCause().getSuppressed()).contains(rollbackEx));
    }
}
