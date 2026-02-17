package com.fvd.api.resources;

import java.util.ArrayList;
import java.util.List;

import com.fvd.indexs.model.DocChunk;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Integration tests for Feature 78 — Fix Slow Document Keyword Search.
 * Validates brief default behavior, limit capping, and warning field.
 */
@QuarkusTest
class DocumentResourceSlowSearchTest extends AbstractApiResourceTest {

    @Test
    void testKeywordSearchDefaultsBriefToTrue() {
        seedManyDocs(3);
        seedManyDocChunkIndex(3);
        given()
                .queryParam("version", "3.27")
                .queryParam("keywords", "quarkus")
                .when().get("/api/documents")
                .then()
                .statusCode(200)
                .body("results.size()", greaterThan(0))
                .body("results[0].title", notNullValue())
                .body("results[0].path", notNullValue())
                .body("results[0]", not(hasKey("sections")))
                .body("results[0]", not(hasKey("codeBlocks")));
    }

    @Test
    void testKeywordSearchBriefFalseCapsAtFiveResults() {
        seedManyDocs(8);
        seedManyDocChunkIndex(8);
        given()
                .queryParam("version", "3.27")
                .queryParam("keywords", "quarkus")
                .queryParam("brief", "false")
                .when().get("/api/documents")
                .then()
                .statusCode(200)
                .body("returnedCount", is(5))
                .body("totalCount", equalTo(8))
                .body("results.size()", is(5))
                .body("results[0].sections", notNullValue());
    }

    @Test
    void testKeywordSearchBriefFalseIncludesWarningWhenCapped() {
        seedManyDocs(8);
        seedManyDocChunkIndex(8);
        given()
                .queryParam("version", "3.27")
                .queryParam("keywords", "quarkus")
                .queryParam("brief", "false")
                .when().get("/api/documents")
                .then()
                .statusCode(200)
                .body("warning", notNullValue())
                .body("warning", containsString("brief=false"))
                .body("warning", containsString("limited to 5"));
    }

    @Test
    void testKeywordSearchBriefFalseRespectsExplicitLimitBelowCap() {
        seedManyDocs(8);
        seedManyDocChunkIndex(8);
        given()
                .queryParam("version", "3.27")
                .queryParam("keywords", "quarkus")
                .queryParam("brief", "false")
                .queryParam("limit", 2)
                .when().get("/api/documents")
                .then()
                .statusCode(200)
                .body("results.size()", is(2))
                .body("returnedCount", is(2))
                .body("results[0].sections", notNullValue());
    }

    @Test
    void testKeywordSearchBriefFalseNoWarningWhenSmallResultSet() {
        seedManyDocs(3);
        seedManyDocChunkIndex(3);
        given()
                .queryParam("version", "3.27")
                .queryParam("keywords", "quarkus")
                .queryParam("brief", "false")
                .when().get("/api/documents")
                .then()
                .statusCode(200)
                .body("results.size()", is(3))
                .body("$", not(hasKey("warning")));
    }

    @Test
    void testKeywordSearchExplicitBriefTrueStillWorks() {
        seedManyDocs(3);
        seedManyDocChunkIndex(3);
        given()
                .queryParam("version", "3.27")
                .queryParam("keywords", "quarkus")
                .queryParam("brief", "true")
                .when().get("/api/documents")
                .then()
                .statusCode(200)
                .body("results.size()", greaterThan(0))
                .body("results[0].title", notNullValue())
                .body("results[0]", not(hasKey("sections")))
                .body("results[0]", not(hasKey("codeBlocks")));
    }

    private void seedManyDocs(int count) {
        for (int i = 0; i < count; i++) {
            String content = "= Doc " + i + "\n" +
                    ":description: Description for doc " + i + "\n\n" +
                    "== Section " + i + "\n" +
                    "Content about quarkus in doc " + i + ".\n";
            docStore.write("3.27", "doc" + i + ".adoc", content);
        }
    }

    private void seedManyDocChunkIndex(int count) {
        List<DocChunk> chunks = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            chunks.add(new DocChunk(
                    "slow-chunk-" + i, "3.27", "doc" + i,
                    "Doc " + i, "Section " + i,
                    "https://quarkus.io/guides/doc" + i,
                    List.of("quarkus"), List.of("quarkus-core"),
                    "Description for doc " + i,
                    "Content about quarkus framework features in doc " + i + " covering various topics."
            ));
        }
        seedDocChunks("3.27", chunks);
    }
}
