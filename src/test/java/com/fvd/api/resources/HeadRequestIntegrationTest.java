package com.fvd.api.resources;

import com.fvd.indexs.model.DocChunk;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

@QuarkusTest
class HeadRequestIntegrationTest extends AbstractApiResourceTest {

    @Test
    void testGetSearchIncludesTotalCountHeader() {
        seedSearchChunksForHead();
        given()
                .queryParam("version", "3.27")
                .queryParam("q", "security")
                .when().get("/api/search")
                .then()
                .statusCode(200)
                .header("X-Total-Count", notNullValue());
    }

    @Test
    void testHeadSearchReturnsTotalCountAndNoBody() {
        seedSearchChunksForHead();
        given()
                .queryParam("version", "3.27")
                .queryParam("q", "security")
                .when().head("/api/search")
                .then()
                .statusCode(200)
                .header("X-Total-Count", notNullValue())
                .body(emptyOrNullString());
    }

    @Test
    void testHeadDocumentPathReturns200WithoutTotalCount() {
        docStore.write("3.27", "security-overview.adoc",
                "= Security Overview\nContent about security.");
        given()
                .queryParam("version", "3.27")
                .queryParam("path", "security-overview.adoc")
                .when().head("/api/documents")
                .then()
                .statusCode(200)
                .header("X-Total-Count", nullValue());
    }

    @Test
    void testGetCatalogDoesNotHaveTotalCountHeader() {
        seedDocFilesMultiple();
        given()
                .queryParam("version", "3.27")
                .when().get("/api/catalog")
                .then()
                .statusCode(200)
                .header("X-Total-Count", nullValue());
    }

    private void seedSearchChunksForHead() {
        seedDocFilesMultiple();
        seedDocChunks("3.27", List.of(
                new DocChunk("head-chunk-1", "3.27", "security.adoc",
                        "Security", "Overview",
                        "https://quarkus.io/guides/security",
                        List.of("security"), List.of("quarkus-core"),
                        "Security overview",
                        "Content about security and quarkus applications.")
        ));
    }
}
