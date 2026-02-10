package com.fvd.search.resources;

import com.fvd.docs.stores.DocStore;
import com.fvd.indexs.indexers.*;
import com.fvd.indexs.stores.CodeSampleIndexStore;
import com.fvd.indexs.stores.KeywordIndexStore;
import com.fvd.indexs.stores.SqliteSchemaInitializer;
import com.fvd.search.services.SearchService;
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

    @Inject
    SearchService searchService;

    @BeforeEach
    void cleanTestCache() throws IOException {
        var cachePath = Path.of("build/test-cache").toFile();
        if (cachePath.exists()) {
            FileUtils.cleanDirectory(cachePath);
        }
        // Re-initialize schema after cleaning (DB file was deleted)
        schemaInitializer.resetSchema();
        // Invalidate in-memory caches to avoid cross-test pollution
        searchService.invalidateCache("3.27");
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
                .queryParam("version", "3.27")
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
                .body("results.size()", is(0))
                .body("total", is(0));
    }

    @Test
    void testSearchFilesEndpointReturnsResults() {
        seedKeywordIndex();
        given()
                .queryParam("version", "3.27")
                .queryParam("keywords", "security")
                .when().get("/api/search/files")
                .then()
                .statusCode(200)
                .body("results.size()", greaterThan(0))
                .body("results[0].path", equalTo("security-overview.adoc"))
                .body("results[0].score", greaterThan(0f))
                .body("total", greaterThan(0))
                .body("limit", is(10))
                .body("offset", is(0));
    }

    @Test
    void testSearchFilesEndpointReturnsResultsEvenIfOneKeywordDoesNotMatch() {
        seedKeywordIndex();
        given()
                .queryParam("version", "3.27")
                .queryParam("keywords", "oidc,security")
                .when().get("/api/search/files")
                .then()
                .statusCode(200)
                .body("results.size()", greaterThan(0))
                .body("results[0].path", equalTo("security-overview.adoc"))
                .body("results[0].score", greaterThan(0f));
    }

    @Test
    void testSearchFilesEndpointWithPagination() {
        seedKeywordIndex();
        given()
                .queryParam("version", "3.27")
                .queryParam("keywords", "quarkus")
                .queryParam("limit", 1)
                .queryParam("offset", 0)
                .when().get("/api/search/files")
                .then()
                .statusCode(200)
                .body("results.size()", is(1))
                .body("total", is(2))
                .body("limit", is(1))
                .body("offset", is(0));
    }

    @Test
    void testSearchFilesEndpointWithPaginationOffset() {
        seedKeywordIndex();
        given()
                .queryParam("version", "3.27")
                .queryParam("keywords", "quarkus")
                .queryParam("limit", 1)
                .queryParam("offset", 1)
                .when().get("/api/search/files")
                .then()
                .statusCode(200)
                .body("results.size()", is(1))
                .body("total", is(2))
                .body("limit", is(1))
                .body("offset", is(1));
    }

    @Test
    void testSearchFilesEndpointInvalidLimit() {
        given()
                .queryParam("version", "3.27")
                .queryParam("keywords", "security")
                .queryParam("limit", 0)
                .when().get("/api/search/files")
                .then()
                .statusCode(400);
    }

    @Test
    void testSearchFilesEndpointInvalidOffset() {
        given()
                .queryParam("version", "3.27")
                .queryParam("keywords", "security")
                .queryParam("offset", -1)
                .when().get("/api/search/files")
                .then()
                .statusCode(400);
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
                .queryParam("version", "3.27")
                .queryParam("filePaths", "docs/src/main/asciidoc/security-oidc.adoc")
                .when().get("/api/search/sections")
                .then()
                .statusCode(400);
    }

    @Test
    void testSearchSectionsEndpointMissingFilePaths() {
        seedKeywordIndex();
        given()
                .queryParam("version", "3.27")
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
                .queryParam("version", "3.27")
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
                .queryParam("version", "3.27")
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
                .queryParam("version", "3.27")
                .queryParam("keywords", "security")
                .queryParam("filePaths", "security-overview.adoc")
                .when().get("/api/search/sections")
                .then()
                .statusCode(200)
                .body("results.size()", greaterThan(0))
                .body("results[0].path", equalTo("security-overview.adoc"))
                .body("results[0].score", greaterThan(0f));
    }

    @Test
    void testSearchSectionsEndpointWithPagination() {
        seedKeywordIndex();
        given()
                .queryParam("version", "3.27")
                .queryParam("keywords", "security")
                .queryParam("filePaths", "security-overview.adoc")
                .queryParam("limit", 1)
                .queryParam("offset", 0)
                .when().get("/api/search/sections")
                .then()
                .statusCode(200)
                .body("results.size()", is(1))
                .body("total", greaterThanOrEqualTo(1))
                .body("limit", is(1))
                .body("offset", is(0));
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
                .queryParam("version", "3.27")
                .queryParam("sectionTitle", "Overview")
                .when().get("/api/search/section-content")
                .then()
                .statusCode(400);
    }

    @Test
    void testSectionContentEndpointMissingSectionTitle() {
        given()
                .queryParam("version", "3.27")
                .queryParam("filePath", "security.adoc")
                .when().get("/api/search/section-content")
                .then()
                .statusCode(400);
    }

    @Test
    void testSectionContentEndpointPathTraversal() {
        given()
                .queryParam("version", "3.27")
                .queryParam("filePath", "../../etc/passwd")
                .queryParam("sectionTitle", "Overview")
                .when().get("/api/search/section-content")
                .then()
                .statusCode(400);
    }

    @Test
    void testSectionContentEndpointDocNotFound() {
        given()
                .queryParam("version", "3.27")
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
                .queryParam("version", "3.27")
                .queryParam("filePath", "security.adoc")
                .queryParam("sectionTitle", "XYZ Completely Unrelated 12345")
                .when().get("/api/search/section-content")
                .then()
                .statusCode(404)
                .body("message", containsString("Section not found"));
    }

    @Test
    void testSectionContentEndpointReturnsContent() {
        seedDocFile();
        given()
                .queryParam("version", "3.27")
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
                .body("endLine", greaterThan(0))
                .body("matchedTitle", equalTo("Overview"))
                .body("matchScore", is(1.0f))
                .body("matchType", equalTo("exact"));
    }

    @Test
    void testSectionContentEndpointFuzzyMatchPartialTitle() {
        seedDocFile();
        given()
                .queryParam("version", "3.27")
                .queryParam("filePath", "security.adoc")
                .queryParam("sectionTitle", "Over")
                .when().get("/api/search/section-content")
                .then()
                .statusCode(200)
                .body("title", equalTo("Overview"))
                .body("matchedTitle", equalTo("Overview"))
                .body("matchScore", greaterThan(0.0f))
                .body("matchScore", lessThan(1.0f))
                .body("content", containsString("This is the overview section."));
    }

    @Test
    void testSectionContentEndpointFuzzyMatchTypo() {
        seedDocFile();
        given()
                .queryParam("version", "3.27")
                .queryParam("filePath", "security.adoc")
                .queryParam("sectionTitle", "Overvew")
                .when().get("/api/search/section-content")
                .then()
                .statusCode(200)
                .body("title", equalTo("Overview"))
                .body("matchedTitle", equalTo("Overview"))
                .body("matchScore", greaterThan(0.0f))
                .body("matchScore", lessThan(1.0f));
    }

    // --- Content search endpoint tests ---

    @Test
    void testSearchContentEndpointMissingVersion() {
        given()
                .queryParam("keywords", "security")
                .when().get("/api/search/content")
                .then()
                .statusCode(400);
    }

    @Test
    void testSearchContentEndpointMissingKeywords() {
        given()
                .queryParam("version", "3.27")
                .when().get("/api/search/content")
                .then()
                .statusCode(400);
    }

    @Test
    void testSearchContentEndpointNoDocsReturnsEmpty() {
        given()
                .queryParam("version", "3.99")
                .queryParam("keywords", "nonexistent")
                .when().get("/api/search/content")
                .then()
                .statusCode(200)
                .body("results.size()", is(0))
                .body("total", is(0));
    }

    @Test
    void testSearchContentEndpointReturnsResults() {
        seedDocFile();
        given()
                .queryParam("version", "3.27")
                .queryParam("keywords", "security")
                .when().get("/api/search/content")
                .then()
                .statusCode(200)
                .body("results.size()", greaterThan(0))
                .body("results[0].path", equalTo("security.adoc"))
                .body("results[0].snippet", notNullValue())
                .body("results[0].matchOffset", greaterThanOrEqualTo(0))
                .body("results[0].matchLine", greaterThanOrEqualTo(1))
                .body("results[0].score", greaterThan(0.0f));
    }

    @Test
    void testSearchContentEndpointWithPagination() {
        seedDocFile();
        docStore.write("3.27", "config.adoc", "= Config Guide\nSecurity config for quarkus.\n");
        given()
                .queryParam("version", "3.27")
                .queryParam("keywords", "security")
                .queryParam("limit", 1)
                .queryParam("offset", 0)
                .when().get("/api/search/content")
                .then()
                .statusCode(200)
                .body("results.size()", is(1))
                .body("total", is(2))
                .body("limit", is(1))
                .body("offset", is(0));
    }

    @Test
    void testSearchContentEndpointWithPaginationOffset() {
        seedDocFile();
        docStore.write("3.27", "config.adoc", "= Config Guide\nSecurity config for quarkus.\n");
        given()
                .queryParam("version", "3.27")
                .queryParam("keywords", "security")
                .queryParam("limit", 1)
                .queryParam("offset", 1)
                .when().get("/api/search/content")
                .then()
                .statusCode(200)
                .body("results.size()", is(1))
                .body("total", is(2))
                .body("limit", is(1))
                .body("offset", is(1));
    }

    @Test
    void testSearchContentEndpointWithFilePathsFilter() {
        seedDocFile();
        docStore.write("3.27", "config.adoc", "= Config Guide\nSecurity config for quarkus.\n");
        given()
                .queryParam("version", "3.27")
                .queryParam("keywords", "security")
                .queryParam("filePaths", "security.adoc")
                .when().get("/api/search/content")
                .then()
                .statusCode(200)
                .body("results.size()", is(1))
                .body("results[0].path", equalTo("security.adoc"));
    }

    @Test
    void testSearchContentEndpointFilePathsTraversal() {
        given()
                .queryParam("version", "3.27")
                .queryParam("keywords", "security")
                .queryParam("filePaths", "../../etc/passwd")
                .when().get("/api/search/content")
                .then()
                .statusCode(400);
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
        docStore.write("3.27", "security.adoc", "= Guide\nContent.");
        docStore.write("3.17", "config.adoc", "= Config\nContent.");

        given()
                .when().get("/api/search/versions")
                .then()
                .statusCode(200)
                .body("results.size()", is(2))
                .body("results", hasItems("3.27", "3.17"));
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
                .queryParam("version", "3.27")
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
                .queryParam("version", "3.27")
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
                .queryParam("version", "3.27")
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
                .queryParam("version", "3.27")
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
                .queryParam("version", "3.27")
                .queryParam("keywords", "security")
                .queryParam("filePath", "../../etc/passwd")
                .when().get("/api/search/code-samples")
                .then()
                .statusCode(400);
    }

    @Test
    void testSearchCodeSamplesEndpointWithPagination() {
        seedCodeSampleIndex();
        given()
                .queryParam("version", "3.27")
                .queryParam("keywords", "security")
                .queryParam("limit", 1)
                .queryParam("offset", 0)
                .when().get("/api/search/code-samples")
                .then()
                .statusCode(200)
                .body("results.size()", is(1))
                .body("total", is(2))
                .body("limit", is(1))
                .body("offset", is(0));
    }

    @Test
    void testSearchCodeSamplesEndpointWithPaginationOffset() {
        seedCodeSampleIndex();
        given()
                .queryParam("version", "3.27")
                .queryParam("keywords", "security")
                .queryParam("limit", 1)
                .queryParam("offset", 1)
                .when().get("/api/search/code-samples")
                .then()
                .statusCode(200)
                .body("results.size()", is(1))
                .body("total", is(2))
                .body("limit", is(1))
                .body("offset", is(1));
    }

    @Test
    void testSearchCodeSamplesEndpointFuzzySectionTitleMatch() {
        seedCodeSampleIndex();
        given()
                .queryParam("version", "3.27")
                .queryParam("keywords", "security")
                .queryParam("sectionTitle", "Authenticat")
                .when().get("/api/search/code-samples")
                .then()
                .statusCode(200)
                .body("results.size()", greaterThan(0))
                .body("results[0].matchedSectionTitle", notNullValue())
                .body("results[0].sectionMatchScore", greaterThan(0f));
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
        docStore.write("3.27", "security.adoc", docContent);
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
        keywordIndexStore.write("3.27", index);
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
        codeSampleIndexStore.write("3.27", codeSampleIndex);
    }
}
