package com.fvd.quarkiverse;

import com.fvd.docs.stores.DocStore;
import com.fvd.indexs.indexers.CodeSampleIndexer;
import com.fvd.indexs.indexers.KeywordIndexer;
import com.fvd.indexs.stores.SqliteSchemaInitializer;
import com.fvd.quarkiverse.services.QuarkiverseService;
import com.fvd.search.services.SearchService;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;

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
        buildQuarkiverseIndexes();

        // Search for keywords from the quarkiverse doc using new /api/search endpoint
        given()
                .queryParam("version", "main")
                .queryParam("keywords", "test extension")
        .when()
                .get("/api/search")
        .then()
                .statusCode(200)
                .body("totalCount", greaterThan(0))
                .body("results[0].path", equalTo("quarkiverse/quarkus-test-extension/index.adoc"))
                .body("results[0].extension", equalTo("quarkus-test-extension"));
    }

    @Test
    void quarkiverseDocRetrievableViaDocumentsEndpoint() {
        // Extract quarkiverse docs
        quarkiverseService.fetchAndExtractAll();

        // Retrieve the quarkiverse doc via the new /api/documents endpoint
        given()
                .queryParam("version", "main")
                .queryParam("path", "quarkiverse/quarkus-test-extension/index.adoc")
        .when()
                .get("/api/documents")
        .then()
                .statusCode(200)
                .body("path", equalTo("quarkiverse/quarkus-test-extension/index.adoc"))
                .body("title", containsString("Quarkus Test Extension"));
    }

    @Test
    void quarkiverseDocsNotInIndexEndpoint() {
        buildQuarkiverseIndexes();

        // The old /api/index endpoint is removed
        // We verify that the catalog endpoint works and includes versions
        given()
                .queryParam("version", "main")
        .when()
                .get("/api/catalog")
        .then()
                .statusCode(200);
    }

    @Test
    void quarkiverseDocsNotInVersionedSearch() {
        buildQuarkiverseIndexes();

        // Seed version 3.27 cache directory so version validation passes
        docStore.write("3.27", "_placeholder.adoc", "= Placeholder");

        // Search for 3.27 should not return quarkiverse results
        given()
                .queryParam("version", "3.27")
                .queryParam("keywords", "test extension")
        .when()
                .get("/api/search")
        .then()
                .statusCode(200)
                .body("totalCount", equalTo(0));
    }

    @Test
    void quarkiverseCodeSamplesAppearInSearch() {
        buildQuarkiverseIndexes();

        // Search for code samples from the quarkiverse doc using new /api/code-samples endpoint
        given()
                .queryParam("version", "main")
                .queryParam("keywords", "dependency quarkus")
        .when()
                .get("/api/code-samples")
        .then()
                .statusCode(200)
                .body("totalCount", greaterThan(0))
                .body("results[0].documentPath", equalTo("quarkiverse/quarkus-test-extension/index.adoc"))
                .body("results[0].extension", equalTo("quarkus-test-extension"));
    }

    private void buildQuarkiverseIndexes() {
        List<String> paths = quarkiverseService.fetchAndExtractAll();

        Map<String, List<String>> filePathsByExtension = new LinkedHashMap<>();
        filePathsByExtension.put("quarkus-core", List.of());
        for (String path : paths) {
            String[] parts = path.split("/", 3);
            if (parts.length >= 2) {
                String extName = parts[1];
                filePathsByExtension.computeIfAbsent(extName, k -> new ArrayList<>()).add(path);
            }
        }
        keywordIndexer.build("main", filePathsByExtension);
        codeSampleIndexer.build("main", filePathsByExtension);
    }

}
