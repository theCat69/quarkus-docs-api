package com.fvd.indexs.stores;

import com.fvd.asciidocs.model.DocumentMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@ApplicationScoped
public class DocumentMetadataStore {

    private final DataSource dataSource;

    @Inject
    public DocumentMetadataStore(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Protected no-arg constructor for Quarkus ARC proxy creation.
     */
    protected DocumentMetadataStore() {
        this.dataSource = null;
    }

    /**
     * Inserts metadata for a file. Called during indexing after the file is inserted.
     * Uses the active connection shared with KeywordIndexStore transaction.
     *
     * @param conn   the active connection (shared with KeywordIndexStore transaction)
     * @param fileId the file ID from the files table
     * @param metadata the extracted metadata
     */
    public void insert(Connection conn, long fileId, DocumentMetadata metadata) throws SQLException {
        String sql = "INSERT INTO document_metadata (file_id, categories, topics, extensions_gav, summary, diataxis_type) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, fileId);
            stmt.setString(2, joinList(metadata.getCategories()));
            stmt.setString(3, joinList(metadata.getTopics()));
            stmt.setString(4, joinList(metadata.getExtensions()));
            stmt.setString(5, metadata.getSummary());
            stmt.setString(6, metadata.getDiataxisType());
            stmt.executeUpdate();
        }
    }

    /**
     * Reads metadata for a specific file path and version.
     *
     * @param version the documentation version
     * @param path    the document file path
     * @return the metadata if found
     */
    public Optional<DocumentMetadata> readByPath(String version, String path) {
        String sql = "SELECT dm.categories, dm.topics, dm.extensions_gav, dm.summary, dm.diataxis_type "
                + "FROM document_metadata dm "
                + "JOIN files f ON dm.file_id = f.id "
                + "WHERE f.version = ? AND f.path = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, version);
            stmt.setString(2, path);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to read document metadata for path: " + path, e);
        }
        return Optional.empty();
    }

    /**
     * Reads metadata for all files of a version.
     *
     * @param version the documentation version
     * @return map of file path to metadata
     */
    public Map<String, DocumentMetadata> readAll(String version) {
        String sql = "SELECT f.path, dm.categories, dm.topics, dm.extensions_gav, dm.summary, dm.diataxis_type "
                + "FROM document_metadata dm "
                + "JOIN files f ON dm.file_id = f.id "
                + "WHERE f.version = ?";
        Map<String, DocumentMetadata> result = new LinkedHashMap<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, version);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String path = rs.getString("path");
                    result.put(path, mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to read all document metadata for version: " + version, e);
        }
        return result;
    }

    private DocumentMetadata mapRow(ResultSet rs) throws SQLException {
        return DocumentMetadata.builder()
                .categories(splitList(rs.getString("categories")))
                .topics(splitList(rs.getString("topics")))
                .extensions(splitList(rs.getString("extensions_gav")))
                .summary(rs.getString("summary"))
                .diataxisType(rs.getString("diataxis_type"))
                .build();
    }

    private String joinList(List<String> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        return String.join(",", list);
    }

    private List<String> splitList(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return Arrays.stream(text.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
