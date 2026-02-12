package com.fvd.api.resources;

import com.fvd.docs.stores.DocStore;
import com.fvd.indexs.indexers.FileKeywordEntry;
import com.fvd.indexs.indexers.KeywordIndex;
import com.fvd.indexs.indexers.KeywordScore;
import com.fvd.indexs.indexers.SectionKeywordEntry;
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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class ApiSearchResourceTest {

    @Inject
    KeywordIndexStore keywordIndexStore;

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
        searchService.invalidateCache("3.27");
        searchService.invalidateCache("main");
    }

    @Test
    void testSearchMissingKeywords() {
        given()
                .queryParam("version", "3.27")
                .when().get("/api/search")
                .then()
                .statusCode(400);
    }

    @Test
    void testSearchNoResults() {
        given()
                .queryParam("version", "3.27")
                .queryParam("keywords", "nonexistent")
                .when().get("/api/search")
                .then()
                .statusCode(200)
                .body("results.size()", is(0))
                .body("totalCount", is(0));
    }

    @Test
    void testSearchReturnsResults() {
        seedDocFile();
        seedKeywordIndex();
        given()
                .queryParam("version", "3.27")
                .queryParam("keywords", "security")
                .when().get("/api/search")
                .then()
                .statusCode(200)
                .body("results.size()", greaterThan(0))
                .body("totalCount", greaterThan(0))
                .body("returnedCount", greaterThan(0));
    }

    @Test
    void testSearchResultStructure() {
        seedDocFile();
        seedKeywordIndex();
        given()
                .queryParam("version", "3.27")
                .queryParam("keywords", "security")
                .when().get("/api/search")
                .then()
                .statusCode(200)
                .body("results[0].path", notNullValue())
                .body("results[0].title", notNullValue())
                .body("results[0].subject", notNullValue())
                .body("results[0].score", greaterThan(0f))
                .body("results[0].matchedKeywords", notNullValue())
                .body("results[0].snippet", notNullValue());
    }

    @Test
    void testSearchWithExtensionFilter() {
        seedDocFilesMultiple();
        seedKeywordIndexWithExtensions();
        given()
                .queryParam("version", "3.27")
                .queryParam("keywords", "security")
                .queryParam("extension", "quarkus-core")
                .when().get("/api/search")
                .then()
                .statusCode(200)
                .body("results.size()", is(1))
                .body("results[0].extension", equalTo("quarkus-core"));
    }

    @Test
    void testSearchWithPagination() {
        seedDocFilesMultiple();
        seedKeywordIndexMultiple();
        given()
                .queryParam("version", "3.27")
                .queryParam("keywords", "quarkus")
                .queryParam("limit", 1)
                .queryParam("offset", 0)
                .when().get("/api/search")
                .then()
                .statusCode(200)
                .body("results.size()", is(1))
                .body("totalCount", equalTo(2));
    }

    @Test
    void testSearchWithOffset() {
        seedDocFilesMultiple();
        seedKeywordIndexMultiple();
        given()
                .queryParam("version", "3.27")
                .queryParam("keywords", "quarkus")
                .queryParam("limit", 1)
                .queryParam("offset", 1)
                .when().get("/api/search")
                .then()
                .statusCode(200)
                .body("results.size()", is(1))
                .body("totalCount", equalTo(2));
    }

    @Test
    void testSearchSnippetContainsContext() {
        seedDocFileWithKeyword();
        seedKeywordIndexForSnippet();
        given()
                .queryParam("version", "3.27")
                .queryParam("keywords", "security")
                .when().get("/api/search")
                .then()
                .statusCode(200)
                .body("results[0].snippet", notNullValue())
                .body("results[0].snippet", containsString("security"));
    }

    @Test
    void testSearchSortedByScore() {
        seedDocFilesMultiple();
        seedKeywordIndexWithScores();
        given()
                .queryParam("version", "3.27")
                .queryParam("keywords", "security")
                .when().get("/api/search")
                .then()
                .statusCode(200)
                .body("results.size()", greaterThan(0))
                .body("results[0].score", notNullValue());
    }

    @Test
    void testSearchDefaultVersion() {
        seedDocFileForMain();
        seedKeywordIndexForMain();
        given()
                .queryParam("keywords", "security")
                .when().get("/api/search")
                .then()
                .statusCode(200)
                .body("results.size()", greaterThan(0));
    }

    private void seedDocFile() {
        docStore.write("3.27", "security-overview.adoc", """
                = Security Overview
                
                This guide covers security features including authentication and authorization.
                """);
    }

    private void seedDocFileWithKeyword() {
        docStore.write("3.27", "security.adoc", """
                = Security Guide
                
                This is content about security features for your Quarkus application.
                Learn about security best practices and implementation patterns.
                """);
    }

    private void seedDocFilesMultiple() {
        docStore.write("3.27", "security.adoc", "= Security\nContent about security and quarkus.");
        docStore.write("3.27", "config.adoc", "= Config\nContent about config and quarkus.");
    }

    private void seedDocFileForMain() {
        docStore.write("main", "security.adoc", "= Security\nContent about security.");
    }

    private void seedKeywordIndex() {
        KeywordIndex index = new KeywordIndex(List.of(
                new FileKeywordEntry("security-overview.adoc",
                        List.of(new KeywordScore("security", 15)),
                        List.of(new SectionKeywordEntry("Overview", 1, 5,
                                List.of(new KeywordScore("security", 12)))))
        ));
        keywordIndexStore.write("3.27", index);
    }

    private void seedKeywordIndexForSnippet() {
        KeywordIndex index = new KeywordIndex(List.of(
                new FileKeywordEntry("security.adoc",
                        List.of(new KeywordScore("security", 15)),
                        List.of())
        ));
        keywordIndexStore.write("3.27", index);
    }

    private void seedKeywordIndexMultiple() {
        KeywordIndex index = new KeywordIndex(List.of(
                new FileKeywordEntry("security.adoc",
                        List.of(new KeywordScore("security", 15), new KeywordScore("quarkus", 8)),
                        List.of()),
                new FileKeywordEntry("config.adoc",
                        List.of(new KeywordScore("config", 10), new KeywordScore("quarkus", 5)),
                        List.of())
        ));
        keywordIndexStore.write("3.27", index);
    }

    private void seedKeywordIndexWithExtensions() {
        KeywordIndex index = new KeywordIndex(List.of(
                new FileKeywordEntry("security.adoc",
                        List.of(new KeywordScore("security", 15)),
                        List.of(),
                        "quarkus-core"),
                new FileKeywordEntry("config.adoc",
                        List.of(new KeywordScore("security", 10)),
                        List.of(),
                        "quarkus-openapi-generator")
        ));
        keywordIndexStore.write("3.27", index);
    }

    private void seedKeywordIndexWithScores() {
        KeywordIndex index = new KeywordIndex(List.of(
                new FileKeywordEntry("security.adoc",
                        List.of(new KeywordScore("security", 100)),
                        List.of()),
                new FileKeywordEntry("config.adoc",
                        List.of(new KeywordScore("security", 1)),
                        List.of())
        ));
        keywordIndexStore.write("3.27", index);
    }

    private void seedKeywordIndexForMain() {
        KeywordIndex index = new KeywordIndex(List.of(
                new FileKeywordEntry("security.adoc",
                        List.of(new KeywordScore("security", 15)),
                        List.of())
        ));
        keywordIndexStore.write("main", index);
    }
}
