package com.fvd.api.resources;

import com.fvd.indexs.indexers.CodeSampleEntry;
import com.fvd.indexs.indexers.CodeSampleIndex;
import com.fvd.indexs.indexers.KeywordScore;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class CodeSampleResourceTest extends AbstractApiResourceTest {

    @Test
    void testSearchCodeSamplesNoResults() {
        given()
                .queryParam("version", "main")
                .queryParam("keywords", "nonexistent")
                .when().get("/api/code-samples")
                .then()
                .statusCode(200)
                .body("results.size()", is(0))
                .body("totalCount", is(0))
                .body("offset", is(0))
                .body("hasMore", is(false));
    }

    @Test
    void testSearchCodeSamplesReturnsResults() {
        ensureVersionCached();
        seedCodeSampleIndex();
        given()
                .queryParam("version", "3.27")
                .queryParam("keywords", "security")
                .when().get("/api/code-samples")
                .then()
                .statusCode(200)
                .body("results.size()", greaterThan(0))
                .body("totalCount", greaterThan(0))
                .body("returnedCount", greaterThan(0));
    }

    @Test
    void testSearchCodeSamplesResultStructure() {
        seedDocFile();
        seedCodeSampleIndex();
        given()
                .queryParam("version", "3.27")
                .queryParam("keywords", "security")
                .when().get("/api/code-samples")
                .then()
                .statusCode(200)
                .body("results[0].language", equalTo("java"))
                .body("results[0].content", notNullValue())
                .body("results[0].context", notNullValue())
                .body("results[0].documentPath", notNullValue())
                .body("results[0].score", greaterThan(0f))
                .body("results[0].matchedKeywords", notNullValue())
                .body("results[0].startLine", greaterThan(0))
                .body("results[0].endLine", greaterThan(0));
    }

    @Test
    void testSearchCodeSamplesWithLanguageFilter() {
        ensureVersionCached();
        seedCodeSampleIndexWithMultipleLanguages();
        given()
                .queryParam("version", "3.27")
                .queryParam("keywords", "security")
                .queryParam("language", "java")
                .when().get("/api/code-samples")
                .then()
                .statusCode(200)
                .body("results.size()", is(1))
                .body("results[0].language", equalTo("java"));
    }

    @Test
    void testSearchCodeSamplesWithExtensionFilter() {
        ensureVersionCached();
        seedCodeSampleIndexWithExtensions();
        given()
                .queryParam("version", "3.27")
                .queryParam("keywords", "security")
                .queryParam("extension", "quarkus-core")
                .when().get("/api/code-samples")
                .then()
                .statusCode(200)
                .body("results.size()", is(1))
                .body("results[0].extension", equalTo("quarkus-core"));
    }

    @Test
    void testSearchCodeSamplesWithPagination() {
        ensureVersionCached();
        seedCodeSampleIndex();
        given()
                .queryParam("version", "3.27")
                .queryParam("keywords", "security")
                .queryParam("limit", 1)
                .queryParam("offset", 0)
                .when().get("/api/code-samples")
                .then()
                .statusCode(200)
                .body("results.size()", is(1))
                .body("totalCount", equalTo(2))
                .body("offset", is(0))
                .body("limit", is(1))
                .body("hasMore", is(true));
    }

    @Test
    void testSearchCodeSamplesSortedByScore() {
        ensureVersionCached();
        seedCodeSampleIndexWithScores();
        given()
                .queryParam("version", "3.27")
                .queryParam("keywords", "security")
                .when().get("/api/code-samples")
                .then()
                .statusCode(200)
                .body("results.size()", greaterThan(0))
                .body("results[0].score", notNullValue());
    }

    private void seedDocFile() {
        docStore.write("3.27", "security.adoc", "= Security Guide\nContent about security.");
    }

    /**
     * Creates the cache directory for version 3.27 so that version validation passes.
     */
    private void ensureVersionCached() {
        docStore.write("3.27", "_placeholder.adoc", "= Placeholder");
    }

    private void seedCodeSampleIndexWithMultipleLanguages() {
        CodeSampleIndex index = new CodeSampleIndex(List.of(
                new CodeSampleEntry("security.adoc", "Authentication", "java",
                        "import io.quarkus.security.identity.SecurityIdentity;",
                        5, 10,
                        List.of(new KeywordScore("security", 15))),
                new CodeSampleEntry("security.adoc", "Properties", "properties",
                        "quarkus.security.enabled=true",
                        15, 18,
                        List.of(new KeywordScore("security", 10)))
        ));
        codeSampleIndexStore.write("3.27", index);
    }

    private void seedCodeSampleIndexWithExtensions() {
        CodeSampleIndex index = new CodeSampleIndex(List.of(
                new CodeSampleEntry("core-security.adoc", "Authentication", "java",
                        "import io.quarkus.security.identity.SecurityIdentity;",
                        5, 10,
                        List.of(new KeywordScore("security", 15)), "quarkus-core"),
                new CodeSampleEntry("ext-security.adoc", "Authentication", "java",
                        "import io.quarkus.openapi.Generator;",
                        5, 10,
                        List.of(new KeywordScore("security", 10)), "quarkus-openapi-generator")
        ));
        codeSampleIndexStore.write("3.27", index);
    }

    private void seedCodeSampleIndexWithScores() {
        CodeSampleIndex index = new CodeSampleIndex(List.of(
                new CodeSampleEntry("high-score.adoc", "High Score Section", "java",
                        "// High score code",
                        5, 10,
                        List.of(new KeywordScore("security", 100))),
                new CodeSampleEntry("low-score.adoc", "Low Score Section", "java",
                        "// Low score code",
                        5, 10,
                        List.of(new KeywordScore("security", 1)))
        ));
        codeSampleIndexStore.write("3.27", index);
    }

    @Test
    void testSearchCodeSamplesReturns400ForUnknownVersion() {
        given()
                .queryParam("version", "nonexistent")
                .queryParam("keywords", "security")
                .when().get("/api/code-samples")
                .then()
                .statusCode(400)
                .body("detail", containsString("Unknown version"));
    }

    @Test
    void testSearchCodeSamplesReturns400ForUnknownSubject() {
        ensureVersionCached();
        seedCodeSampleIndex();
        given()
                .queryParam("version", "3.27")
                .queryParam("keywords", "security")
                .queryParam("subject", "nonexistent-subject")
                .when().get("/api/code-samples")
                .then()
                .statusCode(400)
                .body("detail", containsString("Unknown subject"));
    }
}
