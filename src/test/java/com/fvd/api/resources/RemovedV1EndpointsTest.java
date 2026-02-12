package com.fvd.api.resources;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

/**
 * Tests verifying that old v1 endpoints have been removed and return 404.
 */
@QuarkusTest
class RemovedV1EndpointsTest {

    @Test
    void testOldDocEndpointReturns404() {
        given()
                .queryParam("version", "3.27")
                .queryParam("path", "security.adoc")
                .when().get("/api/doc")
                .then()
                .statusCode(404);
    }

    @Test
    void testOldSearchVersionsEndpointReturns404() {
        given()
                .when().get("/api/search/versions")
                .then()
                .statusCode(404);
    }

    @Test
    void testOldSearchFilesEndpointReturns404() {
        given()
                .queryParam("version", "3.27")
                .queryParam("keywords", "security")
                .when().get("/api/search/files")
                .then()
                .statusCode(404);
    }

    @Test
    void testOldSearchSectionsEndpointReturns404() {
        given()
                .queryParam("version", "3.27")
                .queryParam("keywords", "security")
                .when().get("/api/search/sections")
                .then()
                .statusCode(404);
    }

    @Test
    void testOldSearchSectionContentEndpointReturns404() {
        given()
                .queryParam("version", "3.27")
                .queryParam("filePath", "security.adoc")
                .queryParam("sectionTitle", "Overview")
                .when().get("/api/search/section-content")
                .then()
                .statusCode(404);
    }

    @Test
    void testOldSearchCodeSamplesEndpointReturns404() {
        given()
                .queryParam("version", "3.27")
                .queryParam("keywords", "security")
                .when().get("/api/search/code-samples")
                .then()
                .statusCode(404);
    }

    @Test
    void testOldIndexEndpointReturns404() {
        given()
                .queryParam("version", "3.27")
                .when().get("/api/index")
                .then()
                .statusCode(404);
    }
}
