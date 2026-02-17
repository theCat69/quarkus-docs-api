package com.fvd.indexs.stores;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import jakarta.inject.Inject;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fvd.indexs.model.ChunkSearchRow;
import com.fvd.indexs.model.DocChunk;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class DocChunkStoreTest {

    @Inject
    DocChunkStore docChunkStore;

    @Inject
    DataSource dataSource;

    private static final String VERSION = "test-v1";

    @BeforeEach
    void setUp() throws SQLException {
        cleanUp();
    }

    @AfterEach
    void tearDown() throws SQLException {
        cleanUp();
    }

    private void cleanUp() throws SQLException {
        try (var conn = dataSource.getConnection(); var stmt = conn.createStatement()) {
            stmt.execute("TRUNCATE doc_chunks CASCADE");
        }
    }

    @Test
    void shouldReturnRankedResultsForSearch() {
        DocChunk highRelevance = new DocChunk(
                "reactive#overview", VERSION, "reactive", "Reactive Guide", "Overview",
                "https://quarkus.io/guides/reactive#overview",
                List.of("reactive"), List.of("quarkus-core"),
                "Overview of reactive programming",
                "Reactive programming is a paradigm for building reactive systems. " +
                        "Reactive streams and reactive extensions are core to reactive programming.");
        DocChunk lowRelevance = new DocChunk(
                "config#overview", VERSION, "config", "Config Guide", "Overview",
                "https://quarkus.io/guides/config#overview",
                List.of("config"), List.of("quarkus-core"),
                "Overview of configuration",
                "Configuration management for application properties and settings in your project.");

        docChunkStore.insertBatch(VERSION, List.of(highRelevance, lowRelevance));

        List<ChunkSearchRow> results = docChunkStore.search("reactive", VERSION, null, 10, 0);

        assertThat(results).isNotEmpty();
        assertThat(results.getFirst().page()).isEqualTo("reactive");
        assertThat(results.getFirst().score()).isGreaterThan(0.0);
    }

    @Test
    void shouldReturnResultsForFuzzySearch() {
        DocChunk chunk = new DocChunk(
                "security#overview", VERSION, "security", "Security Guide", "Overview",
                "https://quarkus.io/guides/security#overview",
                List.of("security"), List.of("quarkus-core"),
                "Overview of security features",
                "This guide covers security authentication and authorization basics for Quarkus applications.");

        docChunkStore.insertBatch(VERSION, List.of(chunk));

        List<ChunkSearchRow> results = docChunkStore.fuzzySearch(
                "securty authenticaton", VERSION, 10);

        assertThat(results).isNotEmpty();
    }

    @Test
    void shouldDeleteOnlyTargetVersion() {
        String version2 = "test-v2";

        DocChunk v1Chunk = new DocChunk(
                "rest#v1", VERSION, "rest", "REST Guide", "Overview",
                "https://quarkus.io/guides/rest#overview",
                List.of("rest"), List.of("quarkus-core"),
                "REST overview", "Content about REST endpoints.");
        DocChunk v2Chunk = new DocChunk(
                "rest#v2", version2, "rest", "REST Guide", "Overview",
                "https://quarkus.io/guides/rest#overview",
                List.of("rest"), List.of("quarkus-core"),
                "REST overview", "Content about REST endpoints.");

        docChunkStore.insertBatch(VERSION, List.of(v1Chunk));
        docChunkStore.insertBatch(version2, List.of(v2Chunk));

        docChunkStore.deleteByVersion(VERSION);

        List<ChunkSearchRow> v1Results = docChunkStore.findByPage(VERSION, "rest");
        List<ChunkSearchRow> v2Results = docChunkStore.findByPage(version2, "rest");

        assertThat(v1Results).isEmpty();
        assertThat(v2Results).hasSize(1);
        assertThat(v2Results.getFirst().version()).isEqualTo(version2);
    }

    @Test
    void shouldRoundTripTextArraysCorrectly() {
        List<String> topics = List.of("security", "web", "authentication");
        List<String> extensions = List.of("quarkus-core", "quarkus-oidc");

        DocChunk chunk = new DocChunk(
                "security#arrays", VERSION, "security", "Security Guide", "Arrays Test",
                "https://quarkus.io/guides/security#arrays-test",
                topics, extensions,
                "Testing array roundtrip", "Content for array test.");

        docChunkStore.insertBatch(VERSION, List.of(chunk));

        List<ChunkSearchRow> results = docChunkStore.findByPage(VERSION, "security");

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().topics()).containsExactlyElementsOf(topics);
        assertThat(results.getFirst().extensions()).containsExactlyElementsOf(extensions);
    }

    @Test
    void shouldReturnCountForCountSearch() {
        DocChunk chunk1 = new DocChunk(
                "rest#intro", VERSION, "rest", "REST Guide", "Introduction",
                "https://quarkus.io/guides/rest#introduction",
                List.of("rest"), List.of("quarkus-core"),
                "REST introduction", "Building REST endpoints with Quarkus framework.");
        DocChunk chunk2 = new DocChunk(
                "rest#advanced", VERSION, "rest", "REST Guide", "Advanced",
                "https://quarkus.io/guides/rest#advanced",
                List.of("rest"), List.of("quarkus-core"),
                "Advanced REST", "Advanced REST patterns and endpoints configuration with Quarkus.");

        docChunkStore.insertBatch(VERSION, List.of(chunk1, chunk2));

        int count = docChunkStore.countSearch("REST", VERSION, null);

        assertThat(count).isEqualTo(2);
    }

    @Test
    void shouldFindDistinctExtensionsWithDocCount() {
        DocChunk coreChunk1 = new DocChunk(
                "rest#overview", VERSION, "rest", "REST Guide", "Overview",
                null, List.of("rest"), List.of("quarkus-core"),
                "REST overview", "Content about REST.");
        DocChunk coreChunk2 = new DocChunk(
                "security#overview", VERSION, "security", "Security Guide", "Overview",
                null, List.of("security"), List.of("quarkus-core"),
                "Security overview", "Content about security.");
        DocChunk oidcChunk = new DocChunk(
                "oidc#overview", VERSION, "oidc", "OIDC Guide", "Overview",
                null, List.of("security"), List.of("quarkus-oidc"),
                "OIDC overview", "Content about OIDC.");

        docChunkStore.insertBatch(VERSION, List.of(coreChunk1, coreChunk2, oidcChunk));

        Map<String, Integer> extensions = docChunkStore.findDistinctExtensionsWithDocCount(VERSION);

        assertThat(extensions).containsKey("quarkus-core");
        assertThat(extensions).containsKey("quarkus-oidc");
        assertThat(extensions.get("quarkus-core")).isEqualTo(2);
        assertThat(extensions.get("quarkus-oidc")).isEqualTo(1);
    }

    @Test
    void shouldFindDistinctTopicsWithDocCount() {
        DocChunk secChunk1 = new DocChunk(
                "security#overview", VERSION, "security", "Security Guide", "Overview",
                null, List.of("security", "auth"), List.of("quarkus-core"),
                "Security overview", "Content about security.");
        DocChunk secChunk2 = new DocChunk(
                "oidc#overview", VERSION, "oidc", "OIDC Guide", "Overview",
                null, List.of("security", "oidc"), List.of("quarkus-oidc"),
                "OIDC overview", "Content about OIDC.");

        docChunkStore.insertBatch(VERSION, List.of(secChunk1, secChunk2));

        Map<String, Integer> topics = docChunkStore.findDistinctTopicsWithDocCount(VERSION);

        assertThat(topics).containsKey("security");
        assertThat(topics.get("security")).isEqualTo(2);
        assertThat(topics).containsKey("auth");
        assertThat(topics.get("auth")).isEqualTo(1);
        assertThat(topics).containsKey("oidc");
        assertThat(topics.get("oidc")).isEqualTo(1);
    }

    @Test
    void shouldFindRelatedPages() {
        DocChunk securityChunk = new DocChunk(
                "security#overview", VERSION, "security", "Security Guide", "Overview",
                null, List.of("security", "auth"), List.of("quarkus-core"),
                "Security overview", "Content about security.");
        DocChunk oidcChunk = new DocChunk(
                "oidc#overview", VERSION, "oidc", "OIDC Guide", "Overview",
                null, List.of("security", "oidc"), List.of("quarkus-oidc"),
                "OIDC overview", "Content about OIDC authentication.");
        DocChunk configChunk = new DocChunk(
                "config#overview", VERSION, "config", "Config Guide", "Overview",
                null, List.of("config"), List.of("quarkus-core"),
                "Config overview", "Content about configuration.");

        docChunkStore.insertBatch(VERSION, List.of(securityChunk, oidcChunk, configChunk));

        List<DocChunkStore.RelatedPageRow> related = docChunkStore.findRelatedPages(
                VERSION, "security", List.of("security", "auth"), List.of("quarkus-core"), 10);

        assertThat(related).isNotEmpty();
        assertThat(related).extracting(DocChunkStore.RelatedPageRow::page)
                .doesNotContain("security");
        assertThat(related).extracting(DocChunkStore.RelatedPageRow::page)
                .contains("oidc");
    }
}
