package com.fvd.indexs.indexers;

import com.fvd.asciidocs.model.DocumentMetadata;
import com.fvd.cache.services.CacheService;
import com.fvd.docs.stores.DocStore;
import com.fvd.indexs.stores.DocumentMetadataStore;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class KeywordIndexerMetadataIntegrationTest {

    @Inject
    KeywordIndexer indexer;

    @Inject
    DocStore docStore;

    @Inject
    DocumentMetadataStore metadataStore;

    @Inject
    DataSource dataSource;

    @Inject
    CacheService cacheService;

    @BeforeEach
    void cleanup() throws SQLException {
        try (var conn = dataSource.getConnection(); var stmt = conn.createStatement()) {
            stmt.execute("TRUNCATE files, file_keywords, sections, section_keywords, "
                + "code_samples, code_sample_keywords, github_index, document_metadata CASCADE");
        }
        cacheService.deleteCache();
    }

    @Test
    void buildPersistsMetadataDuringIndexing() {
        String doc = """
                = Security OIDC Guide
                :categories: security,web
                :topics: security,oidc,authentication
                :extensions: io.quarkus:quarkus-oidc
                :summary: OIDC guide for web apps
                :diataxis-type: reference
                
                == Introduction
                
                Some content about security.
                """;
        docStore.write("3.27", "security-oidc.adoc", doc);

        indexer.build("3.27", List.of("security-oidc.adoc"));

        Map<String, DocumentMetadata> allMetadata = metadataStore.readAll("3.27");
        assertThat(allMetadata).hasSize(1);
        assertThat(allMetadata).containsKey("security-oidc.adoc");

        DocumentMetadata metadata = allMetadata.get("security-oidc.adoc");
        assertThat(metadata.getCategories()).containsExactly("security", "web");
        assertThat(metadata.getTopics()).containsExactly("security", "oidc", "authentication");
        assertThat(metadata.getExtensions()).containsExactly("io.quarkus:quarkus-oidc");
        assertThat(metadata.getSummary()).isEqualTo("OIDC guide for web apps");
        assertThat(metadata.getDiataxisType()).isEqualTo("reference");
    }

    @Test
    void buildPersistsMetadataForMultipleFiles() {
        docStore.write("3.27", "security.adoc", """
                = Security Guide
                :categories: security
                :topics: auth,oidc
                
                == Content
                
                Security content.
                """);
        docStore.write("3.27", "config.adoc", """
                = Configuration Guide
                :categories: core
                :summary: Config reference
                
                == Content
                
                Config content.
                """);

        indexer.build("3.27", List.of("security.adoc", "config.adoc"));

        Map<String, DocumentMetadata> allMetadata = metadataStore.readAll("3.27");
        assertThat(allMetadata).hasSize(2);
        assertThat(allMetadata.get("security.adoc").getCategories()).containsExactly("security");
        assertThat(allMetadata.get("config.adoc").getCategories()).containsExactly("core");
        assertThat(allMetadata.get("config.adoc").getSummary()).isEqualTo("Config reference");
    }

    @Test
    void buildHandlesFileWithNoMetadata() {
        docStore.write("3.27", "plain.adoc", """
                = Plain Guide
                
                == Section
                
                No metadata attributes in this document.
                """);

        indexer.build("3.27", List.of("plain.adoc"));

        // File with no meaningful metadata should still have a metadata row (empty)
        Optional<DocumentMetadata> metadata = metadataStore.readByPath("3.27", "plain.adoc");
        assertThat(metadata).isPresent();
        assertThat(metadata.get().getCategories()).isEmpty();
        assertThat(metadata.get().getTopics()).isEmpty();
        assertThat(metadata.get().getExtensions()).isEmpty();
    }

    @Test
    void buildFileEntryIncludesMetadata() {
        String content = """
                = Guide
                :categories: web
                :topics: rest
                
                == Section
                
                Content.
                """;

        FileKeywordEntry entry = indexer.buildFileEntry("test.adoc", content);

        assertThat(entry.metadata).isNotNull();
        assertThat(entry.metadata.getCategories()).containsExactly("web");
        assertThat(entry.metadata.getTopics()).containsExactly("rest");
    }

    @Test
    void metadataReadByPathReturnsCorrectly() {
        String doc = """
                = REST Guide
                :categories: web,getting-started
                :extensions: io.quarkus:quarkus-rest,io.quarkus:quarkus-rest-jackson
                :diataxis-type: tutorial
                
                == Getting Started
                
                REST tutorial content.
                """;
        docStore.write("main", "rest-getting-started.adoc", doc);

        indexer.build("main", List.of("rest-getting-started.adoc"));

        Optional<DocumentMetadata> result = metadataStore.readByPath("main", "rest-getting-started.adoc");
        assertThat(result).isPresent();
        assertThat(result.get().getCategories()).containsExactly("web", "getting-started");
        assertThat(result.get().getExtensions()).containsExactly(
                "io.quarkus:quarkus-rest", "io.quarkus:quarkus-rest-jackson");
        assertThat(result.get().getDiataxisType()).isEqualTo("tutorial");
    }
}
