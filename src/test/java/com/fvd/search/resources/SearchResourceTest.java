package com.fvd.search.resources;

import com.fvd.docs.stores.DocStore;
import com.fvd.indexs.stores.KeywordIndexStore;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class SearchResourceTest {

    @Inject
    KeywordIndexStore keywordIndexStore;

    @Inject
    DocStore docStore;

    @BeforeEach
    void cleanTestCache() throws IOException {
        var cachePath = Path.of("build/test-cache").toFile();
        if(cachePath.exists()) {
            FileUtils.cleanDirectory(cachePath);
        }
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
                .queryParam("version", "3.21")
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
        given()
                .queryParam("version", "3.21")
                .queryParam("keywords", "oidc")
                .when().get("/api/search/sections")
                .then()
                .statusCode(400);
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
                .queryParam("version", "3.21")
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
        String keywordIndexJson = """
                {
                  "files": [
                    {
                      "path": "security-overview.adoc",
                      "keywords": [
                        {"word": "security", "score": 15},
                        {"word": "quarkus", "score": 8}
                      ],
                      "sections": [
                        {
                          "title": "Security Overview",
                          "start": 1,
                          "end": 10,
                          "keywords": [
                            {"word": "security", "score": 12},
                            {"word": "overview", "score": 5}
                          ]
                        }
                      ]
                    },
                    {
                      "path": "config.adoc",
                      "keywords": [
                        {"word": "config", "score": 10},
                        {"word": "quarkus", "score": 5}
                      ],
                      "sections": []
                    }
                  ]
                }
                """;
        keywordIndexStore.write("3.21", keywordIndexJson);
    }
}