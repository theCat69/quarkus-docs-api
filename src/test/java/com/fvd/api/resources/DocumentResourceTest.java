package com.fvd.api.resources;

import com.fvd.docs.stores.DocStore;
import com.fvd.indexs.indexers.FileKeywordEntry;
import com.fvd.indexs.indexers.KeywordIndex;
import com.fvd.indexs.indexers.KeywordScore;
import com.fvd.indexs.indexers.SectionKeywordEntry;
import com.fvd.indexs.stores.KeywordIndexStore;
import com.fvd.indexs.stores.SqliteSchemaInitializer;
import com.fvd.search.services.SearchService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class DocumentResourceTest {

    @Inject
    KeywordIndexStore keywordIndexStore;

    @Inject
    DocStore docStore;

    @Inject
    SqliteSchemaInitializer schemaInitializer;

    @Inject
    SearchService searchService;

    @BeforeEach
    void cleanTestCache() throws IOException {
        var cachePath = Path.of("build/test-cache").toFile();
        if (cachePath.exists()) {
            FileUtils.cleanDirectory(cachePath);
        }
        schemaInitializer.resetSchema();
        searchService.invalidateCache("3.27");
        searchService.invalidateCache("main");
    }

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
    void testGetDocumentByPathNotFound() {
        given()
                .queryParam("version", "3.27")
                .queryParam("path", "nonexistent.adoc")
                .when().get("/api/documents")
                .then()
                .statusCode(404)
                .body("message", containsString("Document not found"));
    }

    @Test
    void testGetDocumentByPathInvalid() {
        given()
                .queryParam("version", "3.27")
                .queryParam("path", "../../etc/passwd")
                .when().get("/api/documents")
                .then()
                .statusCode(400);
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
        seedDocFileMultiple();
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
        seedDocFileMultiple();
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

    // --- Error cases ---

    @Test
    void testMissingPathAndKeywords() {
        given()
                .queryParam("version", "3.27")
                .when().get("/api/documents")
                .then()
                .statusCode(400)
                .body("message", containsString("path"));
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

    private void seedDocFileMultiple() {
        docStore.write("3.27", "security.adoc", "= Security\nContent about security.");
        docStore.write("3.27", "config.adoc", "= Config\nContent about config.");
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

    private void seedKeywordIndexMultiple() {
        KeywordIndex index = new KeywordIndex(List.of(
                new FileKeywordEntry("security.adoc",
                        List.of(new KeywordScore("security", 15), new KeywordScore("quarkus", 8)),
                        List.of()),
                new FileKeywordEntry("config.adoc",
                        List.of(new KeywordScore("config", 10), new KeywordScore("quarkus", 5)),
                        List.of())
        ));
        keywordIndexStore.write("3.27", index);
    }

    private void seedKeywordIndexWithExtensions() {
        KeywordIndex index = new KeywordIndex(List.of(
                new FileKeywordEntry("security.adoc",
                        List.of(new KeywordScore("security", 15)),
                        List.of(),
                        "quarkus-core"),
                new FileKeywordEntry("config.adoc",
                        List.of(new KeywordScore("security", 10)),
                        List.of(),
                        "quarkus-openapi-generator")
        ));
        keywordIndexStore.write("3.27", index);
    }
}
