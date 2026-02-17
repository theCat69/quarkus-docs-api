package com.fvd.api.resources;

import java.util.List;

import com.fvd.indexs.model.DocChunk;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

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
                .body("results.path", not(hasItem("security-overview.adoc")));
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

        // Seed doc chunks with overlapping topics for relatedness
        seedDocChunks(version, List.of(
                new DocChunk("related-chunk-1", version, "security-overview",
                        "Security Overview", "Overview",
                        "https://quarkus.io/guides/security-overview",
                        List.of("security", "oidc", "authentication"), List.of("quarkus-core"),
                        "Overview of security",
                        "Security overview covering basics of authentication and authorization."),
                new DocChunk("related-chunk-2", version, "security-oidc",
                        "OIDC Auth", "Overview",
                        "https://quarkus.io/guides/security-oidc",
                        List.of("security", "oidc", "authentication"), List.of("quarkus-core"),
                        "OIDC authentication guide",
                        "OIDC authentication guide covering OpenID Connect features."),
                new DocChunk("related-chunk-3", version, "config",
                        "Configuration", "Overview",
                        "https://quarkus.io/guides/config",
                        List.of("config", "security"), List.of("quarkus-core"),
                        "Configuration guide",
                        "Configuration guide covering application properties and settings.")
        ));
    }
}
