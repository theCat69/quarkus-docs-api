package com.fvd.search.resources;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import jakarta.inject.Inject;

import com.fvd.docs.stores.DocStore;
import com.fvd.indexs.indexers.CodeSampleEntry;
import com.fvd.indexs.indexers.CodeSampleIndex;
import com.fvd.indexs.indexers.FileKeywordEntry;
import com.fvd.indexs.indexers.KeywordIndex;
import com.fvd.indexs.indexers.KeywordScore;
import com.fvd.indexs.indexers.SectionKeywordEntry;
import com.fvd.indexs.stores.CodeSampleIndexStore;
import com.fvd.indexs.stores.KeywordIndexStore;
import com.fvd.indexs.stores.SqliteSchemaInitializer;
import com.fvd.search.services.SearchService;
import io.quarkus.test.junit.QuarkusTest;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class MultiParameterSearchTest {

    private static final String VERSION = "3.27";

    @Inject
    KeywordIndexStore keywordIndexStore;

    @Inject
    CodeSampleIndexStore codeSampleIndexStore;

    @Inject
    DocStore docStore;

    @Inject
    SqliteSchemaInitializer schemaInitializer;

    @Inject
    SearchService searchService;

    @BeforeEach
    void cleanTestCache() throws IOException {
        var cachePath = Path.of("build/test-cache").toFile();
        if (cachePath.exists()) {
            FileUtils.cleanDirectory(cachePath);
        }
        schemaInitializer.resetSchema();
        searchService.invalidateCache(VERSION);
        searchService.invalidateCache("main");
    }

    // ---- Test 1: keywords + extension filter reduces results ----

    @Test
    void keywordsAndExtensionFilterReducesResults() {
        seedAllData();

        // Without extension filter — should return results from both extensions
        int unfilteredTotal = given()
                .queryParam("version", VERSION)
                .queryParam("keywords", "security")
                .when().get("/api/search/files")
                .then()
                .statusCode(200)
                .body("results.size()", greaterThan(1))
                .extract().path("total");

        // With extension filter — should return fewer results
        given()
                .queryParam("version", VERSION)
                .queryParam("keywords", "security")
                .queryParam("extension", "quarkus-core")
                .when().get("/api/search/files")
                .then()
                .statusCode(200)
                .body("total", lessThanTotal(unfilteredTotal))
                .body("results.extension", everyItem(equalTo("quarkus-core")));
    }

    // ---- Test 2: sections triple filter (keywords + filePaths + extension) ----

    @Test
    void sectionsTripleFilter() {
        seedAllData();

        given()
                .queryParam("version", VERSION)
                .queryParam("keywords", "security")
                .queryParam("filePaths", "security-overview.adoc")
                .queryParam("extension", "quarkus-core")
                .when().get("/api/search/sections")
                .then()
                .statusCode(200)
                .body("results.size()", greaterThan(0))
                .body("results[0].path", equalTo("security-overview.adoc"))
                .body("results[0].score", greaterThan(0f))
                .body("results[0].matchedKeywords", notNullValue());
    }

    // ---- Test 3: pagination with extension filter ----

    @Test
    void paginationWithExtensionFilter() {
        seedAllData();

        // First, get the total filtered count without pagination
        int filteredTotal = given()
                .queryParam("version", VERSION)
                .queryParam("keywords", "security authentication")
                .queryParam("extension", "quarkus-core")
                .when().get("/api/search/files")
                .then()
                .statusCode(200)
                .extract().path("total");

        // Now paginate with limit=1
        given()
                .queryParam("version", VERSION)
                .queryParam("keywords", "security authentication")
                .queryParam("extension", "quarkus-core")
                .queryParam("limit", 1)
                .queryParam("offset", 0)
                .when().get("/api/search/files")
                .then()
                .statusCode(200)
                .body("results.size()", is(1))
                .body("total", is(filteredTotal))
                .body("limit", is(1))
                .body("offset", is(0));
    }

    // ---- Test 4: code-samples with all 4 filters ----

    @Test
    void codeSamplesMultipleFilters() {
        seedAllData();

        given()
                .queryParam("version", VERSION)
                .queryParam("keywords", "oidc")
                .queryParam("sectionTitle", "Authentication")
                .queryParam("filePath", "oidc-guide.adoc")
                .queryParam("extension", "quarkus-oidc")
                .when().get("/api/search/code-samples")
                .then()
                .statusCode(200)
                .body("results.size()", greaterThan(0))
                .body("results[0].path", equalTo("oidc-guide.adoc"))
                .body("results[0].extension", equalTo("quarkus-oidc"));
    }

    // ---- Test 5: stop words removed with extension filter ----

    @Test
    void stopWordsRemovedWithExtensionFilter() {
        seedAllData();

        given()
                .queryParam("version", VERSION)
                .queryParam("keywords", "the security of")
                .queryParam("extension", "quarkus-core")
                .when().get("/api/search/files")
                .then()
                .statusCode(200)
                .body("results.size()", greaterThan(0))
                .body("queriedKeywords", hasItem("security"))
                .body("queriedKeywords", not(hasItem("the")))
                .body("queriedKeywords", not(hasItem("of")))
                .body("results.extension", everyItem(equalTo("quarkus-core")));
    }

    // ---- Test 6: sections keywords + extension (quarkus-oidc only) ----

    @Test
    void sectionsKeywordsAndExtension() {
        seedAllData();

        given()
                .queryParam("version", VERSION)
                .queryParam("keywords", "authentication")
                .queryParam("extension", "quarkus-oidc")
                .when().get("/api/search/sections")
                .then()
                .statusCode(200)
                .body("results.size()", greaterThan(0))
                .body("results.path", everyItem(is(
                        org.hamcrest.Matchers.oneOf("oidc-guide.adoc", "oidc-config.adoc"))));
    }

    // ---- Test 7: nonexistent extension returns empty ----

    @Test
    void nonexistentExtensionReturnsEmpty() {
        seedAllData();

        given()
                .queryParam("version", VERSION)
                .queryParam("keywords", "security")
                .queryParam("extension", "nonexistent-ext")
                .when().get("/api/search/files")
                .then()
                .statusCode(200)
                .body("results.size()", is(0))
                .body("total", is(0));
    }

    // ---- Test 8: no extension returns all extensions ----

    @Test
    void noExtensionReturnsAllExtensions() {
        seedAllData();

        given()
                .queryParam("version", VERSION)
                .queryParam("keywords", "security")
                .when().get("/api/search/files")
                .then()
                .statusCode(200)
                .body("results.size()", greaterThan(1))
                .body("total", greaterThan(1))
                .body("results.extension", hasItem("quarkus-core"))
                .body("results.extension", hasItem("quarkus-oidc"));
    }

    // ---- Seed helpers ----

    private void seedAllData() {
        seedKeywordIndex();
        seedCodeSampleIndex();
        seedDocFiles();
        searchService.invalidateCache(VERSION);
    }

    private void seedKeywordIndex() {
        KeywordIndex index = new KeywordIndex(List.of(
                // quarkus-core: security-overview.adoc (14 lines)
                new FileKeywordEntry("security-overview.adoc",
                        List.of(
                                new KeywordScore("security", 20),
                                new KeywordScore("authentication", 15),
                                new KeywordScore("authorization", 12)),
                        List.of(
                                new SectionKeywordEntry("Security Fundamentals", 4, 8,
                                        List.of(
                                                new KeywordScore("security", 18),
                                                new KeywordScore("authentication", 12))),
                                new SectionKeywordEntry("Authorization Model", 10, 13,
                                        List.of(
                                                new KeywordScore("authorization", 10),
                                                new KeywordScore("security", 8)))),
                        "quarkus-core"),
                // quarkus-core: configuration-guide.adoc (8 lines)
                new FileKeywordEntry("configuration-guide.adoc",
                        List.of(
                                new KeywordScore("configuration", 18),
                                new KeywordScore("properties", 14)),
                        List.of(
                                new SectionKeywordEntry("Configuration Basics", 4, 7,
                                        List.of(
                                                new KeywordScore("configuration", 15),
                                                new KeywordScore("properties", 10)))),
                        "quarkus-core"),
                // quarkus-oidc: oidc-guide.adoc (11 lines)
                new FileKeywordEntry("oidc-guide.adoc",
                        List.of(
                                new KeywordScore("oidc", 20),
                                new KeywordScore("authentication", 16),
                                new KeywordScore("security", 14)),
                        List.of(
                                new SectionKeywordEntry("OIDC Authentication", 4, 7,
                                        List.of(
                                                new KeywordScore("oidc", 18),
                                                new KeywordScore("authentication", 14),
                                                new KeywordScore("security", 10))),
                                new SectionKeywordEntry("Token Validation", 9, 11,
                                        List.of(
                                                new KeywordScore("oidc", 12),
                                                new KeywordScore("security", 8)))),
                        "quarkus-oidc"),
                // quarkus-oidc: oidc-config.adoc (8 lines)
                new FileKeywordEntry("oidc-config.adoc",
                        List.of(
                                new KeywordScore("oidc", 15),
                                new KeywordScore("configuration", 12)),
                        List.of(
                                new SectionKeywordEntry("OIDC Configuration", 4, 7,
                                        List.of(
                                                new KeywordScore("oidc", 12),
                                                new KeywordScore("configuration", 10)))),
                        "quarkus-oidc")
        ));
        keywordIndexStore.write(VERSION, index);
    }

    private void seedCodeSampleIndex() {
        CodeSampleIndex codeSampleIndex = new CodeSampleIndex(List.of(
                // quarkus-core code samples
                new CodeSampleEntry("security-overview.adoc", "Security Fundamentals", "java",
                        "@RolesAllowed(\"admin\")\npublic class SecuredResource { }",
                        5, 10,
                        List.of(new KeywordScore("security", 15), new KeywordScore("authorization", 8)),
                        "quarkus-core"),
                new CodeSampleEntry("configuration-guide.adoc", "Configuration Basics", "properties",
                        "quarkus.http.port=8080\nquarkus.datasource.db-kind=postgresql",
                        3, 8,
                        List.of(new KeywordScore("configuration", 12), new KeywordScore("properties", 10)),
                        "quarkus-core"),
                // quarkus-oidc code samples
                new CodeSampleEntry("oidc-guide.adoc", "Authentication", "java",
                        "import io.quarkus.oidc.OidcTenantConfig;\n@Authenticated\npublic class OidcResource { }",
                        8, 15,
                        List.of(new KeywordScore("oidc", 18), new KeywordScore("authentication", 14)),
                        "quarkus-oidc"),
                new CodeSampleEntry("oidc-config.adoc", "OIDC Configuration", "properties",
                        "quarkus.oidc.auth-server-url=https://auth.example.com\nquarkus.oidc.client-id=app",
                        4, 9,
                        List.of(new KeywordScore("oidc", 12), new KeywordScore("configuration", 10)),
                        "quarkus-oidc")
        ));
        codeSampleIndexStore.write(VERSION, codeSampleIndex);
    }

    private void seedDocFiles() {
        docStore.write(VERSION, "security-overview.adoc", """
                = Security Overview
                Introduction to Quarkus security features.

                == Security Fundamentals
                This section covers the security fundamentals of Quarkus.
                Authentication and authorization are core concepts.
                Quarkus provides built-in security mechanisms.
                Security is a first-class concern in Quarkus.

                == Authorization Model
                Role-based access control is supported.
                Use annotations like @RolesAllowed for authorization.
                The authorization model integrates with security identity.
                """);

        docStore.write(VERSION, "configuration-guide.adoc", """
                = Configuration Guide
                How to configure your Quarkus application.

                == Configuration Basics
                Quarkus uses application.properties for configuration.
                Properties can be overridden via environment variables.
                Configuration profiles allow environment-specific settings.
                """);

        docStore.write(VERSION, "oidc-guide.adoc", """
                = OIDC Guide
                OpenID Connect integration for Quarkus.

                == OIDC Authentication
                Configure OIDC authentication for your application.
                Token-based authentication with OIDC providers.
                Security is handled through bearer tokens.

                == Token Validation
                Tokens are validated against the OIDC provider.
                Security policies enforce token expiration.
                """);

        docStore.write(VERSION, "oidc-config.adoc", """
                = OIDC Configuration
                Configuring OIDC for Quarkus applications.

                == OIDC Configuration
                Set the auth-server-url property for OIDC.
                Configure client-id and client-secret.
                OIDC configuration supports multi-tenancy.
                """);
    }

    /**
     * Custom Hamcrest matcher to assert a value is less than a given total.
     */
    private static org.hamcrest.Matcher<Integer> lessThanTotal(int total) {
        return org.hamcrest.Matchers.lessThan(total);
    }
}
