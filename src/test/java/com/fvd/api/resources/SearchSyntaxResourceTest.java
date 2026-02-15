package com.fvd.api.resources;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class SearchSyntaxResourceTest extends AbstractApiResourceTest {

    @Test
    void shouldReturnSearchSyntaxDocumentation() {
        given()
                .when().get("/api/search/syntax")
                .then()
                .statusCode(200)
                .body("tokenization", notNullValue())
                .body("stemming", notNullValue())
                .body("scoring", notNullValue())
                .body("stopWords", notNullValue())
                .body("fuzzyMatching", notNullValue())
                .body("supported", notNullValue())
                .body("unsupported", notNullValue())
                .body("filters", notNullValue())
                .body("examples", notNullValue())
                .body("tips", notNullValue());
    }

    @Test
    void shouldReturnStopWordsList() {
        given()
                .when().get("/api/search/syntax")
                .then()
                .statusCode(200)
                .body("stopWords.words.size()", greaterThanOrEqualTo(1))
                .body("stopWords.words", hasItems("a", "the", "and", "is"));
    }

    @Test
    void shouldReturnScoringMatchTypes() {
        given()
                .when().get("/api/search/syntax")
                .then()
                .statusCode(200)
                .body("scoring.matchTypes.type", hasItems("exact", "prefix"));
    }

    @Test
    void shouldReturnLocationWeights() {
        given()
                .when().get("/api/search/syntax")
                .then()
                .statusCode(200)
                .body("scoring.locationWeights", hasSize(5))
                .body("scoring.locationWeights.location", hasItem("filename"))
                .body("scoring.locationWeights.find { it.location == 'filename' }.weight", equalTo(10.0f));
    }

    @Test
    void shouldReturnUnsupportedFeatures() {
        given()
                .when().get("/api/search/syntax")
                .then()
                .statusCode(200)
                .body("unsupported.features.syntax", hasItems(
                        "\"quoted phrases\"",
                        "AND / OR / NOT",
                        "* or ? wildcards"
                ));
    }

    @Test
    void shouldReturnStemmingExamples() {
        given()
                .when().get("/api/search/syntax")
                .then()
                .statusCode(200)
                .body("stemming.examples.size()", greaterThanOrEqualTo(3))
                .body("stemming.examples[0].input", notNullValue())
                .body("stemming.examples[0].stemmed", notNullValue())
                .body("stemming.examples[0].alsoMatches", notNullValue());
    }

    @Test
    void shouldReturnQueryExamples() {
        given()
                .when().get("/api/search/syntax")
                .then()
                .statusCode(200)
                .body("examples.size()", greaterThanOrEqualTo(3))
                .body("examples[0].query", notNullValue())
                .body("examples[0].description", notNullValue());
    }

    @Test
    void shouldReturnTips() {
        given()
                .when().get("/api/search/syntax")
                .then()
                .statusCode(200)
                .body("tips.size()", greaterThanOrEqualTo(5))
                .body("tips", everyItem(not(emptyString())));
    }

    @Test
    void shouldReturnFilters() {
        given()
                .when().get("/api/search/syntax")
                .then()
                .statusCode(200)
                .body("filters.parameter", hasItems("version", "subject", "extension", "limit", "offset"));
    }

    @Test
    void shouldReturnFuzzyMatchingInfo() {
        given()
                .when().get("/api/search/syntax")
                .then()
                .statusCode(200)
                .body("fuzzyMatching.appliesTo", containsString("Section title search"));
    }
}
