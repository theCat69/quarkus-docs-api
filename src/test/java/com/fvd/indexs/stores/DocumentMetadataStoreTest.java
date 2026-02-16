package com.fvd.indexs.stores;

import com.fvd.asciidocs.model.DocumentMetadata;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class DocumentMetadataStoreTest {

    @Inject
    DataSource dataSource;

    @Inject
    DocumentMetadataStore metadataStore;

    @BeforeEach
    void cleanup() throws SQLException {
        try (var conn = dataSource.getConnection(); var stmt = conn.createStatement()) {
            stmt.execute("TRUNCATE files, file_keywords, sections, section_keywords, "
                + "code_samples, code_sample_keywords, github_index, document_metadata CASCADE");
        }
    }

    @Test
    void insertAndReadByPath() throws SQLException {
        long fileId = insertFile("3.27", "security-oidc.adoc");

        DocumentMetadata metadata = DocumentMetadata.builder()
                .categories(List.of("security", "web"))
                .topics(List.of("security", "oidc", "authentication"))
                .extensions(List.of("io.quarkus:quarkus-oidc"))
                .summary("OIDC Authorization Code Flow")
                .diataxisType("reference")
                .build();

        try (Connection conn = dataSource.getConnection()) {
            metadataStore.insert(conn, fileId, metadata);
        }

        Optional<DocumentMetadata> result = metadataStore.readByPath("3.27", "security-oidc.adoc");

        assertThat(result).isPresent();
        DocumentMetadata loaded = result.get();
        assertThat(loaded.getCategories()).containsExactly("security", "web");
        assertThat(loaded.getTopics()).containsExactly("security", "oidc", "authentication");
        assertThat(loaded.getExtensions()).containsExactly("io.quarkus:quarkus-oidc");
        assertThat(loaded.getSummary()).isEqualTo("OIDC Authorization Code Flow");
        assertThat(loaded.getDiataxisType()).isEqualTo("reference");
    }

    @Test
    void insertAndReadAll() throws SQLException {
        long fileId1 = insertFile("3.27", "security.adoc");
        long fileId2 = insertFile("3.27", "config.adoc");

        DocumentMetadata metadata1 = DocumentMetadata.builder()
                .categories(List.of("security"))
                .topics(List.of("oidc"))
                .extensions(List.of())
                .summary("Security guide")
                .diataxisType("reference")
                .build();

        DocumentMetadata metadata2 = DocumentMetadata.builder()
                .categories(List.of("core"))
                .topics(List.of("config"))
                .extensions(List.of("io.quarkus:quarkus-config"))
                .summary("Config guide")
                .build();

        try (Connection conn = dataSource.getConnection()) {
            metadataStore.insert(conn, fileId1, metadata1);
            metadataStore.insert(conn, fileId2, metadata2);
        }

        Map<String, DocumentMetadata> all = metadataStore.readAll("3.27");

        assertThat(all).hasSize(2);
        assertThat(all).containsKey("security.adoc");
        assertThat(all).containsKey("config.adoc");
        assertThat(all.get("security.adoc").getCategories()).containsExactly("security");
        assertThat(all.get("config.adoc").getCategories()).containsExactly("core");
    }

    @Test
    void readByPathReturnsEmptyForNonExistentPath() {
        Optional<DocumentMetadata> result = metadataStore.readByPath("3.27", "nonexistent.adoc");

        assertThat(result).isEmpty();
    }

    @Test
    void readAllReturnsEmptyMapWhenNoData() {
        Map<String, DocumentMetadata> all = metadataStore.readAll("3.27");

        assertThat(all).isEmpty();
    }

    @Test
    void cascadingDeleteRemovesMetadataWhenFileIsDeleted() throws SQLException {
        long fileId = insertFile("3.27", "test.adoc");

        DocumentMetadata metadata = DocumentMetadata.builder()
                .categories(List.of("web"))
                .topics(List.of("rest"))
                .extensions(List.of())
                .summary("Test")
                .build();

        try (Connection conn = dataSource.getConnection()) {
            metadataStore.insert(conn, fileId, metadata);
        }

        // Verify metadata exists
        assertThat(metadataStore.readByPath("3.27", "test.adoc")).isPresent();

        // Delete the file row - metadata should cascade delete
        try (Connection conn = dataSource.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM files WHERE id = ?")) {
                stmt.setLong(1, fileId);
                stmt.executeUpdate();
            }
        }

        assertThat(metadataStore.readByPath("3.27", "test.adoc")).isEmpty();
    }

    @Test
    void insertWithEmptyMetadataStoresNulls() throws SQLException {
        long fileId = insertFile("3.27", "minimal.adoc");

        DocumentMetadata metadata = DocumentMetadata.empty();

        try (Connection conn = dataSource.getConnection()) {
            metadataStore.insert(conn, fileId, metadata);
        }

        Optional<DocumentMetadata> result = metadataStore.readByPath("3.27", "minimal.adoc");

        assertThat(result).isPresent();
        DocumentMetadata loaded = result.get();
        assertThat(loaded.getCategories()).isEmpty();
        assertThat(loaded.getTopics()).isEmpty();
        assertThat(loaded.getExtensions()).isEmpty();
        assertThat(loaded.getSummary()).isNull();
        assertThat(loaded.getDiataxisType()).isNull();
    }

    @Test
    void readAllFiltersOnVersion() throws SQLException {
        long fileId1 = insertFile("3.27", "test.adoc");
        long fileId2 = insertFile("main", "test.adoc");

        DocumentMetadata metadata1 = DocumentMetadata.builder()
                .categories(List.of("web"))
                .topics(List.of())
                .extensions(List.of())
                .build();

        DocumentMetadata metadata2 = DocumentMetadata.builder()
                .categories(List.of("security"))
                .topics(List.of())
                .extensions(List.of())
                .build();

        try (Connection conn = dataSource.getConnection()) {
            metadataStore.insert(conn, fileId1, metadata1);
            metadataStore.insert(conn, fileId2, metadata2);
        }

        Map<String, DocumentMetadata> result327 = metadataStore.readAll("3.27");
        Map<String, DocumentMetadata> resultMain = metadataStore.readAll("main");

        assertThat(result327).hasSize(1);
        assertThat(result327.get("test.adoc").getCategories()).containsExactly("web");

        assertThat(resultMain).hasSize(1);
        assertThat(resultMain.get("test.adoc").getCategories()).containsExactly("security");
    }

    /**
     * Helper to insert a file row and return the generated file ID.
     */
    private long insertFile(String version, String path) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT INTO files (version, path, extension) VALUES (?, ?, ?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, version);
            stmt.setString(2, path);
            stmt.setString(3, "quarkus-core");
            stmt.executeUpdate();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }
}
