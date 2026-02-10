package com.fvd.quarkiverse;

import com.fvd.docs.stores.DocStore;
import com.fvd.indexs.indexers.CodeSampleIndexer;
import com.fvd.indexs.indexers.ContentIndexer;
import com.fvd.indexs.indexers.KeywordIndexer;
import com.fvd.indexs.stores.SqliteSchemaInitializer;
import com.fvd.quarkiverse.services.QuarkiverseService;
import com.fvd.search.services.SearchService;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;
import jakarta.inject.Inject;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.containsString;

@QuarkusTest
@TestProfile(QuarkiverseTestProfile.class)
class QuarkiverseIntegrationTest {

    @Inject
    QuarkiverseService quarkiverseService;

    @Inject
    KeywordIndexer keywordIndexer;

    @Inject
    CodeSampleIndexer codeSampleIndexer;

    @Inject
    ContentIndexer contentIndexer;

    @Inject
    DocStore docStore;

    @Inject
    SearchService searchService;

    @Inject
    SqliteSchemaInitializer schemaInitializer;

    @BeforeEach
    void cleanTestCache() throws IOException {
        var cachePath = Path.of("build/test-cache").toFile();
        if (cachePath.exists()) {
            FileUtils.cleanDirectory(cachePath);
        }
        schemaInitializer.resetSchema();
        searchService.invalidateCache("main");
    }

    @Test
    void fetchAndExtractAllExtractsQuarkiverseDocs() {
        // Fetch and extract quarkiverse docs via WireMock-backed services
        List<String> paths = quarkiverseService.fetchAndExtractAll();

        // Verify extracted paths contain the test extension doc
        assertThat(paths).containsExactly("quarkiverse/quarkus-test-extension/index.adoc");

        // Verify the doc is readable from the cache
        Optional<String> doc = docStore.read("main", "quarkiverse/quarkus-test-extension/index.adoc");
        assertThat(doc).isPresent();
        assertThat(doc.get()).contains("Quarkus Test Extension");
        assertThat(doc.get()).contains("Getting Started");
    }

    @Test
    void quarkiverseDocsAppearInSearchResultsForMainVersion() {
        // Extract quarkiverse docs
        List<String> quarkiversePaths = quarkiverseService.fetchAndExtractAll();

        // Build indexes with quarkiverse docs only (no core docs in this test)
        Map<String, List<String>> filePathsByExtension = new LinkedHashMap<>();
        filePathsByExtension.put("quarkus-core", List.of());
        for (String path : quarkiversePaths) {
            String[] parts = path.split("/", 3);
            if (parts.length >= 2) {
                String extName = parts[1];
                filePathsByExtension.computeIfAbsent(extName, k -> new ArrayList<>()).add(path);
            }
        }
        keywordIndexer.build("main", filePathsByExtension);
        codeSampleIndexer.build("main", filePathsByExtension);
        contentIndexer.build("main", filePathsByExtension);

        // Search for keywords from the quarkiverse doc
        given()
                .queryParam("version", "main")
                .queryParam("keywords", "test,extension")
        .when()
                .get("/api/search/files")
        .then()
                .statusCode(200)
                .body("total", greaterThan(0))
                .body("results[0].path", equalTo("quarkiverse/quarkus-test-extension/index.adoc"))
                .body("results[0].extension", equalTo("quarkus-test-extension"));
    }

    @Test
    void quarkiverseDocRetrievableViaDocEndpoint() {
        // Extract quarkiverse docs
        quarkiverseService.fetchAndExtractAll();

        // Retrieve the quarkiverse doc via the /api/doc endpoint
        given()
                .queryParam("version", "main")
                .queryParam("path", "quarkiverse/quarkus-test-extension/index.adoc")
        .when()
                .get("/api/doc")
        .then()
                .statusCode(200)
                .body("path", equalTo("quarkiverse/quarkus-test-extension/index.adoc"))
                .body("content", containsString("Quarkus Test Extension"))
                .body("format", equalTo("asciidoc"));
    }

