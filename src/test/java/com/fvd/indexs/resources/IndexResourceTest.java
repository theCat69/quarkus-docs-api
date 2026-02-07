package com.fvd.indexs.resources;

import io.quarkus.test.junit.QuarkusTest;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class IndexResourceTest {

    @BeforeEach
    void cleanTestCache() throws IOException {
        var cachePath = Path.of("build/test-cache").toFile();
        if(cachePath.exists()) {
            FileUtils.cleanDirectory(cachePath);
        }
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
    void testIndexEndpointValidVersionReturnsIndex() {
        given()
                .queryParam("version", "3.21")
                .when().get("/api/index")
                .then()
                .statusCode(200)
                .body("[0].name", equalTo("security-overview.adoc"))
                .body("[1].name", equalTo("config.adoc"))
                .body("[2].name", equalTo("cdi.adoc"))
                .body("size()", is(3));
    }

}