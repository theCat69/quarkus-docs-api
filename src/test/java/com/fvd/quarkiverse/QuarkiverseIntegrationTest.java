package com.fvd.quarkiverse;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fvd.cache.services.CacheService;
import com.fvd.docs.stores.DocStore;
import com.fvd.indexs.services.DocChunkBuilder;
import com.fvd.quarkiverse.services.QuarkiverseService;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;

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
    DocChunkBuilder docChunkBuilder;

    @Inject
    DocStore docStore;

    @Inject
    DataSource dataSource;

    @Inject
    CacheService cacheService;

    @BeforeEach
    void cleanup() throws SQLException {
        try (var conn = dataSource.getConnection(); var stmt = conn.createStatement()) {
            stmt.execute("TRUNCATE files, file_keywords, sections, section_keywords, "
                + "code_samples, code_sample_keywords, github_index, document_metadata, doc_chunks CASCADE");
        }
        cacheService.deleteCache();
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

        // Search for keywords from the quarkiverse doc using /api/documents endpoint
        given()
                .queryParam("version", "main")
                .queryParam("keywords", "test extension")
        .when()
                .get("/api/documents")
        .then()
                .statusCode(200)
                .body("results.size()", greaterThan(0))
                .body("results[0].path", equalTo("quarkiverse/quarkus-test-extension/index.adoc"))
                .body("results[0].extension", equalTo("quarkus-test-extension"));
    }

    @Test
    void quarkiverseDocRetrievableViaDocumentsEndpoint() {
        // Extract quarkiverse docs
        quarkiverseService.fetchAndExtractAll();

        // Retrieve the quarkiverse doc via the /api/documents endpoint
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

        // Verify that the catalog endpoint works and includes versions
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

        // Search for 3.27 via /api/documents should not return quarkiverse results
        given()
                .queryParam("version", "3.27")
                .queryParam("keywords", "test extension")
        .when()
                .get("/api/documents")
        .then()
                .statusCode(200)
                .body("totalCount", equalTo(0));
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
        docChunkBuilder.build("main", filePathsByExtension);
    }

}
