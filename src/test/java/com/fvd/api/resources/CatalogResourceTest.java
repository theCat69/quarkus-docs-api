package com.fvd.api.resources;

import java.util.List;

import com.fvd.indexs.model.DocChunk;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class CatalogResourceTest extends AbstractApiResourceTest {

    @Test
    void testCatalogEndpointDefaultVersion() {
        given()
                .when().get("/api/catalog")
                .then()
                .statusCode(200)
                .body("subjects", notNullValue())
                .body("extensions", notNullValue())
                .body("versions", notNullValue());
    }

    @Test
    void testCatalogEndpointWithVersion() {
        docStore.write("3.27", "test.adoc", "= Test\nContent");
        seedDocChunkIndex();
        given()
                .queryParam("version", "3.27")
                .when().get("/api/catalog")
                .then()
                .statusCode(200)
                .body("subjects", notNullValue())
                .body("extensions", notNullValue())
                .body("versions", notNullValue());
    }

    @Test
    void testCatalogEndpointReturnsSubjects() {
        docStore.write("3.27", "test.adoc", "= Test\nContent");
        seedDocChunkIndex();
        given()
                .queryParam("version", "3.27")
                .when().get("/api/catalog")
                .then()
                .statusCode(200)
                .body("subjects.size()", greaterThan(0))
                .body("subjects[0].name", notNullValue())
                .body("subjects[0].displayName", notNullValue());
    }

    @Test
    void testCatalogEndpointReturnsExtensions() {
        docStore.write("3.27", "test.adoc", "= Test\nContent");
        seedDocChunkIndexWithExtensionsAndSections();
        given()
                .queryParam("version", "3.27")
                .when().get("/api/catalog")
                .then()
                .statusCode(200)
                .body("extensions.size()", greaterThan(0))
                .body("extensions[0].name", notNullValue())
                .body("extensions[0].docCount", greaterThan(0));
    }

    @Test
    void testCatalogEndpointReturnsVersions() {
        docStore.write("3.27", "test.adoc", "= Test\nContent");
        docStore.write("main", "test.adoc", "= Test\nContent");

        given()
                .queryParam("version", "3.27")
                .when().get("/api/catalog")
                .then()
                .statusCode(200)
                .body("versions", hasItem("3.27"))
                .body("versions", hasItem("main"));
    }

    private void seedDocChunkIndex() {
        seedDocChunks("3.27", List.of(
                new DocChunk("catalog-chunk-1", "3.27", "security-overview",
                        "Security Overview", "Overview",
                        "https://quarkus.io/guides/security-overview",
                        List.of("security"), List.of("quarkus-core"),
                        "Overview of security features",
                        "This guide covers security basics and authentication in quarkus.")
        ));
    }

    private void seedDocChunkIndexWithExtensionsAndSections() {
        seedDocChunks("3.27", List.of(
                new DocChunk("catalog-ext-chunk-1", "3.27", "core-security",
                        "Core Security", "Overview",
                        "https://quarkus.io/guides/core-security",
                        List.of("security"), List.of("quarkus-core"),
                        "Core security overview",
                        "This guide covers core security features in quarkus."),
                new DocChunk("catalog-ext-chunk-2", "3.27", "ext-security",
                        "Ext Security", "Overview",
                        "https://quarkus.io/guides/ext-security",
                        List.of("security"), List.of("quarkus-openapi-generator"),
                        "Extension security overview",
                        "This guide covers security features in the openapi generator extension.")
        ));
    }

    @Test
    void testCatalogReturns400ForUnknownVersion() {
        given()
                .queryParam("version", "nonexistent")
                .when().get("/api/catalog")
                .then()
                .statusCode(400)
                .body("detail", containsString("Unknown version"));
    }
}
