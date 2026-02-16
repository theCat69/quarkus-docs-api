package com.fvd.indexs.stores;

import com.fvd.indexs.model.ChunkSearchRow;
import com.fvd.indexs.model.DocChunk;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class DocChunkStore {

    private final DataSource dataSource;

    public void insertBatch(String version, List<DocChunk> chunks) {
        String sql = "INSERT INTO doc_chunks (id, version, page, title, section, url, topics, extensions, summary, content, content_tsv) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, to_tsvector('english', ?))";

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                for (DocChunk chunk : chunks) {
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
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to insert doc chunks for version: " + version, e);
        }
    }

    public void deleteByVersion(String version) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement("DELETE FROM doc_chunks WHERE version = ?")) {
            stmt.setString(1, version);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete doc chunks for version: " + version, e);
        }
    }

    public List<ChunkSearchRow> search(String query, String version, String extension, int limit, int offset) {
        StringBuilder sql = new StringBuilder(
                "SELECT id, version, page, title, section, url, topics, extensions, summary, content, "
                        + "ts_rank(content_tsv, plainto_tsquery('english', ?)) AS score "
                        + "FROM doc_chunks WHERE version = ? AND content_tsv @@ plainto_tsquery('english', ?)");

        boolean hasExtension = extension != null && !extension.isEmpty();
        if (hasExtension) {
            sql.append(" AND extensions @> ARRAY[?]::text[]");
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
            throw new RuntimeException("Failed to search doc chunks for version: " + version, e);
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
            throw new RuntimeException("Failed to fuzzy search doc chunks for version: " + version, e);
        }
    }

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
