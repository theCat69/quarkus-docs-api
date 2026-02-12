package com.fvd.api.resources;

import com.fvd.indexs.stores.SqliteSchemaInitializer;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Tests verifying RFC 7807 Problem Details error responses.
 */
@QuarkusTest
class ProblemDetailErrorResponseTest {

    @Inject
    SqliteSchemaInitializer schemaInitializer;

    @BeforeEach
    void cleanTestCache() throws IOException {
        var cachePath = Path.of("build/test-cache").toFile();
        if (cachePath.exists()) {
            FileUtils.cleanDirectory(cachePath);
        }
        schemaInitializer.resetSchema();
    }

    @Test
    void testBadRequestReturnsProblemDetail() {
        given()
                .queryParam("version", "../etc/passwd")
                .when().get("/api/catalog")
                .then()
                .statusCode(400)
                .body("title", equalTo("Bad Request"))
                .body("status", equalTo(400))
                .body("detail", notNullValue())
                .body("instance", containsString("catalog"))
                .body("timestamp", notNullValue());
    }

    @Test
    void testNotFoundReturnsProblemDetail() {
        given()
                .queryParam("version", "3.27")
                .queryParam("path", "nonexistent.adoc")
                .when().get("/api/documents")
                .then()
                .statusCode(404)
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
