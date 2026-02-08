package com.fvd.search.resources;

import com.fvd.docs.stores.DocStore;
import com.fvd.indexs.indexers.*;
import com.fvd.indexs.stores.CodeSampleIndexStore;
import com.fvd.indexs.stores.KeywordIndexStore;
import com.fvd.indexs.stores.SqliteSchemaInitializer;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class SearchResourceTest {

    @Inject
    KeywordIndexStore keywordIndexStore;

    @Inject
    CodeSampleIndexStore codeSampleIndexStore;

    @Inject
    DocStore docStore;

    @Inject
    SqliteSchemaInitializer schemaInitializer;

    @BeforeEach
    void cleanTestCache() throws IOException {
        var cachePath = Path.of("build/test-cache").toFile();
        if (cachePath.exists()) {
            FileUtils.cleanDirectory(cachePath);
        }
        // Re-initialize schema after cleaning (DB file was deleted)
        schemaInitializer.initSchema();
    }

    @Test
    void testSearchFilesEndpointMissingVersion() {
        given()
                .queryParam("keywords", "oidc")
                .when().get("/api/search/files")
                .then()
                .statusCode(400);
    }

    @Test
    void testSearchFilesEndpointMissingKeywords() {
        given()
                .queryParam("version", "3.21")
                .when().get("/api/search/files")
                .then()
                .statusCode(400);
    }

    @Test
    void testSearchFilesEndpointNoIndexReturnsEmpty() {
        given()
                .queryParam("version", "3.99")
                .queryParam("keywords", "oidc,security")
                .when().get("/api/search/files")
                .then()
                .statusCode(200)
                .body("results.size()", is(0));
    }

    @Test
    void testSearchFilesEndpointReturnsResults() {
        seedKeywordIndex();
        given()
                .queryParam("version", "3.21")
                .queryParam("keywords", "security")
                .when().get("/api/search/files")
                .then()
                .statusCode(200)
                .body("results.size()", greaterThan(0))
                .body("results[0].path", equalTo("security-overview.adoc"))
                .body("results[0].score", greaterThan(0f));
    }

    @Test
    void testSearchFilesEndpointReturnsResultsEvenIfOneKeywordDoesNotMatch() {
        seedKeywordIndex();
        given()
                .queryParam("version", "3.21")
                .queryParam("keywords", "oidc,security")
                .when().get("/api/search/files")
                .then()
                .statusCode(200)
                .body("results.size()", greaterThan(0))
                .body("results[0].path", equalTo("security-overview.adoc"))
                .body("results[0].score", greaterThan(0f));
    }

    @Test
    void testSearchSectionsEndpointMissingVersion() {
        given()
                .queryParam("keywords", "oidc")
                .queryParam("filePaths", "docs/src/main/asciidoc/security-oidc.adoc")
                .when().get("/api/search/sections")
                .then()
                .statusCode(400);
    }

    @Test
    void testSearchSectionsEndpointMissingKeywords() {
        given()
                .queryParam("version", "3.21")
                .queryParam("filePaths", "docs/src/main/asciidoc/security-oidc.adoc")
                .when().get("/api/search/sections")
                .then()
                .statusCode(400);
    }

    @Test
    void testSearchSectionsEndpointMissingFilePaths() {
        seedKeywordIndex();
        given()
                .queryParam("version", "3.21")
                .queryParam("keywords", "security")
                .when().get("/api/search/sections")
                .then()
                .statusCode(200)
                .body("results.size()", greaterThan(0));
    }

    @Test
    void testSearchSectionsEndpointWithFilePaths() {
        seedKeywordIndex();
        given()
                .queryParam("version", "3.21")
                .queryParam("keywords", "security")
                .queryParam("filePaths", "security-overview.adoc")
                .when().get("/api/search/sections")
                .then()
                .statusCode(200)
                .body("results.size()", greaterThan(0))
                .body("results[0].path", equalTo("security-overview.adoc"));
    }

    @Test
    void testSearchSectionsEndpointFilePathsTraversal() {
        given()
                .queryParam("version", "3.21")
                .queryParam("keywords", "oidc")
                .queryParam("filePaths", "../../etc/passwd")
                .when().get("/api/search/sections")
                .then()
                .statusCode(400);
    }

    @Test
    void testSearchSectionsEndpointNoIndexReturnsEmpty() {
        given()
                .queryParam("version", "3.99")
                .queryParam("keywords", "oidc")
                .queryParam("filePaths", "docs/src/main/asciidoc/security-oidc.adoc")
                .when().get("/api/search/sections")
                .then()
                .statusCode(200)
                .body("results.size()", is(0));
    }

    @Test
    void testSearchSectionsEndpointReturnsResults() {
        seedKeywordIndex();
        given()
                .queryParam("version", "3.21")
                .queryParam("keywords", "security")
                .queryParam("filePaths", "security-overview.adoc")
                .when().get("/api/search/sections")
                .then()
                .statusCode(200)
                .body("results.size()", greaterThan(0))
                .body("results[0].path", equalTo("security-overview.adoc"))
                .body("results[0].score", greaterThan(0f));
    }

    // --- Section content endpoint tests ---

    @Test
    void testSectionContentEndpointMissingVersion() {
        given()
                .queryParam("filePath", "security.adoc")
                .queryParam("sectionTitle", "Overview")
                .when().get("/api/search/section-content")
                .then()
                .statusCode(400);
    }

    @Test
    void testSectionContentEndpointMissingFilePath() {
        given()
                .queryParam("version", "3.21")
                .queryParam("sectionTitle", "Overview")
                .when().get("/api/search/section-content")
                .then()
                .statusCode(400);
    }

    @Test
    void testSectionContentEndpointMissingSectionTitle() {
        given()
                .queryParam("version", "3.21")
                .queryParam("filePath", "security.adoc")
                .when().get("/api/search/section-content")
                .then()
                .statusCode(400);
    }

    @Test
    void testSectionContentEndpointPathTraversal() {
        given()
                .queryParam("version", "3.21")
                .queryParam("filePath", "../../etc/passwd")
                .queryParam("sectionTitle", "Overview")
                .when().get("/api/search/section-content")
                .then()
                .statusCode(400);
    }

    @Test
    void testSectionContentEndpointDocNotFound() {
        given()
                .queryParam("version", "3.21")
                .queryParam("filePath", "nonexistent.adoc")
                .queryParam("sectionTitle", "Overview")
                .when().get("/api/search/section-content")
                .then()
                .statusCode(404)
                .body("message", containsString("Document not found"));
    }

    @Test
    void testSectionContentEndpointSectionNotFound() {
        seedDocFile();
        given()
                .queryParam("version", "3.21")
                .queryParam("filePath", "security.adoc")
                .queryParam("sectionTitle", "Nonexistent Section")
                .when().get("/api/search/section-content")
                .then()
                .statusCode(404)
                .body("message", containsString("Section not found"));
    }

    @Test
    void testSectionContentEndpointReturnsContent() {
        seedDocFile();
        given()
                .queryParam("version", "3.21")
                .queryParam("filePath", "security.adoc")
                .queryParam("sectionTitle", "Overview")
                .when().get("/api/search/section-content")
                .then()
                .statusCode(200)
                .body("path", equalTo("security.adoc"))
                .body("title", equalTo("Overview"))
                .body("content", notNullValue())
                .body("content", containsString("This is the overview section."))
                .body("startLine", greaterThan(0))
                .body("endLine", greaterThan(0));
    }

    // --- Versions endpoint tests ---

    @Test
    void testVersionsEndpointReturnsEmptyWhenNoVersionsCached() {
        given()
                .when().get("/api/search/versions")
                .then()
                .statusCode(200)
                .body("results.size()", is(0));
    }

    @Test
    void testVersionsEndpointReturnsCachedVersions() {
        // Seed a doc file to create a cached version directory
        docStore.write("3.21", "security.adoc", "= Guide\nContent.");
        docStore.write("3.17", "config.adoc", "= Config\nContent.");

        given()
                .when().get("/api/search/versions")
                .then()
                .statusCode(200)
                .body("results.size()", is(2))
                .body("results", hasItems("3.21", "3.17"));
    }

    // --- Code sample search endpoint tests ---

    @Test
    void testSearchCodeSamplesEndpointMissingVersion() {
        given()
                .queryParam("keywords", "security")
                .when().get("/api/search/code-samples")
                .then()
                .statusCode(400);
    }

    @Test
    void testSearchCodeSamplesEndpointMissingKeywords() {
        given()
                .queryParam("version", "3.21")
                .when().get("/api/search/code-samples")
                .then()
                .statusCode(400);
    }

    @Test
    void testSearchCodeSamplesEndpointNoIndexReturnsEmpty() {
        given()
                .queryParam("version", "3.99")
                .queryParam("keywords", "security")
                .when().get("/api/search/code-samples")
                .then()
                .statusCode(200)
                .body("results.size()", is(0));
    }

    @Test
    void testSearchCodeSamplesEndpointReturnsResults() {
        seedCodeSampleIndex();
        given()
                .queryParam("version", "3.21")
                .queryParam("keywords", "security")
                .when().get("/api/search/code-samples")
                .then()
                .statusCode(200)
                .body("results.size()", greaterThan(0))
                .body("results[0].path", equalTo("security.adoc"))
                .body("results[0].sectionTitle", equalTo("Authentication"))
                .body("results[0].language", equalTo("java"))
                .body("results[0].content", notNullValue())
                .body("results[0].score", greaterThan(0f));
    }

    @Test
    void testSearchCodeSamplesEndpointFiltersToFilePath() {
        seedCodeSampleIndex();
        given()
                .queryParam("version", "3.21")
                .queryParam("keywords", "security")
                .queryParam("filePath", "security.adoc")
                .when().get("/api/search/code-samples")
                .then()
                .statusCode(200)
                .body("results.size()", is(1))
                .body("results[0].path", equalTo("security.adoc"));
    }

    @Test
    void testSearchCodeSamplesEndpointFiltersToSectionTitle() {
        seedCodeSampleIndex();
        given()
                .queryParam("version", "3.21")
                .queryParam("keywords", "security")
                .queryParam("sectionTitle", "Authorization")
                .when().get("/api/search/code-samples")
                .then()
                .statusCode(200)
                .body("results.size()", is(1))
                .body("results[0].sectionTitle", equalTo("Authorization"));
    }

    @Test
    void testSearchCodeSamplesEndpointPathTraversal() {
        given()
                .queryParam("version", "3.21")
                .queryParam("keywords", "security")
                .queryParam("filePath", "../../etc/passwd")
                .when().get("/api/search/code-samples")
                .then()
                .statusCode(400);
    }

    private void seedDocFile() {
        String docContent = """
                = Security Guide
                Introduction text.
                
                == Overview
                This is the overview section.
                It covers security basics.
                
                == Configuration
                Config details here.
                """;
        docStore.write("3.21", "security.adoc", docContent);
    }

    private void seedKeywordIndex() {
        KeywordIndex index = new KeywordIndex(List.of(
                new FileKeywordEntry("security-overview.adoc",
                        List.of(new KeywordScore("security", 15), new KeywordScore("quarkus", 8)),
                        List.of(new SectionKeywordEntry("Security Overview", 1, 10,
                                List.of(new KeywordScore("security", 12), new KeywordScore("overview", 5))))),
                new FileKeywordEntry("config.adoc",
                        List.of(new KeywordScore("config", 10), new KeywordScore("quarkus", 5)),
                        List.of())
        ));
        keywordIndexStore.write("3.21", index);
    }

    private void seedCodeSampleIndex() {
        CodeSampleIndex codeSampleIndex = new CodeSampleIndex(List.of(
                new CodeSampleEntry("security.adoc", "Authentication", "java",
                        "import io.quarkus.security.identity.SecurityIdentity;",
                        5, 10,
                        List.of(new KeywordScore("security", 15), new KeywordScore("identity", 8))),
                new CodeSampleEntry("config.adoc", "Authorization", "java",
                        "@RolesAllowed(\"admin\")",
                        20, 25,
                        List.of(new KeywordScore("security", 10), new KeywordScore("roles", 5)))
        ));
        codeSampleIndexStore.write("3.21", codeSampleIndex);
    }
}
