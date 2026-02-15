package com.fvd.api.resources;

import com.fvd.indexs.indexers.CodeSampleEntry;
import com.fvd.indexs.indexers.CodeSampleIndex;
import com.fvd.indexs.indexers.FileKeywordEntry;
import com.fvd.indexs.indexers.KeywordIndex;
import com.fvd.indexs.indexers.KeywordScore;
import com.fvd.indexs.indexers.SectionKeywordEntry;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.List;

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
        seedDocAndKeywordIndex();
        given()
                .queryParam("version", "3.27")
                .queryParam("keywords", "security")
                .queryParam("fields", "title,path")
                .when().get("/api/search")
                .then()
                .statusCode(200)
                .body("results[0].title", notNullValue())
                .body("results[0].path", notNullValue())
                .body("results[0]", not(hasKey("subject")))
                .body("results[0]", not(hasKey("extension")))
                .body("results[0]", not(hasKey("score")))
                .body("results[0]", not(hasKey("matchedKeywords")))
                .body("results[0]", not(hasKey("snippet")));
    }

    @Test
    void shouldPreserveEnvelopeFieldsWhenFieldsParamPresent() {
        seedDocAndKeywordIndex();
        given()
                .queryParam("version", "3.27")
                .queryParam("keywords", "security")
                .queryParam("fields", "title,path")
                .when().get("/api/search")
                .then()
                .statusCode(200)
                .body("totalCount", greaterThan(0))
                .body("returnedCount", greaterThan(0))
                .body("results", notNullValue());
    }

    @Test
    void shouldReturnAllFieldsWhenFieldsParamOmitted() {
        seedDocAndKeywordIndex();
        given()
                .queryParam("version", "3.27")
                .queryParam("keywords", "security")
                .when().get("/api/search")
                .then()
                .statusCode(200)
                .body("results[0].title", notNullValue())
                .body("results[0].path", notNullValue())
                .body("results[0].subject", notNullValue())
                .body("results[0].score", greaterThan(0f))
                .body("results[0].matchedKeywords", notNullValue())
                .body("results[0].snippet", notNullValue());
    }

    @Test
    void shouldReturn400ForInvalidFieldOnSearch() {
        seedDocAndKeywordIndex();
        given()
                .queryParam("version", "3.27")
                .queryParam("keywords", "security")
                .queryParam("fields", "nonexistent")
                .when().get("/api/search")
                .then()
                .statusCode(400)
                .body("detail", containsString("Unknown field(s): nonexistent"))
                .body("detail", containsString("Available fields:"));
    }

    @Test
    void shouldReturnSingleFieldOnSearch() {
        seedDocAndKeywordIndex();
        given()
                .queryParam("version", "3.27")
                .queryParam("keywords", "security")
                .queryParam("fields", "score")
                .when().get("/api/search")
                .then()
                .statusCode(200)
                .body("results[0]", hasKey("score"))
                .body("results[0]", not(hasKey("title")))
                .body("results[0]", not(hasKey("path")));
    }

    @Test
    void shouldReturnAllFieldsWhenFieldsParamIsEmpty() {
        seedDocAndKeywordIndex();
        given()
                .queryParam("version", "3.27")
                .queryParam("keywords", "security")
                .queryParam("fields", "")
                .when().get("/api/search")
                .then()
                .statusCode(200)
                .body("results[0].title", notNullValue())
                .body("results[0].path", notNullValue())
                .body("results[0].score", greaterThan(0f));
    }

    // --- Document endpoint field selection (search mode) ---

    @Test
    void shouldReturnFilteredFieldsOnDocumentSearch() {
        seedDocAndKeywordIndex();
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

    // --- Code samples endpoint field selection ---

    @Test
    void shouldReturnFilteredFieldsOnCodeSamples() {
        seedDocAndCodeSampleIndex();
        given()
                .queryParam("version", "3.27")
                .queryParam("keywords", "security")
                .queryParam("fields", "content,language")
                .when().get("/api/code-samples")
                .then()
                .statusCode(200)
                .body("results[0]", hasKey("content"))
                .body("results[0]", hasKey("language"))
                .body("results[0]", not(hasKey("documentPath")))
                .body("results[0]", not(hasKey("documentTitle")))
                .body("results[0]", not(hasKey("score")))
                .body("results[0]", not(hasKey("context")));
    }

    @Test
    void shouldReturn400ForInvalidFieldOnCodeSamples() {
        seedDocAndCodeSampleIndex();
        given()
                .queryParam("version", "3.27")
                .queryParam("keywords", "security")
                .queryParam("fields", "invalid")
                .when().get("/api/code-samples")
                .then()
                .statusCode(400)
                .body("detail", containsString("Unknown field(s): invalid"));
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
                .queryParam("keywords", "security")
                .queryParam("fields", "title")
                .when().get("/api/search")
                .then()
                .statusCode(400)
                .body("detail", containsString("Unknown version"))
                .body("status", is(400));
    }

    // --- Seed helpers ---

    private void seedDocAndKeywordIndex() {
        seedDocFile();
        KeywordIndex index = new KeywordIndex(List.of(
                new FileKeywordEntry("security.adoc",
                        List.of(new KeywordScore("security", 15)),
                        List.of(new SectionKeywordEntry("Overview", 4, 8,
                                List.of(new KeywordScore("security", 12)))))
        ));
        keywordIndexStore.write("3.27", index);
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

    private void seedDocAndCodeSampleIndex() {
        docStore.write("3.27", "security.adoc", "= Security Guide\nContent about security.");
        CodeSampleIndex index = new CodeSampleIndex(List.of(
                new CodeSampleEntry("security.adoc", "Authentication", "java",
                        "import io.quarkus.security.identity.SecurityIdentity;",
                        5, 10,
                        List.of(new KeywordScore("security", 15)))
        ));
        codeSampleIndexStore.write("3.27", index);
    }

    private void seedDocForCatalog() {
        docStore.write("3.27", "test.adoc", "= Test\nContent");
        docStore.write("main", "test.adoc", "= Test\nContent");
        KeywordIndex index = new KeywordIndex(List.of(
                new FileKeywordEntry("test.adoc",
                        List.of(new KeywordScore("test", 10)),
                        List.of())
        ));
        keywordIndexStore.write("3.27", index);
    }

    private void seedRelatedDocsAndIndex() {
        docStore.write("3.27", "security-overview.adoc",
                "= Security Overview\n:description: Overview of security\n\nSecurity basics.");
        docStore.write("3.27", "security-oidc.adoc",
                "= OIDC Auth\n:description: OIDC authentication\n\nOIDC guide.");
        KeywordIndex index = new KeywordIndex(List.of(
                new FileKeywordEntry("security-overview.adoc",
                        List.of(new KeywordScore("secur", 15),
                                new KeywordScore("oidc", 8)),
                        List.of()),
                new FileKeywordEntry("security-oidc.adoc",
                        List.of(new KeywordScore("secur", 12),
                                new KeywordScore("oidc", 15)),
                        List.of())
        ));
        keywordIndexStore.write("3.27", index);
    }
}
