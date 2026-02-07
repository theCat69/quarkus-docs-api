package com.fvd;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

import com.github.tomakehurst.wiremock.WireMockServer;

import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

@QuarkusTest
@ConnectWireMock
class DocsResourceTest {

    WireMockServer wiremock;

    @BeforeEach
    void cleanTestCache() throws IOException {
        Path testCache = Path.of("build/test-cache");
        if (Files.exists(testCache)) {
            Files.walkFileTree(testCache, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    @Test
    void testHealthEndpoint() {
        given()
            .when().get("/api/health")
            .then()
                .statusCode(200)
                .body("status", is("UP"));
    }

    @Test
    void testIndexEndpointMissingVersion() {
        given()
            .when().get("/api/index")
            .then()
                .statusCode(400)
                .body("status", is(400))
                .body("message", not(emptyOrNullString()));
    }

    @Test
    void testIndexEndpointEmptyVersion() {
        given()
            .queryParam("version", "")
            .when().get("/api/index")
            .then()
                .statusCode(400);
    }

    @Test
    void testIndexEndpointInvalidVersionChars() {
        given()
            .queryParam("version", "../etc/passwd")
            .when().get("/api/index")
            .then()
                .statusCode(400);
    }

    @Test
    void testIndexEndpointValidVersionReturnsIndex() {
        given()
            .queryParam("version", "3.21")
            .when().get("/api/index")
            .then()
                .statusCode(200)
                .body("[0].name", equalTo("security-overview.adoc"))
                .body("[1].name", equalTo("config.adoc"))
                .body("[2].name", equalTo("cdi.adoc"))
                .body("size()", is(3));
    }

    @Test
    void testDocEndpointMissingVersion() {
        given()
            .queryParam("path", "docs/src/main/asciidoc/security-overview.adoc")
            .when().get("/api/doc")
            .then()
                .statusCode(400);
    }

    @Test
    void testDocEndpointMissingPath() {
        given()
            .queryParam("version", "3.21")
            .when().get("/api/doc")
            .then()
                .statusCode(400);
    }

    @Test
    void testDocEndpointPathTraversal() {
        given()
            .queryParam("version", "3.21")
            .queryParam("path", "../../etc/passwd")
            .when().get("/api/doc")
            .then()
                .statusCode(400);
    }

    @Test
    void testDocEndpointReturnsDecodedContent() {
        given()
            .queryParam("version", "3.21")
            .queryParam("path", "docs/src/main/asciidoc/security-overview.adoc")
            .when().get("/api/doc")
            .then()
                .statusCode(200)
                .body("path", equalTo("docs/src/main/asciidoc/security-overview.adoc"))
                .body("format", equalTo("asciidoc"))
                .body("content", equalTo("= Quarkus Security overview\n\nQuarkus Security is a framework that provides the architecture."));
    }

    @Test
    void testDocEndpointNotFound() {
        given()
            .queryParam("version", "3.21")
            .queryParam("path", "docs/src/main/asciidoc/nonexistent.adoc")
            .when().get("/api/doc")
            .then()
                .statusCode(404);
    }

    @Test
    void testSearchFilesEndpointMissingVersion() {
        given()
            .queryParam("keywords", "oidc")
            .when().get("/api/search/files")
            .then()
                .statusCode(400);
    }

    @Test
    void testSearchFilesEndpointMissingKeywords() {
        given()
            .queryParam("version", "3.21")
            .when().get("/api/search/files")
            .then()
                .statusCode(400);
    }

    @Test
    void testSearchFilesEndpointValid() {
        given()
            .queryParam("version", "3.21")
            .queryParam("keywords", "oidc,security")
            .when().get("/api/search/files")
            .then()
                .statusCode(200)
                .body("results.size()", is(0));
    }

    @Test
    void testSearchSectionsEndpointMissingVersion() {
        given()
            .queryParam("keywords", "oidc")
            .queryParam("filePaths", "docs/src/main/asciidoc/security-oidc.adoc")
            .when().get("/api/search/sections")
            .then()
                .statusCode(400);
    }

    @Test
    void testSearchSectionsEndpointMissingKeywords() {
        given()
            .queryParam("version", "3.21")
            .queryParam("filePaths", "docs/src/main/asciidoc/security-oidc.adoc")
            .when().get("/api/search/sections")
            .then()
                .statusCode(400);
    }

    @Test
    void testSearchSectionsEndpointMissingFilePaths() {
        given()
            .queryParam("version", "3.21")
            .queryParam("keywords", "oidc")
            .when().get("/api/search/sections")
            .then()
                .statusCode(400);
    }

    @Test
    void testSearchSectionsEndpointFilePathsTraversal() {
        given()
            .queryParam("version", "3.21")
            .queryParam("keywords", "oidc")
            .queryParam("filePaths", "../../etc/passwd")
            .when().get("/api/search/sections")
            .then()
                .statusCode(400);
    }

    @Test
    void testSearchSectionsEndpointValid() {
        given()
            .queryParam("version", "3.21")
            .queryParam("keywords", "oidc")
            .queryParam("filePaths", "docs/src/main/asciidoc/security-oidc.adoc")
            .when().get("/api/search/sections")
            .then()
                .statusCode(200)
                .body("results.size()", is(0));
    }
}
