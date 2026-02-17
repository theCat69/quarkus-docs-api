package com.fvd.api.resources;

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

@QuarkusTest
class FieldSelectionIntegrationTest extends AbstractApiResourceTest {

    // --- Search endpoint field selection ---

    @Test
    void shouldReturnOnlyRequestedFieldsOnSearchResults() {
        seedDocAndChunkIndex();
        given()
                .queryParam("version", "3.27")
                .queryParam("q", "security")
                .queryParam("fields", "title,page")
                .when().get("/api/search")
                .then()
                .statusCode(200)
                .body("results[0].title", notNullValue())
                .body("results[0].page", notNullValue())
                .body("results[0]", not(hasKey("section")))
                .body("results[0]", not(hasKey("extensions")))
                .body("results[0]", not(hasKey("score")))
                .body("results[0]", not(hasKey("topics")))
                .body("results[0]", not(hasKey("summary")));
    }

    @Test
    void shouldPreserveEnvelopeFieldsWhenFieldsParamPresent() {
        seedDocAndChunkIndex();
        given()
                .queryParam("version", "3.27")
                .queryParam("q", "security")
                .queryParam("fields", "title,page")
                .when().get("/api/search")
                .then()
                .statusCode(200)
                .body("total", greaterThan(0))
                .body("results", notNullValue());
    }

    @Test
    void shouldReturnAllFieldsWhenFieldsParamOmitted() {
        seedDocAndChunkIndex();
        given()
                .queryParam("version", "3.27")
                .queryParam("q", "security")
                .when().get("/api/search")
                .then()
                .statusCode(200)
                .body("results[0].title", notNullValue())
                .body("results[0].page", notNullValue())
                .body("results[0].section", notNullValue())
                .body("results[0].score", greaterThan(0f))
                .body("results[0].topics", notNullValue())
                .body("results[0].summary", notNullValue());
    }

    @Test
    void shouldReturn400ForInvalidFieldOnSearch() {
        seedDocAndChunkIndex();
        given()
                .queryParam("version", "3.27")
                .queryParam("q", "security")
                .queryParam("fields", "nonexistent")
                .when().get("/api/search")
                .then()
                .statusCode(400)
                .body("detail", containsString("Unknown field(s): nonexistent"))
                .body("detail", containsString("Available fields:"));
    }

    @Test
    void shouldReturnSingleFieldOnSearch() {
        seedDocAndChunkIndex();
        given()
                .queryParam("version", "3.27")
                .queryParam("q", "security")
                .queryParam("fields", "score")
                .when().get("/api/search")
                .then()
                .statusCode(200)
                .body("results[0]", hasKey("score"))
                .body("results[0]", not(hasKey("title")))
                .body("results[0]", not(hasKey("page")));
    }

    @Test
    void shouldReturnAllFieldsWhenFieldsParamIsEmpty() {
        seedDocAndChunkIndex();
        given()
                .queryParam("version", "3.27")
                .queryParam("q", "security")
                .queryParam("fields", "")
                .when().get("/api/search")
                .then()
                .statusCode(200)
                .body("results[0].title", notNullValue())
                .body("results[0].page", notNullValue())
                .body("results[0].score", greaterThan(0f));
    }

    // --- Document endpoint field selection (search mode) ---

    @Test
    void shouldReturnFilteredFieldsOnDocumentSearch() {
        seedDocAndDocChunkIndex();
        given()
                .queryParam("version", "3.27")
                .queryParam("keywords", "security")
                .queryParam("fields", "title,score")
                .when().get("/api/documents")
                .then()
                .statusCode(200)
                .body("results[0]", hasKey("title"))
                .body("results[0]", hasKey("score"))
                .body("results[0]", not(hasKey("path")))
                .body("results[0]", not(hasKey("sections")))
                .body("results[0]", not(hasKey("codeBlocks")));
    }

    // --- Document endpoint field selection (path mode) ---

    @Test
    void shouldReturnFilteredFieldsOnSingleDocument() {
        seedDocFile();
        given()
                .queryParam("version", "3.27")
                .queryParam("path", "security.adoc")
                .queryParam("fields", "title,path")
                .when().get("/api/documents")
                .then()
                .statusCode(200)
                .body("title", equalTo("Security Guide"))
                .body("path", equalTo("security.adoc"))
                .body("$", not(hasKey("subject")))
                .body("$", not(hasKey("description")))
                .body("$", not(hasKey("sections")))
                .body("$", not(hasKey("codeBlocks")));
    }

    // --- Catalog endpoint field selection ---

