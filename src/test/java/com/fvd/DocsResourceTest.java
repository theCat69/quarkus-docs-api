package com.fvd;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.not;

@QuarkusTest
class DocsResourceTest {

    @Test
    void testHealthEndpoint() {
        given()
            .when().get("/api/health")
            .then()
                .statusCode(200)
                .body("status", is("UP"));
    }

    @Test
    void testIndexEndpointMissingVersion() {
        given()
            .when().get("/api/index")
            .then()
                .statusCode(400)
                .body("status", is(400))
                .body("message", not(emptyOrNullString()));
    }

    @Test
    void testIndexEndpointEmptyVersion() {
        given()
            .queryParam("version", "")
            .when().get("/api/index")
            .then()
                .statusCode(400);
    }

    @Test
    void testIndexEndpointInvalidVersionChars() {
        given()
            .queryParam("version", "../etc/passwd")
            .when().get("/api/index")
            .then()
                .statusCode(400);
    }

    @Test
    void testIndexEndpointValidVersion() {
        given()
            .queryParam("version", "3.21")
            .when().get("/api/index")
            .then()
                .statusCode(200);
    }

    @Test
    void testDocEndpointMissingVersion() {
        given()
            .queryParam("path", "docs/src/main/asciidoc/security-oidc.adoc")
            .when().get("/api/doc")
            .then()
                .statusCode(400);
    }

    @Test
    void testDocEndpointMissingPath() {
        given()
            .queryParam("version", "3.21")
            .when().get("/api/doc")
            .then()
                .statusCode(400);
    }

    @Test
    void testDocEndpointPathTraversal() {
        given()
            .queryParam("version", "3.21")
            .queryParam("path", "../../etc/passwd")
            .when().get("/api/doc")
            .then()
                .statusCode(400);
    }

    @Test
    void testDocEndpointNotFound() {
        given()
            .queryParam("version", "3.21")
            .queryParam("path", "docs/src/main/asciidoc/nonexistent.adoc")
            .when().get("/api/doc")
            .then()
                .statusCode(404)
                .body("status", is(404));
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
    void testSearchFilesEndpointValid() {
        given()
            .queryParam("version", "3.21")
            .queryParam("keywords", "oidc,security")
            .when().get("/api/search/files")
            .then()
                .statusCode(200)
                .body("results.size()", is(0));
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
    void testSearchSectionsEndpointValid() {
        given()
            .queryParam("version", "3.21")
            .queryParam("keywords", "oidc")
            .queryParam("filePaths", "docs/src/main/asciidoc/security-oidc.adoc")
            .when().get("/api/search/sections")
            .then()
                .statusCode(200)
                .body("results.size()", is(0));
    }
}
