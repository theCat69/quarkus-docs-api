package com.fvd.api.resources;

import com.fvd.indexs.indexers.FileKeywordEntry;
import com.fvd.indexs.indexers.KeywordIndex;
import com.fvd.indexs.indexers.KeywordScore;
import com.fvd.indexs.indexers.SectionKeywordEntry;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

/**
 * Integration tests for Feature 78 — Fix Slow Document Keyword Search.
 * Validates brief default behavior, limit capping, and warning field.
 */
@QuarkusTest
class DocumentResourceSlowSearchTest extends AbstractApiResourceTest {

    @Test
    void testKeywordSearchDefaultsBriefToTrue() {
        seedManyDocs(3);
        seedManyDocKeywordIndex(3);
        given()
                .queryParam("version", "3.27")
                .queryParam("keywords", "quarkus")
                .when().get("/api/documents")
                .then()
                .statusCode(200)
                .body("results.size()", greaterThan(0))
                .body("results[0].title", notNullValue())
                .body("results[0].path", notNullValue())
                .body("results[0].sections", nullValue())
                .body("results[0].codeBlocks", nullValue());
    }

    @Test
    void testKeywordSearchBriefFalseCapsAtFiveResults() {
        seedManyDocs(8);
        seedManyDocKeywordIndex(8);
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
        seedManyDocKeywordIndex(8);
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
        seedManyDocKeywordIndex(8);
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
        seedManyDocKeywordIndex(3);
        given()
                .queryParam("version", "3.27")
                .queryParam("keywords", "quarkus")
                .queryParam("brief", "false")
                .when().get("/api/documents")
                .then()
                .statusCode(200)
                .body("results.size()", is(3))
                .body("warning", nullValue());
    }

    @Test
    void testKeywordSearchExplicitBriefTrueStillWorks() {
        seedManyDocs(3);
        seedManyDocKeywordIndex(3);
        given()
                .queryParam("version", "3.27")
                .queryParam("keywords", "quarkus")
                .queryParam("brief", "true")
                .when().get("/api/documents")
                .then()
                .statusCode(200)
                .body("results.size()", greaterThan(0))
                .body("results[0].title", notNullValue())
                .body("results[0].sections", nullValue())
                .body("results[0].codeBlocks", nullValue());
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

    private void seedManyDocKeywordIndex(int count) {
        List<FileKeywordEntry> entries = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            entries.add(new FileKeywordEntry(
                    "doc" + i + ".adoc",
                    List.of(new KeywordScore("quarkus", 10 - i)),
                    List.of(new SectionKeywordEntry("Section " + i, 4, 6,
                            List.of(new KeywordScore("quarkus", 5))))
            ));
        }
        keywordIndexStore.write("3.27", new KeywordIndex(entries));
    }
}
