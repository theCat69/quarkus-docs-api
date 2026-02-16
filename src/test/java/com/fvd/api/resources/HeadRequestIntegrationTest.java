package com.fvd.api.resources;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

@QuarkusTest
class HeadRequestIntegrationTest extends AbstractApiResourceTest {

    @Test
    void testGetSearchIncludesTotalCountHeader() {
        seedKeywordIndexMultiple();
        seedDocFilesMultiple();
        given()
                .queryParam("version", "3.27")
                .queryParam("keywords", "security")
                .when().get("/api/search")
                .then()
                .statusCode(200)
                .header("X-Total-Count", notNullValue());
    }

    @Test
    void testHeadSearchReturnsTotalCountAndNoBody() {
        seedKeywordIndexMultiple();
        seedDocFilesMultiple();
        given()
                .queryParam("version", "3.27")
                .queryParam("keywords", "security")
                .when().head("/api/search")
                .then()
                .statusCode(200)
                .header("X-Total-Count", notNullValue())
                .body(emptyOrNullString());
    }

    @Test
    void testHeadCodeSamplesReturnsTotalCountHeader() {
        seedCodeSampleIndex();
        seedDocFilesMultiple();
        given()
                .queryParam("version", "3.27")
                .queryParam("keywords", "security")
                .when().head("/api/code-samples")
                .then()
                .statusCode(200)
                .header("X-Total-Count", notNullValue());
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
}
