package com.fvd.api.resources;

import com.fvd.api.dto.BatchDocumentRequest;
import com.fvd.indexs.indexers.FileKeywordEntry;
import com.fvd.indexs.indexers.KeywordIndex;
import com.fvd.indexs.indexers.KeywordScore;
import com.fvd.indexs.indexers.SectionKeywordEntry;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
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

@QuarkusTest
class DocumentResourceTest extends AbstractApiResourceTest {

    // --- Path mode tests ---

    @Test
    void testGetDocumentByPath() {
        seedDocFile();
        given()
                .queryParam("version", "3.27")
                .queryParam("path", "security.adoc")
                .when().get("/api/documents")
                .then()
                .statusCode(200)
                .body("title", equalTo("Security Guide"))
                .body("path", equalTo("security.adoc"))
                .body("sections", notNullValue())
                .body("codeBlocks", notNullValue());
    }

    @Test
    void testGetDocumentByPathWithSections() {
        seedDocFile();
        given()
                .queryParam("version", "3.27")
                .queryParam("path", "security.adoc")
                .when().get("/api/documents")
                .then()
                .statusCode(200)
                .body("sections.size()", greaterThan(0))
                .body("sections[0].title", notNullValue())
                .body("sections[0].startLine", greaterThan(0))
                .body("sections[0].endLine", greaterThan(0));
    }

    @Test
    void testGetDocumentByPathWithCodeBlocks() {
        seedDocFileWithCode();
        given()
                .queryParam("version", "3.27")
                .queryParam("path", "rest.adoc")
                .when().get("/api/documents")
                .then()
                .statusCode(200)
                .body("codeBlocks.size()", greaterThan(0))
                .body("codeBlocks[0].language", equalTo("java"))
                .body("codeBlocks[0].content", notNullValue());
    }

    // --- Search mode tests ---

    @Test
    void testSearchDocumentsByKeywords() {
        seedDocFile();
        seedKeywordIndex();
        given()
                .queryParam("version", "3.27")
                .queryParam("keywords", "security")
                .when().get("/api/documents")
                .then()
                .statusCode(200)
                .body("results", notNullValue())
                .body("totalCount", greaterThan(0))
                .body("returnedCount", greaterThan(0));
    }

    @Test
    void testSearchDocumentsReturnsResults() {
        seedDocFile();
        seedKeywordIndex();
        given()
                .queryParam("version", "3.27")
                .queryParam("keywords", "security")
                .when().get("/api/documents")
                .then()
                .statusCode(200)
                .body("results.size()", greaterThan(0))
                .body("results[0].path", notNullValue())
                .body("results[0].score", greaterThan(0f))
                .body("results[0].matchedKeywords", notNullValue());
    }

    @Test
    void testSearchDocumentsWithPagination() {
        seedDocFilesMultiple();
        seedKeywordIndexMultiple();
        given()
                .queryParam("version", "3.27")
                .queryParam("keywords", "quarkus")
                .queryParam("limit", 1)
                .queryParam("offset", 0)
                .when().get("/api/documents")
                .then()
                .statusCode(200)
                .body("results.size()", is(1))
                .body("totalCount", equalTo(2));
    }

    @Test
    void testSearchDocumentsWithExtensionFilter() {
        seedDocFilesMultiple();
        seedKeywordIndexWithExtensions();
        given()
                .queryParam("version", "3.27")
                .queryParam("keywords", "security")
                .queryParam("extension", "quarkus-core")
                .when().get("/api/documents")
                .then()
                .statusCode(200)
                .body("results.size()", is(1))
                .body("results[0].extension", equalTo("quarkus-core"));
    }

    @Test
    void testPathTakesPrecedenceOverKeywords() {
        seedDocFile();
        given()
                .queryParam("version", "3.27")
                .queryParam("path", "security.adoc")
                .queryParam("keywords", "something")
                .when().get("/api/documents")
                .then()
                .statusCode(200)
                .body("title", equalTo("Security Guide"))
                .body("path", equalTo("security.adoc"));
    }

    // --- Brief mode tests ---

    @Test
    void testSearchDocumentsWithBriefMode() {
        seedDocFile();
        seedKeywordIndex();
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
                .body("results[0].subject", notNullValue())
                .body("results[0].score", greaterThan(0f))
                .body("results[0].matchedKeywords", notNullValue())
                .body("results[0].sections", nullValue())
                .body("results[0].codeBlocks", nullValue());
    }

    @Test
    void testSearchDocumentsWithBriefModeHasDescription() {
        seedDocFile();
        seedKeywordIndex();
        given()
                .queryParam("version", "3.27")
                .queryParam("keywords", "security")
                .queryParam("brief", "true")
                .when().get("/api/documents")
                .then()
                .statusCode(200)
                .body("results[0].description", notNullValue());
    }

    @Test
    void testSearchDocumentsWithoutBriefReturnsSections() {
        seedDocFile();
        seedKeywordIndex();
        given()
                .queryParam("version", "3.27")
                .queryParam("keywords", "security")
                .queryParam("brief", "false")
                .when().get("/api/documents")
                .then()
                .statusCode(200)
                .body("results[0].sections", notNullValue())
                .body("results[0].sections.size()", greaterThan(0));
    }

