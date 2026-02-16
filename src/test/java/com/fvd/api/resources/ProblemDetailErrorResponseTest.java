package com.fvd.api.resources;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
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

    @Test
    void testNonNumericLimitReturnsBadRequest() {
        given()
                .queryParam("keywords", "rest")
                .queryParam("limit", "abc")
                .when().get("/api/search")
                .then()
                .statusCode(400)
                .body("type", equalTo("about:blank"))
                .body("title", equalTo("Bad Request"))
                .body("status", equalTo(400))
                .body("detail", notNullValue())
                .body("instance", containsString("search"))
                .body("timestamp", notNullValue());
    }

    @Test
    void testNonNumericOffsetReturnsBadRequest() {
        given()
                .queryParam("keywords", "rest")
                .queryParam("offset", "xyz")
                .when().get("/api/search")
                .then()
                .statusCode(400)
                .body("title", equalTo("Bad Request"))
                .body("status", equalTo(400));
    }

    @Test
    void testUnsupportedMediaTypeReturnsNotAcceptable() {
        given()
                .accept("application/xml")
                .queryParam("keywords", "rest")
                .when().get("/api/search")
                .then()
                .statusCode(406)
                .body("type", equalTo("about:blank"))
                .body("title", equalTo("Not Acceptable"))
                .body("status", equalTo(406))
                .body("detail", containsString("application/json"))
                .body("instance", containsString("search"))
                .body("timestamp", notNullValue());
    }

    @Test
    void testAcceptApplicationXmlOnCatalogReturnsNotAcceptable() {
        given()
                .accept("application/xml")
                .when().get("/api/catalog")
                .then()
                .statusCode(406)
                .body("title", equalTo("Not Acceptable"))
                .body("status", equalTo(406));
    }

    @Test
    void testDeleteOnGetOnlyEndpointReturnsMethodNotAllowed() {
        given()
                .when().delete("/api/documents")
                .then()
                .statusCode(405)
                .body("title", equalTo("Method Not Allowed"))
                .body("status", equalTo(405));
    }

    @Test
    void testMalformedJsonBodyReturnsBadRequest() {
        given()
                .contentType(ContentType.JSON)
                .body("not json")
                .when()
                .post("/api/documents/batch")
                .then()
                .statusCode(400)
                .body("type", equalTo("about:blank"))
                .body("title", equalTo("Bad Request"))
                .body("status", equalTo(400))
                .body("detail", equalTo("Invalid JSON request body"))
                .body("instance", containsString("documents/batch"))
                .body("timestamp", notNullValue());
    }

    @Test
    void testTruncatedJsonBodyReturnsBadRequest() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"paths\":")
                .when()
                .post("/api/documents/batch")
                .then()
                .statusCode(400)
                .body("title", equalTo("Bad Request"))
                .body("status", equalTo(400))
                .body("detail", equalTo("Invalid JSON request body"));
    }

    @Test
    void testInvalidJsonSyntaxReturnsBadRequest() {
        given()
                .contentType(ContentType.JSON)
                .body("{invalid}")
                .when()
                .post("/api/documents/batch")
                .then()
                .statusCode(400)
                .body("title", equalTo("Bad Request"))
                .body("status", equalTo(400))
                .body("detail", equalTo("Invalid JSON request body"));
    }

    @Test
    void testEmptyBodyReturnsBadRequest() {
        given()
                .contentType(ContentType.JSON)
                .body("")
                .when()
                .post("/api/documents/batch")
                .then()
                .statusCode(400)
                .body("title", equalTo("Bad Request"))
                .body("status", equalTo(400))
                .body("detail", equalTo("Request body is required"));
    }

    @Test
    void testTextPlainContentTypeReturnsUnsupportedMediaType() {
        given()
                .contentType(ContentType.TEXT)
                .body("some plain text")
                .when()
                .post("/api/documents/batch")
                .then()
                .statusCode(415)
                .body("type", equalTo("about:blank"))
                .body("title", equalTo("Unsupported Media Type"))
                .body("status", equalTo(415))
                .body("detail", notNullValue())
                .body("instance", containsString("documents/batch"))
                .body("timestamp", notNullValue());
    }

    @Test
    void testXmlContentTypeReturnsUnsupportedMediaType() {
        given()
                .contentType(ContentType.XML)
                .body("<request><path>test.adoc</path></request>")
                .when()
                .post("/api/documents/batch")
                .then()
                .statusCode(415)
                .body("type", equalTo("about:blank"))
                .body("title", equalTo("Unsupported Media Type"))
                .body("status", equalTo(415))
                .body("timestamp", notNullValue());
    }

    @Test
    void testHtmlContentTypeReturnsUnsupportedMediaType() {
        given()
                .contentType(ContentType.HTML)
                .body("<html><body>test</body></html>")
                .when()
                .post("/api/documents/batch")
                .then()
                .statusCode(415)
                .body("type", equalTo("about:blank"))
                .body("title", equalTo("Unsupported Media Type"))
                .body("status", equalTo(415))
                .body("timestamp", notNullValue());
    }

    @Test
    void testJsonContentTypeStillWorksOnBatchEndpoint() {
        docStore.write("3.27", "existing.adoc", "= Existing\nContent");
        given()
                .contentType(ContentType.JSON)
                .body("{\"paths\": [\"existing.adoc\"], \"version\": \"3.27\"}")
                .when()
                .post("/api/documents/batch")
                .then()
                .statusCode(200);
    }
}
