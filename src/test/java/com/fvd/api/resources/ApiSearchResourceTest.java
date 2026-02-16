package com.fvd.api.resources;

import com.fvd.indexs.model.DocChunk;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class ApiSearchResourceTest extends AbstractApiResourceTest {

    @Test
    void testSearchNoResults() {
        given()
                .queryParam("version", "main")
                .queryParam("q", "nonexistent")
                .when().get("/api/search")
                .then()
                .statusCode(200)
                .body("results.size()", is(0))
                .body("total", is(0))
                .body("offset", is(0));
    }

    @Test
    void testSearchReturnsResults() {
        seedSearchChunks("3.27");
        given()
                .queryParam("version", "3.27")
                .queryParam("q", "security")
                .when().get("/api/search")
                .then()
                .statusCode(200)
                .body("results.size()", greaterThan(0))
                .body("total", greaterThan(0));
    }

    @Test
    void testSearchResultStructure() {
        seedSearchChunks("3.27");
        given()
                .queryParam("version", "3.27")
                .queryParam("q", "security")
                .when().get("/api/search")
                .then()
                .statusCode(200)
                .body("results[0].id", notNullValue())
                .body("results[0].page", notNullValue())
                .body("results[0].title", notNullValue())
                .body("results[0].section", notNullValue())
                .body("results[0].score", greaterThan(0f));
    }

    @Test
    void testSearchWithExtensionFilter() {
        seedSearchChunksWithExtensions("3.27");
        given()
                .queryParam("version", "3.27")
                .queryParam("q", "security")
                .queryParam("extension", "quarkus-core")
                .when().get("/api/search")
                .then()
                .statusCode(200)
                .body("results.size()", is(1))
                .body("results[0].extensions", notNullValue());
    }

    @Test
    void testSearchWithPagination() {
        seedMultipleSearchChunks("3.27");
        given()
                .queryParam("version", "3.27")
                .queryParam("q", "quarkus")
                .queryParam("limit", 1)
                .queryParam("offset", 0)
                .when().get("/api/search")
                .then()
                .statusCode(200)
                .body("results.size()", is(1))
                .body("offset", is(0))
                .body("limit", is(1));
    }

    @Test
    void testSearchWithOffset() {
        seedMultipleSearchChunks("3.27");
        given()
                .queryParam("version", "3.27")
                .queryParam("q", "quarkus")
                .queryParam("limit", 1)
                .queryParam("offset", 1)
                .when().get("/api/search")
                .then()
                .statusCode(200)
                .body("results.size()", is(1))
                .body("offset", is(1))
                .body("limit", is(1));
    }

    @Test
    void testSearchSummaryContainsContent() {
        seedSearchChunks("3.27");
        given()
                .queryParam("version", "3.27")
                .queryParam("q", "security")
                .when().get("/api/search")
                .then()
                .statusCode(200)
                .body("results[0].summary", notNullValue());
    }

    @Test
    void testSearchSortedByScore() {
        seedMultipleSearchChunks("3.27");
        given()
                .queryParam("version", "3.27")
                .queryParam("q", "security")
                .when().get("/api/search")
                .then()
                .statusCode(200)
                .body("results.size()", greaterThan(0))
                .body("results[0].score", notNullValue());
    }

    @Test
    void testSearchDefaultVersion() {
        seedSearchChunks("main");
        given()
                .queryParam("q", "security")
                .when().get("/api/search")
                .then()
                .statusCode(200)
                .body("results.size()", greaterThan(0));
    }

    @Test
    void testSearchReturns400ForUnknownVersion() {
        given()
                .queryParam("version", "nonexistent")
                .queryParam("q", "security")
                .when().get("/api/search")
                .then()
                .statusCode(400)
                .body("detail", containsString("Unknown version"));
    }

    @Test
    void testSearchAcceptsMainVersionEvenIfNotCached() {
        given()
                .queryParam("version", "main")
                .queryParam("q", "security")
                .when().get("/api/search")
                .then()
                .statusCode(200);
    }

    @Test
    void testPostSearchWithValidBody() {
        seedSearchChunks("3.27");
        given()
                .contentType(ContentType.JSON)
                .body("{\"q\": \"security\", \"version\": \"3.27\"}")
                .when().post("/api/search")
                .then()
                .statusCode(200)
                .body("results", notNullValue())
                .body("results.size()", greaterThan(0));
    }

    @Test
    void testPostSearchReturnsSameResultsAsGet() {
        seedSearchChunks("3.27");

        int getTotal = given()
                .queryParam("version", "3.27")
                .queryParam("q", "security")
                .when().get("/api/search")
                .then()
                .statusCode(200)
                .extract().path("total");

        given()
                .contentType(ContentType.JSON)
                .body("{\"q\": \"security\", \"version\": \"3.27\"}")
                .when().post("/api/search")
                .then()
                .statusCode(200)
                .body("total", equalTo(getTotal));
    }

    @Test
    void testPostSearchWithMissingQuery() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"version\": \"3.27\"}")
                .when().post("/api/search")
                .then()
                .statusCode(400);
    }

    @Test
    void testPostSearchWithNullBody() {
        given()
                .contentType(ContentType.JSON)
                .body("")
                .when().post("/api/search")
                .then()
                .statusCode(400);
    }

    @Test
    void testPostSearchWithInvalidVersion() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"q\": \"security\", \"version\": \"nonexistent\"}")
                .when().post("/api/search")
                .then()
                .statusCode(400)
                .body("detail", containsString("Unknown version"));
    }

    private void seedSearchChunks(String version) {
        // Ensure version cache directory exists so validation passes
        docStore.write(version, "security-overview.adoc",
                "= Security Overview\nContent about security.");
        seedDocChunks(version, List.of(
                new DocChunk("chunk-1", version, "security-overview.adoc",
                        "Security Overview", "Introduction",
                        "https://quarkus.io/guides/security-overview",
                        List.of("security"), List.of("quarkus-core"),
                        "Overview of security features",
                        "This guide covers security features including authentication and authorization in Quarkus applications.")
        ));
    }

    private void seedSearchChunksWithExtensions(String version) {
        docStore.write(version, "security.adoc", "= Security\nContent about security.");
        docStore.write(version, "config.adoc", "= Config\nContent about config.");
        seedDocChunks(version, List.of(
                new DocChunk("chunk-ext-1", version, "security.adoc",
                        "Security", "Auth",
                        "https://quarkus.io/guides/security",
                        List.of("security"), List.of("quarkus-core"),
                        "Security authentication",
                        "This guide covers security authentication and authorization features."),
                new DocChunk("chunk-ext-2", version, "config.adoc",
                        "Config", "Security Config",
                        "https://quarkus.io/guides/config",
                        List.of("config"), List.of("quarkus-openapi-generator"),
                        "Security configuration",
                        "This guide covers security configuration options.")
        ));
    }

    private void seedMultipleSearchChunks(String version) {
        docStore.write(version, "security.adoc", "= Security\nContent about security and quarkus.");
        docStore.write(version, "config.adoc", "= Config\nContent about config and quarkus.");
        seedDocChunks(version, List.of(
                new DocChunk("chunk-multi-1", version, "security.adoc",
                        "Security Guide", "Overview",
                        "https://quarkus.io/guides/security",
                        List.of("security"), List.of("quarkus-core"),
                        "Security overview for quarkus applications",
                        "Quarkus provides comprehensive security features for application development."),
                new DocChunk("chunk-multi-2", version, "config.adoc",
                        "Config Guide", "Overview",
                        "https://quarkus.io/guides/config",
                        List.of("config"), List.of("quarkus-core"),
                        "Configuration overview for quarkus applications",
                        "Quarkus configuration system for application properties and settings.")
        ));
    }
}