    @Test
    void shouldReturnFilteredFieldsOnCatalog() {
        seedDocForCatalog();
        given()
                .queryParam("version", "3.27")
                .queryParam("fields", "versions")
                .when().get("/api/catalog")
                .then()
                .statusCode(200)
                .body("$", hasKey("versions"))
                .body("$", not(hasKey("subjects")))
                .body("$", not(hasKey("extensions")));
    }

    @Test
    void shouldReturn400ForInvalidFieldOnCatalog() {
        given()
                .queryParam("fields", "invalid")
                .when().get("/api/catalog")
                .then()
                .statusCode(400)
                .body("detail", containsString("Unknown field(s): invalid"));
    }

    // --- Related documents endpoint field selection ---

    @Test
    void shouldReturnFilteredFieldsOnRelatedDocuments() {
        seedRelatedDocsAndIndex();
        given()
                .queryParam("version", "3.27")
                .queryParam("path", "security-overview.adoc")
                .queryParam("fields", "title,path")
                .when().get("/api/documents/related")
                .then()
                .statusCode(200)
                .body("results[0]", hasKey("title"))
                .body("results[0]", hasKey("path"))
                .body("results[0]", not(hasKey("description")))
                .body("results[0]", not(hasKey("subject")))
                .body("results[0]", not(hasKey("extension")))
                .body("results[0]", not(hasKey("similarityScore")))
                .body("results[0]", not(hasKey("sharedKeywords")));
    }

    // --- Error response bypass ---

    @Test
    void shouldNotFilterErrorResponses() {
        given()
                .queryParam("version", "nonexistent")
                .queryParam("q", "security")
                .queryParam("fields", "title")
                .when().get("/api/search")
                .then()
                .statusCode(400)
                .body("detail", containsString("Unknown version"))
                .body("status", is(400));
    }

    // --- Seed helpers ---

    private void seedDocAndChunkIndex() {
        seedDocFile();
        seedDocChunks("3.27", List.of(
                new DocChunk("field-sel-chunk-1", "3.27", "security",
                        "Security Guide", "Overview",
                        "https://quarkus.io/guides/security",
                        List.of("security"), List.of("quarkus-core"),
                        "Overview of security features",
                        "This is the overview section about security basics and authentication.")
        ));
    }

    private void seedDocAndDocChunkIndex() {
        seedDocFile();
        seedDocChunks("3.27", List.of(
                new DocChunk("field-sel-doc-chunk-1", "3.27", "security",
                        "Security Guide", "Overview",
                        "https://quarkus.io/guides/security",
                        List.of("security"), List.of("quarkus-core"),
                        "Overview of security features",
                        "This guide covers security basics and authentication in quarkus.")
        ));
    }

    private void seedDocFile() {
        String docContent = """
                = Security Guide
                :description: Introduction to security features.
                
                == Overview
                This is the overview section.
                It covers security basics.
                """;
        docStore.write("3.27", "security.adoc", docContent);
    }

    private void seedDocForCatalog() {
        docStore.write("3.27", "test.adoc", "= Test\nContent");
        docStore.write("main", "test.adoc", "= Test\nContent");
        seedDocChunks("3.27", List.of(
                new DocChunk("catalog-field-chunk-1", "3.27", "test",
                        "Test", "Overview",
                        "https://quarkus.io/guides/test",
                        List.of("test"), List.of("quarkus-core"),
                        "Test overview",
                        "Content about test features in quarkus.")
        ));
    }

    private void seedRelatedDocsAndIndex() {
        docStore.write("3.27", "security-overview.adoc",
                "= Security Overview\n:description: Overview of security\n\nSecurity basics.");
        docStore.write("3.27", "security-oidc.adoc",
                "= OIDC Auth\n:description: OIDC authentication\n\nOIDC guide.");
        seedDocChunks("3.27", List.of(
                new DocChunk("field-rel-chunk-1", "3.27", "security-overview",
                        "Security Overview", "Overview",
                        "https://quarkus.io/guides/security-overview",
                        List.of("security", "oidc", "authentication"), List.of("quarkus-core"),
                        "Overview of security",
                        "Security overview covering basics of authentication and authorization."),
                new DocChunk("field-rel-chunk-2", "3.27", "security-oidc",
                        "OIDC Auth", "Overview",
                        "https://quarkus.io/guides/security-oidc",
                        List.of("security", "oidc", "authentication"), List.of("quarkus-core"),
                        "OIDC authentication guide",
                        "OIDC authentication guide covering OpenID Connect features.")
        ));
    }
}
