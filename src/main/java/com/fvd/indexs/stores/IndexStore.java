package com.fvd.indexs.stores;

import com.fvd.common.exceptions.StoreException;
import com.fvd.common.validators.InputValidator;
import com.fvd.github.clients.GithubApiIndex;
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
import java.util.Optional;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class IndexStore {

    private final DataSource dataSource;

    public Optional<List<GithubApiIndex>> read(String version) {
        InputValidator.validateVersion(version);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT name, path, sha FROM github_index WHERE version = ?")) {
            stmt.setString(1, version);
            try (ResultSet rs = stmt.executeQuery()) {
                List<GithubApiIndex> entries = new ArrayList<>();
                while (rs.next()) {
                    entries.add(new GithubApiIndex(
                            rs.getString("name"),
                            rs.getString("path"),
                            rs.getString("sha")));
                }
                if (entries.isEmpty()) {
                    return Optional.empty();
                }
                return Optional.of(entries);
            }
        } catch (SQLException e) {
            throw new StoreException("Failed to read file index for version: " + version, e);
        }
    }

    public void write(String version, List<GithubApiIndex> index) {
        InputValidator.validateVersion(version);

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Delete old entries for this version
                try (PreparedStatement deleteStmt = conn.prepareStatement(
                        "DELETE FROM github_index WHERE version = ?")) {
                    deleteStmt.setString(1, version);
                    deleteStmt.executeUpdate();
                }

                // Insert new entries
                try (PreparedStatement insertStmt = conn.prepareStatement(
                        "INSERT INTO github_index (version, name, path, sha) VALUES (?, ?, ?, ?)")) {
                    for (GithubApiIndex entry : index) {
                        insertStmt.setString(1, version);
                        insertStmt.setString(2, entry.name);
                        insertStmt.setString(3, entry.path);
                        insertStmt.setString(4, entry.sha);
                        insertStmt.addBatch();
                    }
                    insertStmt.executeBatch();
                }

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new StoreException("Failed to write file index for version: " + version, e);
        }
    }
}
