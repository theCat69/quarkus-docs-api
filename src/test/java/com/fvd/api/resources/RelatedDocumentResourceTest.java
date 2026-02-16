package com.fvd.api.resources;

import com.fvd.indexs.indexers.FileKeywordEntry;
import com.fvd.indexs.indexers.KeywordIndex;
import com.fvd.indexs.indexers.KeywordScore;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class RelatedDocumentResourceTest extends AbstractApiResourceTest {

    private static final String RELATED_PATH = "/api/documents/related";

    @Test
    void testGetRelatedDocumentsReturnsRankedResults() {
        seedRelatedDocsAndIndex();
        given()
                .queryParam("version", "3.27")
                .queryParam("path", "security-overview.adoc")
                .when().get(RELATED_PATH)
                .then()
                .statusCode(200)
                .body("results", notNullValue())
                .body("results.size()", greaterThan(0))
                .body("totalCount", greaterThan(0))
                .body("returnedCount", greaterThan(0))
                .body("offset", is(0))
                .body("hasMore", notNullValue());
    }

    @Test
    void testGetRelatedDocumentsFilterBySubject() {
        seedRelatedDocsAndIndex();
        given()
                .queryParam("version", "3.27")
                .queryParam("path", "security-overview.adoc")
                .queryParam("subject", "security")
                .when().get(RELATED_PATH)
                .then()
                .statusCode(200)
                .body("results", notNullValue());
    }

    @Test
    void testGetRelatedDocumentsRespectsLimit() {
        seedRelatedDocsAndIndex();
        given()
                .queryParam("version", "3.27")
                .queryParam("path", "security-overview.adoc")
                .queryParam("limit", 1)
                .when().get(RELATED_PATH)
                .then()
                .statusCode(200)
                .body("results.size()", lessThanOrEqualTo(1));
    }

    @Test
    void testGetRelatedDocumentsReturns404ForNonexistentPath() {
        seedRelatedDocsAndIndex();
        given()
                .queryParam("version", "3.27")
                .queryParam("path", "nonexistent.adoc")
                .when().get(RELATED_PATH)
                .then()
                .statusCode(404)
                .body("detail", containsString("Document not found in index"));
    }

    @Test
    void testGetRelatedDocumentsReturns400WhenPathMissing() {
        given()
                .queryParam("version", "main")
                .when().get(RELATED_PATH)
                .then()
                .statusCode(400)
                .body("detail", containsString("path must not be empty"));
    }

    @Test
    void testGetRelatedDocumentsReturns400ForPathTraversal() {
        given()
                .queryParam("version", "main")
                .queryParam("path", "../etc/passwd")
                .when().get(RELATED_PATH)
                .then()
                .statusCode(400)
                .body("detail", containsString(".."));
    }

    @Test
    void testGetRelatedDocumentsReturns400ForUnknownVersion() {
        given()
                .queryParam("version", "nonexistent")
                .queryParam("path", "security-overview.adoc")
                .when().get(RELATED_PATH)
                .then()
                .statusCode(400)
                .body("detail", containsString("Unknown version"));
    }

    @Test
    void testGetRelatedDocumentsContainsSimilarityScoreBetweenZeroAndOne() {
        seedRelatedDocsAndIndex();
        given()
                .queryParam("version", "3.27")
                .queryParam("path", "security-overview.adoc")
                .when().get(RELATED_PATH)
                .then()
                .statusCode(200)
                .body("results.similarityScore", everyItem(greaterThanOrEqualTo(0.0f)))
                .body("results.similarityScore", everyItem(lessThanOrEqualTo(1.0f)));
    }

    @Test
    void testGetRelatedDocumentsContainsSharedKeywords() {
        seedRelatedDocsAndIndex();
        given()
                .queryParam("version", "3.27")
                .queryParam("path", "security-overview.adoc")
                .when().get(RELATED_PATH)
                .then()
                .statusCode(200)
                .body("results[0].sharedKeywords", notNullValue())
                .body("results[0].sharedKeywords.size()", greaterThan(0));
    }

    @Test
    void testGetRelatedDocumentsExcludesSourcePath() {
        seedRelatedDocsAndIndex();
        given()
                .queryParam("version", "3.27")
                .queryParam("path", "security-overview.adoc")
                .when().get(RELATED_PATH)
                .then()
                .statusCode(200)
                .body("results.path", not(org.hamcrest.Matchers.hasItem("security-overview.adoc")));
    }

    @Test
    void testGetRelatedDocumentsReturns400ForUnknownSubject() {
        seedRelatedDocsAndIndex();
        given()
                .queryParam("version", "3.27")
                .queryParam("path", "security-overview.adoc")
                .queryParam("subject", "nonexistent-subject")
                .when().get(RELATED_PATH)
                .then()
                .statusCode(400)
                .body("detail", containsString("Unknown subject"));
    }

    @Test
    void testGetRelatedDocumentsDefaultsToMainVersion() {
        // Seed on main version
        seedRelatedDocsAndIndexForVersion("main");
        given()
                .queryParam("path", "security-overview.adoc")
                .when().get(RELATED_PATH)
                .then()
                .statusCode(200)
                .body("results", notNullValue());
    }

    private void seedRelatedDocsAndIndex() {
        seedRelatedDocsAndIndexForVersion("3.27");
    }

    private void seedRelatedDocsAndIndexForVersion(String version) {
        // Seed doc files
        docStore.write(version, "security-overview.adoc",
                "= Security Overview\n:description: Overview of security\n\nSecurity basics.");
        docStore.write(version, "security-oidc.adoc",
                "= OIDC Auth\n:description: OIDC authentication\n\nOIDC guide.");
        docStore.write(version, "config.adoc",
                "= Configuration\n:description: Configuration guide\n\nConfig basics.");

        // Seed keyword index with shared keywords
        KeywordIndex index = new KeywordIndex(List.of(
                new FileKeywordEntry("security-overview.adoc",
                        List.of(new KeywordScore("secur", 15),
                                new KeywordScore("oidc", 8),
                                new KeywordScore("authent", 5)),
                        List.of(), "quarkus-core"),
                new FileKeywordEntry("security-oidc.adoc",
                        List.of(new KeywordScore("secur", 12),
                                new KeywordScore("oidc", 15),
                                new KeywordScore("authent", 10)),
                        List.of(), "quarkus-core"),
                new FileKeywordEntry("config.adoc",
                        List.of(new KeywordScore("config", 15),
                                new KeywordScore("secur", 2)),
                        List.of(), "quarkus-core")
        ));
        keywordIndexStore.write(version, index);
    }
}
