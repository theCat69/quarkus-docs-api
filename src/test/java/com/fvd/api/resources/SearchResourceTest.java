package com.fvd.api.resources;

import java.util.List;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fvd.api.dto.SearchRequest;
import com.fvd.indexs.model.DocChunk;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class SearchResourceTest extends AbstractApiResourceTest {

    @BeforeEach
    void seedTestData() {
        seedDocChunks("main", List.of(
                new DocChunk("search-reactive-1", "main", "reactive", "Reactive Guide", "Overview",
                        "https://quarkus.io/guides/reactive#overview",
                        List.of("reactive", "streams"), List.of("quarkus-core"),
                        "Overview of reactive programming in Quarkus",
                        "Reactive programming is a paradigm for building reactive systems. " +
                                "Reactive streams and reactive extensions are core to reactive programming in Quarkus."),
                new DocChunk("search-reactive-2", "main", "reactive", "Reactive Guide", "Streams",
                        "https://quarkus.io/guides/reactive#streams",
                        List.of("reactive", "streams"), List.of("quarkus-core"),
                        "Working with reactive streams",
                        "Reactive streams provide a standard for asynchronous stream processing with non-blocking back pressure."),
                new DocChunk("search-security-1", "main", "security", "Security Guide", "Overview",
                        "https://quarkus.io/guides/security#overview",
                        List.of("security"), List.of("quarkus-core"),
                        "Overview of security features",
                        "This guide covers security basics and authentication in Quarkus applications."),
                new DocChunk("search-mailer-1", "main", "mailer", "Mailer Guide", "Overview",
                        "https://quarkus.io/guides/mailer#overview",
                        List.of("reactive", "mail"), List.of("io.quarkus:quarkus-mailer"),
                        "Overview of the reactive mailer",
                        "The reactive mailer extension provides reactive email sending capabilities in Quarkus."),
                new DocChunk("search-config-1", "main", "config", "Config Guide", "Overview",
                        "https://quarkus.io/guides/config#overview",
                        List.of("config"), List.of("quarkus-core"),
                        "Overview of configuration",
                        "Configuration management for application properties and settings."),
                new DocChunk("search-rest-1", "main", "rest-client", "REST Client Guide", "Overview",
                        "https://quarkus.io/guides/rest-client#overview",
                        List.of("rest", "reactive"), List.of("quarkus-core"),
                        "Overview of REST client",
                        "Building reactive REST clients with Quarkus for consuming external services.")
        ));
    }

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

    @Test
    void shouldFilterByExtension() {
        given()
                .queryParam("q", "reactive")
                .queryParam("version", "main")
                .queryParam("extension", "io.quarkus:quarkus-mailer")
        .when()
                .get("/api/search")
        .then()
                .statusCode(200)
                .body("results.size()", greaterThanOrEqualTo(1))
                .body("results[0].extensions", notNullValue());
    }

    @Test
    void shouldReturnFuzzyResultsForMisspelledQuery() {
        given()
                .queryParam("q", "reative progamming")
                .queryParam("version", "main")
        .when()
                .get("/api/search")
        .then()
                .statusCode(200);
    }

    @Test
    void shouldScopeToVersion() {
        // Ensure version 3.17 is recognized as a cached version
        cacheService.ensureVersionDir("3.17");
        seedDocChunks("3.17", List.of(
                new DocChunk("v317-reactive-1", "3.17", "reactive-v317", "Reactive 3.17", "Overview",
                        "https://quarkus.io/guides/reactive#overview",
                        List.of("reactive"), List.of("quarkus-core"),
                        "Reactive programming in version 3.17",
                        "Reactive programming paradigm for building reactive systems in Quarkus 3.17.")
        ));

        given()
                .queryParam("q", "reactive")
                .queryParam("version", "3.17")
        .when()
                .get("/api/search")
        .then()
                .statusCode(200)
                .body("results.size()", greaterThanOrEqualTo(1));
    }

    @Test
    void shouldReturnPaginatedResults() {
        given()
                .queryParam("q", "reactive")
                .queryParam("version", "main")
                .queryParam("limit", 2)
                .queryParam("offset", 0)
        .when()
                .get("/api/search")
        .then()
                .statusCode(200)
                .body("limit", is(2))
                .body("offset", is(0))
                .body("total", greaterThanOrEqualTo(1));
    }

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

    @Test
    void shouldReturn404ForCodeSamplesEndpoint() {
        given()
        .when()
                .get("/api/code-samples")
        .then()
                .statusCode(404);
    }

    @Test
    void shouldReturn400WhenQueryMissing() {
        given()
                .queryParam("version", "main")
        .when()
                .get("/api/search")
        .then()
                .statusCode(400)
                .body("detail", containsString("must not be empty"));
    }

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
}
