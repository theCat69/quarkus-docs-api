package com.fvd.api.resources;

import java.util.List;

import com.fvd.api.dto.BatchDocumentRequest;
import com.fvd.indexs.model.DocChunk;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Integration tests for Feature 81 — Omit Null Fields in Brief Mode.
 * Validates that {@code @JsonInclude(NON_NULL)} on {@code DocumentResponse}
 * and {@code DocumentSearchResponse} omits null fields from JSON responses.
 */
@QuarkusTest
class DocumentResponseNullFieldsTest extends AbstractApiResourceTest {

    @Test
    void testBriefModeOmitsSectionsAndCodeBlocks() {
        seedDocFile();
        seedDocChunkIndex();
        given()
                .queryParam("version", "3.27")
                .queryParam("keywords", "security")
                .queryParam("brief", "true")
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
    void testFullModeIncludesSectionsAndCodeBlocks() {
        seedDocFile();
        seedDocChunkIndex();
        given()
                .queryParam("version", "3.27")
                .queryParam("keywords", "security")
                .queryParam("brief", "false")
                .when().get("/api/documents")
                .then()
                .statusCode(200)
                .body("results[0]", hasKey("sections"))
                .body("results[0]", hasKey("codeBlocks"))
                .body("results[0].sections.size()", greaterThan(0))
                .body("results[0].codeBlocks", notNullValue());
    }

    @Test
    void testPathModeOmitsScoreWhenNull() {
        seedDocFile();
        given()
                .queryParam("version", "3.27")
                .queryParam("path", "security.adoc")
                .when().get("/api/documents")
                .then()
                .statusCode(200)
                .body("title", notNullValue())
                .body("$", not(hasKey("score")));
    }

    @Test
    void testPathModeKeepsMatchedKeywordsAsEmptyList() {
        seedDocFile();
        given()
                .queryParam("version", "3.27")
                .queryParam("path", "security.adoc")
                .when().get("/api/documents")
                .then()
                .statusCode(200)
                .body("$", hasKey("matchedKeywords"))
                .body("matchedKeywords", empty());
    }

    @Test
    void testBatchBriefModeOmitsNullFields() {
        seedDocFile();
        BatchDocumentRequest request = new BatchDocumentRequest(
                List.of("security.adoc"), "3.27", true);
        given()
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/api/documents/batch")
                .then()
                .statusCode(200)
                .body("documents.size()", is(1))
                .body("documents[0].title", notNullValue())
                .body("documents[0]", not(hasKey("sections")))
                .body("documents[0]", not(hasKey("codeBlocks")))
                .body("documents[0]", not(hasKey("score")));
    }

    @Test
    void testBriefModeWithFieldsParameterBothFiltersApply() {
        seedDocFile();
        seedDocChunkIndex();
        given()
                .queryParam("version", "3.27")
                .queryParam("keywords", "security")
                .queryParam("brief", "true")
                .queryParam("fields", "title,path")
                .when().get("/api/documents")
                .then()
                .statusCode(200)
                .body("results[0]", hasKey("title"))
                .body("results[0]", hasKey("path"))
                .body("results[0]", not(hasKey("sections")))
                .body("results[0]", not(hasKey("codeBlocks")))
                .body("results[0]", not(hasKey("score")))
                .body("results[0]", not(hasKey("subject")))
                .body("results[0]", not(hasKey("matchedKeywords")));
    }

    private void seedDocFile() {
        String docContent = """
                = Security Guide
                :description: Introduction to security features.
                
                == Overview
                This is the overview section.
                It covers security basics.
                
                == Configuration
                Config details here.
                """;
        docStore.write("3.27", "security.adoc", docContent);
    }

    private void seedDocChunkIndex() {
        seedDocChunks("3.27", List.of(
                new DocChunk("null-fields-chunk-1", "3.27", "security", "Security Guide", "Overview",
                        "https://quarkus.io/guides/security",
                        List.of("security"), List.of("quarkus-core"),
                        "Overview of security features",
                        "This guide covers security basics and authentication in quarkus.")
        ));
    }
}
