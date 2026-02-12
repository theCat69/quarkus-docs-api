package com.fvd.api.resources;

import com.fvd.api.services.CatalogService;
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
import jakarta.inject.Inject;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class CatalogResourceTest {

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

    @Inject
    CatalogService catalogService;

    @BeforeEach
    void cleanTestCache() throws IOException {
        var cachePath = Path.of("build/test-cache").toFile();
        if (cachePath.exists()) {
            FileUtils.cleanDirectory(cachePath);
        }
        schemaInitializer.resetSchema();
        searchService.invalidateCache("3.27");
        searchService.invalidateCache("main");
        catalogService.invalidateCache("3.27");
        catalogService.invalidateCache("main");
    }

    @Test
    void testCatalogEndpointDefaultVersion() {
        given()
                .when().get("/api/catalog")
                .then()
                .statusCode(200)
                .body("subjects", notNullValue())
                .body("extensions", notNullValue())
                .body("versions", notNullValue());
    }

    @Test
    void testCatalogEndpointWithVersion() {
        seedKeywordIndex();
        given()
                .queryParam("version", "3.27")
                .when().get("/api/catalog")
                .then()
                .statusCode(200)
                .body("subjects", notNullValue())
                .body("extensions", notNullValue())
                .body("versions", notNullValue());
    }

    @Test
    void testCatalogEndpointReturnsSubjects() {
        seedKeywordIndex();
        given()
                .queryParam("version", "3.27")
                .when().get("/api/catalog")
                .then()
                .statusCode(200)
                .body("subjects.size()", greaterThan(0))
                .body("subjects[0].name", notNullValue())
                .body("subjects[0].displayName", notNullValue());
    }

    @Test
    void testCatalogEndpointReturnsExtensions() {
        seedKeywordIndexWithExtensions();
        given()
                .queryParam("version", "3.27")
                .when().get("/api/catalog")
                .then()
                .statusCode(200)
                .body("extensions.size()", greaterThan(0))
                .body("extensions[0].name", notNullValue())
                .body("extensions[0].docCount", greaterThan(0));
    }

    @Test
    void testCatalogEndpointReturnsVersions() {
        docStore.write("3.27", "test.adoc", "= Test\nContent");
        docStore.write("main", "test.adoc", "= Test\nContent");
        
        given()
                .queryParam("version", "3.27")
                .when().get("/api/catalog")
                .then()
                .statusCode(200)
                .body("versions", hasItem("3.27"))
                .body("versions", hasItem("main"));
    }

    @Test
    void testCatalogEndpointInvalidVersion() {
        given()
                .queryParam("version", "../etc/passwd")
                .when().get("/api/catalog")
                .then()
                .statusCode(400);
    }

    private void seedKeywordIndex() {
        KeywordIndex index = new KeywordIndex(List.of(
                new FileKeywordEntry("security-overview.adoc",
                        List.of(new KeywordScore("security", 15)),
                        List.of(new SectionKeywordEntry("Security Overview", 1, 10,
                                List.of(new KeywordScore("security", 12)))))
        ));
        keywordIndexStore.write("3.27", index);
    }

    private void seedKeywordIndexWithExtensions() {
        KeywordIndex index = new KeywordIndex(List.of(
                new FileKeywordEntry("core-security.adoc",
                        List.of(new KeywordScore("security", 15)),
                        List.of(new SectionKeywordEntry("Core Security", 1, 10,
                                List.of(new KeywordScore("security", 12)))),
                        "quarkus-core"),
                new FileKeywordEntry("ext-security.adoc",
                        List.of(new KeywordScore("security", 10)),
                        List.of(new SectionKeywordEntry("Ext Security", 1, 10,
                                List.of(new KeywordScore("security", 8)))),
                        "quarkus-openapi-generator")
        ));
        keywordIndexStore.write("3.27", index);
    }
}
