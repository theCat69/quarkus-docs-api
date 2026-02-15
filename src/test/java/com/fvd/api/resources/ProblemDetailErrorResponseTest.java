package com.fvd.api.resources;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Tests verifying RFC 7807 Problem Details error responses.
 */
@QuarkusTest
class ProblemDetailErrorResponseTest extends AbstractApiResourceTest {

    @Test
    void testBadRequestReturnsProblemDetail() {
        given()
                .queryParam("version", "../etc/passwd")
                .when().get("/api/catalog")
                .then()
                .statusCode(400)
                .body("type", equalTo("about:blank"))
                .body("title", equalTo("Bad Request"))
                .body("status", equalTo(400))
                .body("detail", notNullValue())
                .body("instance", containsString("catalog"))
                .body("timestamp", notNullValue());
    }

    @Test
    void testNotFoundReturnsProblemDetail() {
        docStore.write("3.27", "existing.adoc", "= Existing\nContent");
        given()
                .queryParam("version", "3.27")
                .queryParam("path", "nonexistent.adoc")
                .when().get("/api/documents")
                .then()
                .statusCode(404)
                .body("type", equalTo("about:blank"))
                .body("title", equalTo("Not Found"))
                .body("status", equalTo(404))
                .body("detail", containsString("Document not found"))
                .body("instance", containsString("documents"))
                .body("timestamp", notNullValue());
    }

    @Test
    void testBadRequestForMissingKeywords() {
        given()
                .queryParam("version", "3.27")
                .when().get("/api/search")
                .then()
                .statusCode(400)
                .body("title", equalTo("Bad Request"))
                .body("status", equalTo(400))
                .body("instance", containsString("search"))
                .body("timestamp", notNullValue());
    }

    @Test
    void testBadRequestForInvalidPath() {
        docStore.write("3.27", "existing.adoc", "= Existing\nContent");
        given()
                .queryParam("version", "3.27")
                .queryParam("path", "../../etc/passwd")
                .when().get("/api/documents")
                .then()
                .statusCode(400)
                .body("title", equalTo("Bad Request"))
                .body("status", equalTo(400))
                .body("instance", containsString("documents"))
                .body("timestamp", notNullValue());
    }

    @Test
    void testBadRequestForCodeSamplesMissingKeywords() {
        given()
                .queryParam("version", "3.27")
                .when().get("/api/code-samples")
                .then()
                .statusCode(400)
                .body("title", equalTo("Bad Request"))
                .body("status", equalTo(400))
                .body("instance", containsString("code-samples"))
                .body("timestamp", notNullValue());
    }

    @Test
    void testBadRequestForDocumentsNeitherPathNorKeywords() {
        given()
                .queryParam("version", "3.27")
                .when().get("/api/documents")
                .then()
                .statusCode(400)
                .body("title", equalTo("Bad Request"))
                .body("status", equalTo(400))
                .body("detail", containsString("path"))
                .body("timestamp", notNullValue());
    }
}
