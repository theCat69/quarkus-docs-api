# Pattern: @QuarkusTest Integration Test (RestAssured + seed data)
# Demonstrates: @QuarkusTest, extending AbstractApiResourceTest, @BeforeEach seed data,
# given/when/then RestAssured DSL, Hamcrest matchers, static imports, and testing
# both happy path and error responses.

```java
package com.fvd.api.resources;

import java.util.List;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fvd.api.dto.SearchRequest;
import com.fvd.indexs.model.DocChunk;

// Static imports for test fluency — use these, not fully-qualified calls
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

// @QuarkusTest starts the full Quarkus context (HTTP server + DevServices PostgreSQL)
@QuarkusTest
class SearchResourceTest extends AbstractApiResourceTest {  // shared setup/teardown in base class

    // Seed test data before each test — do not share mutable state between tests
    @BeforeEach
    void seedTestData() {
        seedDocChunks("main", List.of(
                new DocChunk("search-reactive-1", "main", "reactive", "Reactive Guide", "Overview",
                        "https://quarkus.io/guides/reactive#overview",
                        List.of("reactive", "streams"), List.of("quarkus-core"),
                        "Overview of reactive programming in Quarkus",
                        "Reactive programming is a paradigm for building reactive systems."),
                new DocChunk("search-security-1", "main", "security", "Security Guide", "Overview",
                        "https://quarkus.io/guides/security#overview",
                        List.of("security"), List.of("quarkus-core"),
                        "Overview of security features",
                        "This guide covers security basics and authentication in Quarkus applications.")
        ));
    }

    // Happy path: descriptive method name explains the expected behavior
    @Test
    void shouldReturnRankedResultsForSearchQuery() {
        given()
                .queryParam("q", "reactive")
                .queryParam("version", "main")
        .when()
                .get("/api/search")
        .then()
                .statusCode(200)
                .body("results.size()", greaterThan(0))
                .body("results[0].score", greaterThan(0f));
    }

    // Verify response schema shape
    @Test
    void shouldMatchChunkSearchResponseSchema() {
        given()
                .queryParam("q", "reactive")
                .queryParam("version", "main")
        .when()
                .get("/api/search")
        .then()
                .statusCode(200)
                .body("$", hasKey("results"))
                .body("$", hasKey("total"))
                .body("$", hasKey("limit"))
                .body("$", hasKey("offset"))
                .body("total", greaterThanOrEqualTo(0))
                .body("limit", equalTo(20))
                .body("offset", equalTo(0));
    }

    // Error path: test that invalid input returns 400 with ProblemDetail
    @Test
    void shouldReturn400WhenQueryMissing() {
        given()
                .queryParam("version", "main")
        .when()
                .get("/api/search")
        .then()
                .statusCode(400)
                .body("detail", containsString("must not be empty"));  // ProblemDetail.detail field
    }

    // POST variant: JSON body request
    @Test
    void shouldAcceptPostWithJsonBody() {
        SearchRequest request = new SearchRequest("reactive", "main", null, null, null);

        given()
                .contentType(ContentType.JSON)
                .body(request)
        .when()
                .post("/api/search")
        .then()
                .statusCode(200)
                .body("results.size()", greaterThan(0));
    }

    // Filter test: extension filter scopes results correctly
    @Test
    void shouldFilterByExtension() {
        given()
                .queryParam("q", "reactive")
                .queryParam("version", "main")
                .queryParam("extension", "quarkus-core")
        .when()
                .get("/api/search")
        .then()
                .statusCode(200)
                .body("results.size()", greaterThanOrEqualTo(1))
                .body("results[0].extensions", notNullValue());
    }
}
```
