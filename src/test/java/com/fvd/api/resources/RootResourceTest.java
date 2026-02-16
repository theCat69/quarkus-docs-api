package com.fvd.api.resources;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
class RootResourceTest extends AbstractApiResourceTest {

    @Test
    void testRootReturns200() {
        given()
                .when().get("/")
                .then()
                .statusCode(200);
    }

    @Test
    void testRootReturnsJsonContentType() {
        given()
                .when().get("/")
                .then()
                .contentType("application/json");
    }

    @Test
    void testRootContainsMessage() {
        given()
                .when().get("/")
                .then()
                .statusCode(200)
                .body("message", equalTo("Quarkus Docs API"));
    }

    @Test
    void testRootContainsDocumentation() {
        given()
                .when().get("/")
                .then()
                .statusCode(200)
                .body("documentation", equalTo("/api/meta"));
    }

    @Test
    void testRootContainsOpenapi() {
        given()
                .when().get("/")
                .then()
                .statusCode(200)
                .body("openapi", equalTo("/q/openapi"));
    }
}