    @Test
    void testBriefIgnoredInPathMode() {
        seedDocFile();
        given()
                .queryParam("version", "3.27")
                .queryParam("path", "security.adoc")
                .queryParam("brief", "true")
                .when().get("/api/documents")
                .then()
                .statusCode(200)
                .body("title", equalTo("Security Guide"))
                .body("sections", notNullValue())
                .body("sections.size()", greaterThan(0));
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

    private void seedDocFileWithCode() {
        String docContent = """
                = REST Guide
                
                == Creating Endpoints
                Here is an example:
                
                [source,java]
                ----
                @Path("/hello")
                public class HelloResource {
                    @GET
                    public String hello() {
                        return "Hello";
                    }
                }
                ----
                
                That's how you create an endpoint.
                """;
        docStore.write("3.27", "rest.adoc", docContent);
    }

    private void seedKeywordIndex() {
        KeywordIndex index = new KeywordIndex(List.of(
                new FileKeywordEntry("security.adoc",
                        List.of(new KeywordScore("security", 15)),
                        List.of(new SectionKeywordEntry("Overview", 4, 8,
                                List.of(new KeywordScore("security", 12)))))
        ));
        keywordIndexStore.write("3.27", index);
    }

    @Test
    void testGetDocumentByPathReturns400ForUnknownVersion() {
        given()
                .queryParam("version", "nonexistent")
                .queryParam("path", "security.adoc")
                .when().get("/api/documents")
                .then()
                .statusCode(400)
                .body("detail", containsString("Unknown version"));
    }

    @Test
    void testSearchDocumentsReturns400ForUnknownVersion() {
        given()
                .queryParam("version", "nonexistent")
                .queryParam("keywords", "security")
                .when().get("/api/documents")
                .then()
                .statusCode(400)
                .body("detail", containsString("Unknown version"));
    }

    @Test
    void testSearchDocumentsReturns400ForUnknownSubject() {
        seedDocFile();
        seedKeywordIndex();
        given()
                .queryParam("version", "3.27")
                .queryParam("keywords", "security")
                .queryParam("subject", "nonexistent-subject")
                .when().get("/api/documents")
                .then()
                .statusCode(400)
                .body("detail", containsString("Unknown subject"));
    }

    // --- Batch endpoint tests ---

    @Test
    void testBatchRetrievalAllDocsFound() {
        seedDocFile();
        seedDocFileWithCode();
        BatchDocumentRequest request = new BatchDocumentRequest(
                List.of("security.adoc", "rest.adoc"), "3.27", false);
        given()
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/api/documents/batch")
                .then()
                .statusCode(200)
                .body("documents.size()", is(2))
                .body("errors.size()", is(0))
                .body("requestedCount", is(2))
                .body("retrievedCount", is(2))
                .body("errorCount", is(0));
    }

    @Test
    void testBatchRetrievalPartialFailure() {
        seedDocFile();
        BatchDocumentRequest request = new BatchDocumentRequest(
                List.of("security.adoc", "nonexistent.adoc"), "3.27", false);
        given()
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/api/documents/batch")
                .then()
                .statusCode(200)
                .body("documents.size()", is(1))
                .body("errors.size()", is(1))
                .body("errors[0].path", equalTo("nonexistent.adoc"))
                .body("errors[0].reason", equalTo("Document not found"))
                .body("retrievedCount", is(1))
                .body("errorCount", is(1));
    }

    @Test
    void testBatchRetrievalAllNotFound() {
        seedDocFile(); // seed to make version 3.27 exist
        BatchDocumentRequest request = new BatchDocumentRequest(
                List.of("missing1.adoc", "missing2.adoc"), "3.27", false);
        given()
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/api/documents/batch")
                .then()
                .statusCode(404)
                .body("detail", equalTo("None of the requested documents were found"));
    }

    @Test
    void testBatchRetrievalEmptyPaths() {
        BatchDocumentRequest request = new BatchDocumentRequest(
                List.of(), "main", false);
        given()
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/api/documents/batch")
                .then()
                .statusCode(400)
                .body("detail", equalTo("paths must not be empty"));
    }

    @Test
    void testBatchRetrievalNullRequestBody() {
        given()
                .contentType(ContentType.JSON)
                .when()
                .post("/api/documents/batch")
                .then()
                .statusCode(400)
                .body("detail", equalTo("Request body is required"));
    }

    @Test
    void testBatchRetrievalExceedsMaxSize() {
        List<String> paths = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            paths.add("doc" + i + ".adoc");
        }
        BatchDocumentRequest request = new BatchDocumentRequest(paths, "main", false);
        given()
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/api/documents/batch")
                .then()
                .statusCode(400)
                .body("detail", containsString("paths must not exceed"));
    }

    @Test
    void testBatchRetrievalPathTraversal() {
        BatchDocumentRequest request = new BatchDocumentRequest(
                List.of("../secret.adoc"), "main", false);
        given()
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/api/documents/batch")
                .then()
                .statusCode(400)
                .body("detail", containsString(".."));
    }

    @Test
    void testBatchRetrievalUnknownVersion() {
        BatchDocumentRequest request = new BatchDocumentRequest(
                List.of("security.adoc"), "nonexistent", false);
        given()
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/api/documents/batch")
                .then()
                .statusCode(400)
                .body("detail", containsString("Unknown version"));
    }

    @Test
    void testBatchRetrievalBriefMode() {
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
                .body("documents[0].title", equalTo("Security Guide"))
                .body("documents[0].description", notNullValue())
                .body("documents[0].sections", nullValue())
                .body("documents[0].codeBlocks", nullValue());
    }

    @Test
    void testBatchRetrievalDeduplicatesPaths() {
        seedDocFile();
        BatchDocumentRequest request = new BatchDocumentRequest(
                List.of("security.adoc", "security.adoc"), "3.27", false);
        given()
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/api/documents/batch")
                .then()
                .statusCode(200)
                .body("documents.size()", is(1))
                .body("requestedCount", is(1))
                .body("retrievedCount", is(1));
    }
}