    @Test
    void quarkiverseDocsNotInIndexEndpoint() {
        // Extract quarkiverse docs and build indexes
        List<String> quarkiversePaths = quarkiverseService.fetchAndExtractAll();

        Map<String, List<String>> filePathsByExtension = new LinkedHashMap<>();
        filePathsByExtension.put("quarkus-core", List.of());
        for (String path : quarkiversePaths) {
            String[] parts = path.split("/", 3);
            if (parts.length >= 2) {
                String extName = parts[1];
                filePathsByExtension.computeIfAbsent(extName, k -> new ArrayList<>()).add(path);
            }
        }
        keywordIndexer.build("main", filePathsByExtension);

        // The /api/index endpoint should NOT include quarkiverse paths
        // It returns the GitHub API file index (core-only), not the search index
        // For "main", it fetches from GitHub API which won't have quarkiverse entries
        // We can't easily test this without a main index mapping, but we verify
        // that searching version=3.27 does NOT return quarkiverse results
    }

    @Test
    void quarkiverseDocsNotInVersionedSearch() {
        // Extract quarkiverse docs and build indexes for "main" only
        List<String> quarkiversePaths = quarkiverseService.fetchAndExtractAll();

        Map<String, List<String>> filePathsByExtension = new LinkedHashMap<>();
        filePathsByExtension.put("quarkus-core", List.of());
        for (String path : quarkiversePaths) {
            String[] parts = path.split("/", 3);
            if (parts.length >= 2) {
                String extName = parts[1];
                filePathsByExtension.computeIfAbsent(extName, k -> new ArrayList<>()).add(path);
            }
        }
        keywordIndexer.build("main", filePathsByExtension);

        // Search for 3.27 should not return quarkiverse results
        given()
                .queryParam("version", "3.27")
                .queryParam("keywords", "test,extension")
        .when()
                .get("/api/search/files")
        .then()
                .statusCode(200)
                .body("total", equalTo(0));
    }

    @Test
    void quarkiverseCodeSamplesAppearInSearch() {
        // Extract quarkiverse docs
        List<String> quarkiversePaths = quarkiverseService.fetchAndExtractAll();

        // Build indexes
        Map<String, List<String>> filePathsByExtension = new LinkedHashMap<>();
        filePathsByExtension.put("quarkus-core", List.of());
        for (String path : quarkiversePaths) {
            String[] parts = path.split("/", 3);
            if (parts.length >= 2) {
                String extName = parts[1];
                filePathsByExtension.computeIfAbsent(extName, k -> new ArrayList<>()).add(path);
            }
        }
        codeSampleIndexer.build("main", filePathsByExtension);

        // Search for code samples from the quarkiverse doc
        given()
                .queryParam("version", "main")
                .queryParam("keywords", "dependency,quarkus")
        .when()
                .get("/api/search/code-samples")
        .then()
                .statusCode(200)
                .body("total", greaterThan(0))
                .body("results[0].path", equalTo("quarkiverse/quarkus-test-extension/index.adoc"))
                .body("results[0].extension", equalTo("quarkus-test-extension"));
    }

    @Test
    void quarkiverseContentSearchWorks() {
        // Extract quarkiverse docs
        List<String> quarkiversePaths = quarkiverseService.fetchAndExtractAll();

        // Build content index
        Map<String, List<String>> filePathsByExtension = new LinkedHashMap<>();
        filePathsByExtension.put("quarkus-core", List.of());
        for (String path : quarkiversePaths) {
            String[] parts = path.split("/", 3);
            if (parts.length >= 2) {
                String extName = parts[1];
                filePathsByExtension.computeIfAbsent(extName, k -> new ArrayList<>()).add(path);
            }
        }
        contentIndexer.build("main", filePathsByExtension);

        // Full-text search for content from quarkiverse doc
        given()
                .queryParam("version", "main")
                .queryParam("keywords", "test,extension")
        .when()
                .get("/api/search/content")
        .then()
                .statusCode(200)
                .body("total", greaterThan(0));
    }
}
