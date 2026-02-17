package com.fvd.indexs.stores;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import jakarta.enterprise.context.ApplicationScoped;

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

    private final DataSource dataSource;

    /**
     * Atomically replaces all doc chunks for a version: deletes existing chunks
     * and inserts new ones within a single transaction.
     */
    public void replaceVersion(String version, List<DocChunk> chunks) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                deleteByVersion(conn, version);
                insertBatch(conn, version, chunks);
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new StoreException("Failed to replace doc chunks", e);
        }
    }

    public void insertBatch(String version, List<DocChunk> chunks) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                insertBatch(conn, version, chunks);
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new StoreException("Failed to insert doc chunks", e);
        }
    }

    public void deleteByVersion(String version) {
        try (Connection conn = dataSource.getConnection()) {
            deleteByVersion(conn, version);
        } catch (SQLException e) {
            throw new StoreException("Failed to delete doc chunks", e);
        }
    }

    private void deleteByVersion(Connection conn, String version) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM doc_chunks WHERE version = ?")) {
            stmt.setString(1, version);
            stmt.executeUpdate();
        }
    }

    private void insertBatch(Connection conn, String version, List<DocChunk> chunks) throws SQLException {
        String sql = "INSERT INTO doc_chunks (id, version, page, title, section, url, topics, extensions, summary, content, content_tsv) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, to_tsvector('english', ?))";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < chunks.size(); i += BATCH_SIZE) {
                List<DocChunk> batch = chunks.subList(i, Math.min(i + BATCH_SIZE, chunks.size()));
                for (DocChunk chunk : batch) {
                    stmt.setString(1, chunk.id());
                    stmt.setString(2, version);
                    stmt.setString(3, chunk.page());
                    stmt.setString(4, chunk.title());
                    stmt.setString(5, chunk.section());
                    stmt.setString(6, chunk.url());
                    stmt.setArray(7, conn.createArrayOf("text",
                            chunk.topics() != null ? chunk.topics().toArray(new String[0]) : new String[0]));
                    stmt.setArray(8, conn.createArrayOf("text",
                            chunk.extensions() != null ? chunk.extensions().toArray(new String[0]) : new String[0]));
                    stmt.setString(9, chunk.summary());
                    stmt.setString(10, chunk.content());
                    stmt.setString(11, chunk.content());
                    stmt.addBatch();
                }
                stmt.executeBatch();
            }
        }
    }

    public List<ChunkSearchRow> search(String query, String version, String extension, int limit, int offset) {
        StringBuilder sql = new StringBuilder(
                "SELECT id, version, page, title, section, url, topics, extensions, summary, content, "
                        + "ts_rank(content_tsv, plainto_tsquery('english', ?)) AS score "
                        + "FROM doc_chunks WHERE version = ? AND content_tsv @@ plainto_tsquery('english', ?)");

        boolean hasExtension = extension != null && !extension.isEmpty();
        if (hasExtension) {
            sql.append(" AND ? = ANY(extensions)");
        }
        sql.append(" ORDER BY score DESC LIMIT ? OFFSET ?");

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            int paramIndex = 1;
            stmt.setString(paramIndex++, query);
            stmt.setString(paramIndex++, version);
            stmt.setString(paramIndex++, query);
            if (hasExtension) {
                stmt.setString(paramIndex++, extension);
            }
            stmt.setInt(paramIndex++, limit);
            stmt.setInt(paramIndex, offset);

            try (ResultSet rs = stmt.executeQuery()) {
                List<ChunkSearchRow> results = new ArrayList<>();
                while (rs.next()) {
                    results.add(mapRow(rs));
                }
                return results;
            }
        } catch (SQLException e) {
            throw new StoreException("Failed to search doc chunks", e);
        }
    }

    public int countSearch(String query, String version, String extension) {
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) FROM doc_chunks WHERE version = ? AND content_tsv @@ plainto_tsquery('english', ?)");

        boolean hasExtension = extension != null && !extension.isEmpty();
        if (hasExtension) {
            sql.append(" AND ? = ANY(extensions)");
        }

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            int paramIndex = 1;
            stmt.setString(paramIndex++, version);
            stmt.setString(paramIndex++, query);
            if (hasExtension) {
                stmt.setString(paramIndex++, extension);
            }

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new StoreException("Failed to count search results", e);
        }
    }

    public List<ChunkSearchRow> fuzzySearch(String query, String version, int limit) {
        String sql = "SELECT id, version, page, title, section, url, topics, extensions, summary, content, "
                + "similarity(content, ?) AS score "
                + "FROM doc_chunks WHERE version = ? AND similarity(content, ?) > 0.1 "
                + "ORDER BY score DESC LIMIT ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, query);
            stmt.setString(2, version);
            stmt.setString(3, query);
            stmt.setInt(4, limit);

            try (ResultSet rs = stmt.executeQuery()) {
                List<ChunkSearchRow> results = new ArrayList<>();
                while (rs.next()) {
                    results.add(mapRow(rs));
                }
                return results;
            }
        } catch (SQLException e) {
            throw new StoreException("Failed to fuzzy search doc chunks", e);
        }
    }

    /**
     * Finds all chunks for a specific page and version.
     * Used by DocumentService to get extension/topics for a page.
     */
    public List<ChunkSearchRow> findByPage(String version, String page) {
        String sql = "SELECT id, version, page, title, section, url, topics, extensions, summary, content, "
                + "0.0 AS score FROM doc_chunks WHERE version = ? AND page = ? LIMIT 1000";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, version);
            stmt.setString(2, page);

            try (ResultSet rs = stmt.executeQuery()) {
                List<ChunkSearchRow> results = new ArrayList<>();
                while (rs.next()) {
                    results.add(mapRow(rs));
                }
                return results;
            }
        } catch (SQLException e) {
            throw new StoreException("Failed to find doc chunks by page", e);
        }
    }

    /**
     * Returns distinct extensions with doc counts for a version.
     * Each extension appears with the count of distinct pages that reference it.
     */
    public Map<String, Integer> findDistinctExtensionsWithDocCount(String version) {
        String sql = "SELECT UNNEST(extensions) AS ext, COUNT(DISTINCT page) AS doc_count "
                + "FROM doc_chunks WHERE version = ? GROUP BY ext ORDER BY doc_count DESC LIMIT 500";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, version);

            try (ResultSet rs = stmt.executeQuery()) {
                Map<String, Integer> result = new LinkedHashMap<>();
                while (rs.next()) {
                    result.put(rs.getString("ext"), rs.getInt("doc_count"));
                }
                return result;
            }
        } catch (SQLException e) {
            throw new StoreException("Failed to find distinct extensions", e);
        }
    }

    /**
     * Returns distinct topics with doc counts for a version.
     */
    public Map<String, Integer> findDistinctTopicsWithDocCount(String version) {
        String sql = "SELECT UNNEST(topics) AS topic, COUNT(DISTINCT page) AS doc_count "
                + "FROM doc_chunks WHERE version = ? GROUP BY topic ORDER BY doc_count DESC LIMIT 500";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, version);

            try (ResultSet rs = stmt.executeQuery()) {
                Map<String, Integer> result = new LinkedHashMap<>();
                while (rs.next()) {
                    result.put(rs.getString("topic"), rs.getInt("doc_count"));
                }
                return result;
            }
        } catch (SQLException e) {
            throw new StoreException("Failed to find distinct topics", e);
        }
    }

    /**
     * Finds pages that share topics or extensions with the given lists,
     * excluding a specific page. Returns page-level results.
     * Overlap score is computed in Java for simplicity and maintainability.
     */
    public List<RelatedPageRow> findRelatedPages(String version, String excludePage,
                                                 List<String> topics, List<String> extensions, int limit) {
        String sql = "SELECT DISTINCT ON (page) page, title, summary, topics, extensions "
                + "FROM doc_chunks "
                + "WHERE version = ? AND page != ? "
                + "AND (topics && ?::text[] OR extensions && ?::text[]) "
                + "ORDER BY page LIMIT ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, version);
            stmt.setString(2, excludePage);
            stmt.setArray(3, conn.createArrayOf("text",
                    topics != null ? topics.toArray(new String[0]) : new String[0]));
            stmt.setArray(4, conn.createArrayOf("text",
                    extensions != null ? extensions.toArray(new String[0]) : new String[0]));
            stmt.setInt(5, limit);

            try (ResultSet rs = stmt.executeQuery()) {
                List<RelatedPageRow> results = new ArrayList<>();
                while (rs.next()) {
                    Array topicsArray = rs.getArray("topics");
                    List<String> rowTopics = topicsArray != null
                            ? Arrays.asList((String[]) topicsArray.getArray())
                            : List.of();

                    Array extensionsArray = rs.getArray("extensions");
                    List<String> rowExtensions = extensionsArray != null
                            ? Arrays.asList((String[]) extensionsArray.getArray())
                            : List.of();

                    results.add(new RelatedPageRow(
                            rs.getString("page"),
                            rs.getString("title"),
                            rs.getString("summary"),
                            rowTopics,
                            rowExtensions,
                            0 // overlap score computed in Java
                    ));
                }
                return results;
            }
        } catch (SQLException e) {
            throw new StoreException("Failed to find related pages", e);
        }
    }

    /**
     * Represents a related page result with overlap metadata.
     */
    public record RelatedPageRow(
            String page, String title, String summary,
            List<String> topics, List<String> extensions,
            int overlapScore
    ) {}

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
}
