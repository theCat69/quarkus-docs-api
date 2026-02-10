package com.fvd.docs.resources;

import com.fvd.docs.stores.DocStore;
import com.fvd.indexs.stores.SqliteSchemaInitializer;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
class DocsResourceTest {

    @Inject
    SqliteSchemaInitializer schemaInitializer;

    @Inject
    DocStore docStore;

    @BeforeEach
    void cleanTestCache() throws IOException {
        var cachePath = Path.of("build/test-cache").toFile();
        if (cachePath.exists()) {
            FileUtils.cleanDirectory(cachePath);
        }
        // Re-initialize schema after cleaning (DB file was deleted)
        schemaInitializer.resetSchema();
    }

    @Test
    void testDocEndpointMissingVersion() {
        given()
            .queryParam("path", "_versions/3.27/guides/security-overview.adoc")
            .when().get("/api/doc")
            .then()
                .statusCode(200)
                .body("path", equalTo("_versions/3.27/guides/security-overview.adoc"))
                .body("format", equalTo("asciidoc"));
    }

    @Test
    void testDocEndpointMissingPath() {
        given()
            .queryParam("version", "3.27")
            .when().get("/api/doc")
            .then()
                .statusCode(400);
    }

    @Test
    void testDocEndpointPathTraversal() {
        given()
            .queryParam("version", "3.27")
            .queryParam("path", "../../etc/passwd")
            .when().get("/api/doc")
            .then()
                .statusCode(400);
    }

    @Test
    void testDocEndpointReturnsDecodedContent() {
        given()
            .queryParam("version", "3.27")
            .queryParam("path", "_versions/3.27/guides/security-overview.adoc")
            .when().get("/api/doc")
            .then()
                .statusCode(200)
                .body("path", equalTo("_versions/3.27/guides/security-overview.adoc"))
                .body("format", equalTo("asciidoc"))
                .body("content", equalTo("= Quarkus Security overview\n\nQuarkus Security is a framework that provides the architecture."));
    }

    @Test
    void testDocEndpointNotFound() {
        given()
            .queryParam("version", "3.27")
            .queryParam("path", "_versions/3.27/guides/nonexistent.adoc")
            .when().get("/api/doc")
            .then()
                .statusCode(404);
    }

    @Test
    void testDocEndpointDefaultsToMainVersionWithCachedDoc() {
        docStore.write("main", "security.adoc", "= Security Guide\nContent for main version.");
        given()
            .queryParam("path", "security.adoc")
            .when().get("/api/doc")
            .then()
                .statusCode(200)
                .body("path", equalTo("security.adoc"))
                .body("content", equalTo("= Security Guide\nContent for main version."))
                .body("format", equalTo("asciidoc"));
    }

    @Test
    void testDocEndpointExplicitVersionStillWorks() {
        given()
            .queryParam("version", "3.27")
            .queryParam("path", "_versions/3.27/guides/security-overview.adoc")
            .when().get("/api/doc")
            .then()
                .statusCode(200)
                .body("path", equalTo("_versions/3.27/guides/security-overview.adoc"))
                .body("format", equalTo("asciidoc"));
    }

}
