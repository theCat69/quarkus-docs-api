package com.fvd.indexs.stores;

import com.fvd.common.validators.InputValidator;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Abstract base class for versioned stores that persist index data in SQLite.
 * Provides common transactional write, exists, read, and delete operations.
 *
 * @param <T> the index type managed by this store
 */
@Slf4j
public abstract class AbstractVersionedStore<T> {

    protected DataSource dataSource;

    protected AbstractVersionedStore(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    protected AbstractVersionedStore() {
        // no-arg constructor for Quarkus ARC proxy creation
    }

    /**
     * Returns the DataSource instance. Subclass proxies created by Quarkus ARC
     * delegate method calls (not field accesses) to the actual bean, so all
     * internal code must go through this getter to avoid NPE on proxies.
     */
    protected DataSource getDataSource() {
        return dataSource;
    }

    public boolean exists(String version) {
        InputValidator.validateVersion(version);

        try (Connection conn = getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(existsQuery())) {
            stmt.setString(1, version);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to check " + indexName() + " existence", e);
        }
    }

    public Optional<T> read(String version) {
        InputValidator.validateVersion(version);

        try (Connection conn = getDataSource().getConnection()) {
            return doRead(conn, version);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to read " + indexName() + " for version: " + version, e);
        }
    }

    public void write(String version, T index) {
        InputValidator.validateVersion(version);

        try (Connection conn = getDataSource().getConnection()) {
            conn.setAutoCommit(false);
            try {
                doDelete(conn, version);
                doInsert(conn, version, index);
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to write " + indexName(), e);
        }
    }

    public void deleteVersion(String version) {
        InputValidator.validateVersion(version);

        try (Connection conn = getDataSource().getConnection()) {
            doDelete(conn, version);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete " + indexName() + " for version: " + version, e);
        }
    }

    protected abstract String indexName();

    protected abstract String existsQuery();

    protected abstract Optional<T> doRead(Connection conn, String version) throws SQLException;

    protected abstract void doDelete(Connection conn, String version) throws SQLException;

    protected abstract void doInsert(Connection conn, String version, T index) throws SQLException;
}
