package com.fvd.api.resources;

import com.fvd.indexs.indexers.FileKeywordEntry;
import com.fvd.indexs.indexers.KeywordIndex;
import com.fvd.indexs.indexers.KeywordScore;
import com.fvd.indexs.indexers.SectionKeywordEntry;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.restassured.RestAssured.given;
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
        seedKeywordIndex();
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
        seedKeywordIndex();
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
        seedKeywordIndexWithExtensionsAndSections();
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

    private void seedKeywordIndex() {
        KeywordIndex index = new KeywordIndex(List.of(
                new FileKeywordEntry("security-overview.adoc",
                        List.of(new KeywordScore("security", 15)),
                        List.of(new SectionKeywordEntry("Security Overview", 1, 10,
                                List.of(new KeywordScore("security", 12)))))
        ));
        keywordIndexStore.write("3.27", index);
    }

    private void seedKeywordIndexWithExtensionsAndSections() {
        KeywordIndex index = new KeywordIndex(List.of(
                new FileKeywordEntry("core-security.adoc",
                        List.of(new KeywordScore("security", 15)),
                        List.of(new SectionKeywordEntry("Core Security", 1, 10,
                                List.of(new KeywordScore("security", 12)))),
                        "quarkus-core"),
                new FileKeywordEntry("ext-security.adoc",
                        List.of(new KeywordScore("security", 10)),
                        List.of(new SectionKeywordEntry("Ext Security", 1, 10,
                                List.of(new KeywordScore("security", 8)))),
                        "quarkus-openapi-generator")
        ));
        keywordIndexStore.write("3.27", index);
    }
}
