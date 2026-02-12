package com.fvd.repository.sqlite;

import com.fvd.common.validators.InputValidator;
import com.fvd.repository.api.GithubIndexRepository;
import com.fvd.repository.domain.GithubFileEntry;
import com.fvd.repository.exceptions.RepositoryException;
import io.quarkus.arc.lookup.LookupIfProperty;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * SQLite implementation of {@link GithubIndexRepository}.
 * <p>
 * Stores GitHub file index entries in SQLite, mapping documentation file
 * paths to their Git SHA hashes for version tracking.
 * </p>
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
@LookupIfProperty(name = "app.database.type", stringValue = "sqlite", lookupIfMissing = true)
public class SqliteGithubIndexRepository implements GithubIndexRepository {

    private final DataSource dataSource;

    @Override
    public boolean exists(String version) {
        InputValidator.validateVersion(version);

        try (Connection conn = dataSource.getConnection()) {
            return SqlUtils.existsByVersion(conn, "github_index", "version", version);
        } catch (SQLException e) {
            throw new RepositoryException("Failed to check GitHub index existence for version: " + version, e);
        }
    }

    @Override
    public Optional<List<GithubFileEntry>> findByVersion(String version) {
        InputValidator.validateVersion(version);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT name, path, sha FROM github_index WHERE version = ?")) {
            stmt.setString(1, version);
            try (ResultSet rs = stmt.executeQuery()) {
                List<GithubFileEntry> entries = new ArrayList<>();
                while (rs.next()) {
                    entries.add(new GithubFileEntry(
                            rs.getString("name"),
                            rs.getString("path"),
                            rs.getString("sha")));
                }
                if (entries.isEmpty()) {
                    return Optional.empty();
                }
                return Optional.of(List.copyOf(entries));
            }
        } catch (SQLException e) {
            throw new RepositoryException("Failed to read GitHub index for version: " + version, e);
        }
    }

    @Override
    public void save(String version, List<GithubFileEntry> entries) {
        InputValidator.validateVersion(version);
        Objects.requireNonNull(entries, "entries must not be null");

        TransactionTemplate.executeInTransactionVoid(dataSource, conn -> {
            deleteVersion(conn, version);
            insertEntries(conn, version, entries);
        }, "Failed to write GitHub index for version: " + version);
    }

    @Override
    public void deleteByVersion(String version) {
        InputValidator.validateVersion(version);

        TransactionTemplate.executeInTransactionVoid(dataSource, conn -> {
            deleteVersion(conn, version);
        }, "Failed to delete GitHub index for version: " + version);
    }

    private void deleteVersion(Connection conn, String version) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "DELETE FROM github_index WHERE version = ?")) {
            stmt.setString(1, version);
            stmt.executeUpdate();
        }
    }

    private void insertEntries(Connection conn, String version, List<GithubFileEntry> entries) throws SQLException {
        if (entries == null || entries.isEmpty()) {
            return;
        }

        try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO github_index (version, name, path, sha) VALUES (?, ?, ?, ?)")) {
            for (GithubFileEntry entry : entries) {
                stmt.setString(1, version);
                stmt.setString(2, entry.name());
                stmt.setString(3, entry.path());
                stmt.setString(4, entry.sha());
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }
}
