# Pattern: Store (data access layer — raw JDBC with PreparedStatement)
# Demonstrates: @ApplicationScoped, @RequiredArgsConstructor, DataSource injection,
# try-with-resources, PreparedStatement with ? placeholders, transactional batch operations,
# StoreException wrapping, ResultSet mapping, and public record for query result rows.

```java
package com.fvd.indexs.stores;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import javax.sql.DataSource;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.fvd.common.exceptions.StoreException;
import com.fvd.indexs.model.ChunkSearchRow;
import com.fvd.indexs.model.DocChunk;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class DocChunkStore {

    private static final int BATCH_SIZE = 500;

    private final DataSource dataSource;       // Agroal DataSource injected via constructor

    /**
     * Atomically replaces all doc chunks for a version within a single transaction.
     */
    public void replaceVersion(String version, List<DocChunk> chunks) {
        // Always use try-with-resources for Connection to prevent leaks
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);          // Begin manual transaction
            try {
                deleteByVersion(conn, version);
                insertBatch(conn, version, chunks);
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();                // Roll back on any failure
                throw e;
            } finally {
                conn.setAutoCommit(true);       // Restore auto-commit
            }
        } catch (SQLException e) {
            // Wrap SQLException — never expose raw SQL errors to upper layers
            throw new StoreException("Failed to replace doc chunks", e);
        }
    }

    /**
     * Full-text search using plainto_tsquery + ts_rank, with optional extension filter.
     */
    public List<ChunkSearchRow> search(String query, String version, String extension, int limit, int offset) {
        // Build SQL with optional clause — never concatenate user input
        StringBuilder sql = new StringBuilder(
                "SELECT id, version, page, title, section, url, topics, extensions, summary, content, "
                        + "ts_rank(content_tsv, plainto_tsquery('english', ?)) AS score "
                        + "FROM doc_chunks WHERE version = ? AND content_tsv @@ plainto_tsquery('english', ?)");

        boolean hasExtension = extension != null && !extension.isEmpty();
        if (hasExtension) {
            sql.append(" AND ? = ANY(extensions)");
        }
        sql.append(" ORDER BY score DESC LIMIT ? OFFSET ?");

        // try-with-resources for Connection and PreparedStatement
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

            // Bind all parameters with positional setters — NEVER string concatenation
            int paramIndex = 1;
            stmt.setString(paramIndex++, query);
            stmt.setString(paramIndex++, version);
            stmt.setString(paramIndex++, query);
            if (hasExtension) {
                stmt.setString(paramIndex++, extension);
            }
            stmt.setInt(paramIndex++, limit);
            stmt.setInt(paramIndex, offset);

            // try-with-resources for ResultSet
            try (ResultSet rs = stmt.executeQuery()) {
                List<ChunkSearchRow> results = new ArrayList<>();
                while (rs.next()) {
                    results.add(mapRow(rs));   // Extract mapping to a private method
                }
                return results;
            }
        } catch (SQLException e) {
            throw new StoreException("Failed to search doc chunks", e);
        }
    }

    private void deleteByVersion(Connection conn, String version) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "DELETE FROM doc_chunks WHERE version = ?")) {
            stmt.setString(1, version);
            stmt.executeUpdate();
        }
    }

    private void insertBatch(Connection conn, String version, List<DocChunk> chunks) throws SQLException {
        String sql = "INSERT INTO doc_chunks (version, page, title, section, url, topics, extensions, "
                + "summary, content, content_tsv) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, to_tsvector('english', ?))";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < chunks.size(); i += BATCH_SIZE) {
                List<DocChunk> batch = chunks.subList(i, Math.min(i + BATCH_SIZE, chunks.size()));
                for (DocChunk chunk : batch) {
                    stmt.setString(1, version);
                    stmt.setString(2, chunk.page());
                    stmt.setString(3, chunk.title());
                    stmt.setString(4, chunk.section());
                    stmt.setString(5, chunk.url());
                    // PostgreSQL text[] arrays use conn.createArrayOf
                    stmt.setArray(6, conn.createArrayOf("text",
                            chunk.topics() != null ? chunk.topics().toArray(new String[0]) : new String[0]));
                    stmt.setArray(7, conn.createArrayOf("text",
                            chunk.extensions() != null ? chunk.extensions().toArray(new String[0]) : new String[0]));
                    stmt.setString(8, chunk.summary());
                    stmt.setString(9, chunk.content());
                    stmt.setString(10, chunk.content());
                    stmt.addBatch();
                }
                stmt.executeBatch();
            }
        }
    }

    // Private mapping method — maps ResultSet row to a typed record
    private ChunkSearchRow mapRow(ResultSet rs) throws SQLException {
        Array topicsArray = rs.getArray("topics");
        List<String> topics = topicsArray != null
                ? List.of((String[]) topicsArray.getArray())
                : List.of();

        Array extensionsArray = rs.getArray("extensions");
        List<String> extensions = extensionsArray != null
                ? List.of((String[]) extensionsArray.getArray())
                : List.of();

        return new ChunkSearchRow(
                rs.getString("id"),
                rs.getString("version"),
                rs.getString("page"),
                rs.getString("title"),
                rs.getString("section"),
                rs.getString("url"),
                topics,
                extensions,
                rs.getString("summary"),
                rs.getString("content"),
                rs.getDouble("score")
        );
    }

    /**
     * Public record for related-page query results. Records provide value-based equals/hashCode.
     */
    public record RelatedPageRow(
            String page, String title, String summary,
            List<String> topics, List<String> extensions,
            int overlapScore
    ) {}
}
```
