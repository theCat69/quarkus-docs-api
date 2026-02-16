package com.fvd.api.resources;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class MetaResourceTest extends AbstractApiResourceTest {

    @Test
    void testMetaEndpointReturns200() {
        given()
                .when().get("/api/meta")
                .then()
                .statusCode(200);
    }

    @Test
    void testMetaEndpointReturnsCacheControlHeader() {
        given()
                .when().get("/api/meta")
                .then()
                .statusCode(200)
                .header("Cache-Control", containsString("max-age=3600"));
    }

    @Test
    void testMetaEndpointReturnsAllEndpoints() {
        given()
                .when().get("/api/meta")
                .then()
                .statusCode(200)
                .body("endpoints.size()", equalTo(8));
    }

    @Test
    void testMetaEndpointContainsApiInfo() {
        given()
                .when().get("/api/meta")
                .then()
                .statusCode(200)
                .body("apiInfo.name", notNullValue())
                .body("apiInfo.defaultVersion", equalTo("main"));
    }

    @Test
    void testMetaEndpointContainsSearchSyntax() {
        given()
                .when().get("/api/meta")
                .then()
                .statusCode(200)
                .body("searchSyntax.supportedFeatures.size()", greaterThan(0))
                .body("searchSyntax.unsupportedFeatures.size()", greaterThan(0));
    }

    @Test
    void testMetaEndpointContainsSubjects() {
        given()
                .when().get("/api/meta")
                .then()
                .statusCode(200)
                .body("filters.subjects.size()", greaterThan(0))
                .body("filters.subjects", hasItem("security"));
    }

    @Test
    void testMetaEndpointContainsVersions() {
        docStore.write("main", "test.adoc", "= Test\nContent");
        given()
                .when().get("/api/meta")
                .then()
                .statusCode(200)
                .body("filters.versions", hasItem("main"));
    }

    @Test
    void testMetaEndpointContainsPagination() {
        given()
                .when().get("/api/meta")
                .then()
                .statusCode(200)
                .body("pagination.defaultLimit", equalTo(20))
                .body("pagination.maxLimit", equalTo(100));
    }

    @Test
    void testMetaEndpointContainsDocumentsEndpointWithBriefParam() {
        given()
                .when().get("/api/meta")
                .then()
                .statusCode(200)
                .body("endpoints.find { it.path == '/api/documents' }.parameters.name",
                        hasItem("brief"));
    }

    @Test
    void testMetaEndpointContainsCodeSamplesWithLanguageParam() {
        given()
                .when().get("/api/meta")
                .then()
                .statusCode(200)
                .body("endpoints.find { it.path == '/api/code-samples' }.parameters.name",
                        hasItem("language"));
    }
}
