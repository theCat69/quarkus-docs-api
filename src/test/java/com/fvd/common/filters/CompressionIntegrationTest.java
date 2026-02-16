package com.fvd.common.filters;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class CompressionIntegrationTest {

    @Test
    void testGzipCompressionEnabled() {
        given()
                .header("Accept-Encoding", "gzip")
                .queryParam("q", "security")
        .when()
                .get("/api/search")
        .then()
                .statusCode(200)
                .header("Content-Encoding", containsString("gzip"));
    }

    @Test
    void testCompressedResponseIsValidJson() {
        given()
                .queryParam("q", "security")
        .when()
                .get("/api/search")
        .then()
                .statusCode(200)
                .body("results", notNullValue());
    }
}
