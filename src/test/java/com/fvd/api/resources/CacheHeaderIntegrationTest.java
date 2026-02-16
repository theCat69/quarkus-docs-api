package com.fvd.api.resources;

import com.fvd.indexs.model.DocChunk;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

@QuarkusTest
class CacheHeaderIntegrationTest extends AbstractApiResourceTest {

    @Test
    void shouldReturnCacheHeadersOnDocumentSearch() {
        seedKeywordIndexMultiple();
        seedDocFilesMultiple();
        given()
                .queryParam("version", "3.27")
                .queryParam("keywords", "security")
                .when().get("/api/documents")
                .then()
                .statusCode(200)
                .header("Cache-Control", containsString("max-age="))
                .header("ETag", notNullValue());
    }

    @Test
    void shouldReturnCacheHeadersOnSearch() {
        seedSearchChunksForCache();
        given()
                .queryParam("version", "3.27")
                .queryParam("q", "security")
                .when().get("/api/search")
                .then()
                .statusCode(200)
                .header("Cache-Control", containsString("max-age="))
                .header("ETag", notNullValue());
    }

    @Test
    void shouldReturnCatalogMaxAgeOnCatalog() {
        seedDocFilesMultiple();
        given()
                .queryParam("version", "3.27")
                .when().get("/api/catalog")
                .then()
                .statusCode(200)
                .header("Cache-Control", containsString("max-age=1800"))
                .header("ETag", notNullValue());
    }

    @Test
    void shouldNotReturnCacheHeadersOnPostBatch() {
        seedKeywordIndexMultiple();
        seedDocFilesMultiple();
        given()
                .contentType("application/json")
                .body("{\"paths\":[\"security.adoc\"],\"version\":\"3.27\"}")
                .when().post("/api/documents/batch")
                .then()
                .statusCode(200)
                .header("Cache-Control", nullValue())
                .header("ETag", nullValue());
    }

    @Test
    void shouldReturn304OnConditionalGet() {
        seedSearchChunksForCache();

        // Step 1: GET the resource, capture the ETag
        String etag = given()
                .queryParam("version", "3.27")
                .queryParam("q", "security")
                .when().get("/api/search")
                .then()
                .statusCode(200)
                .extract().header("ETag");

        // Step 2: Repeat with If-None-Match
        given()
                .queryParam("version", "3.27")
                .queryParam("q", "security")
                .header("If-None-Match", etag)
                .when().get("/api/search")
                .then()
                .statusCode(304)
                .body(emptyOrNullString());
    }

    @Test
    void shouldProduceDifferentETagsForDifferentFields() {
        seedSearchChunksForCache();

        // Request with fields=title
        String etag1 = given()
                .queryParam("version", "3.27")
                .queryParam("q", "security")
                .queryParam("fields", "title")
                .when().get("/api/search")
                .then()
                .statusCode(200)
                .header("ETag", notNullValue())
                .extract().header("ETag");

        // Request with fields=title,page
        String etag2 = given()
                .queryParam("version", "3.27")
                .queryParam("q", "security")
                .queryParam("fields", "title,page")
                .when().get("/api/search")
                .then()
                .statusCode(200)
                .header("ETag", notNullValue())
                .extract().header("ETag");

        // ETags should be different because different fields → different byte[]
        assertThat(etag2, not(equalTo(etag1)));
    }

    private void seedSearchChunksForCache() {
        seedDocFilesMultiple();
        seedDocChunks("3.27", List.of(
                new DocChunk("cache-chunk-1", "3.27", "security.adoc",
                        "Security", "Overview",
                        "https://quarkus.io/guides/security",
                        List.of("security"), List.of("quarkus-core"),
                        "Security overview",
                        "Content about security and quarkus applications.")
        ));
    }
}
