package com.fvd.api.resources;

import com.fvd.api.dto.BatchDocumentRequest;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class BatchDocumentBriefTest extends AbstractApiResourceTest {

    @Test
    void testBatchBriefTrueOmitsSectionsAndCodeBlocks() {
        seedDocWithSectionsAndCode();
        BatchDocumentRequest request = new BatchDocumentRequest(
                List.of("security.adoc"), "3.27", true);
        given()
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/api/documents/batch")
                .then()
                .statusCode(200)
                .body("documents.size()", is(1))
                .body("documents[0].title", equalTo("Security Guide"))
                .body("documents[0].description", notNullValue())
                .body("documents[0].path", equalTo("security.adoc"))
                .body("documents[0]", not(hasKey("sections")))
                .body("documents[0]", not(hasKey("codeBlocks")));
    }

    @Test
    void testBatchBriefFalseReturnsSectionsAndCodeBlocks() {
        seedDocWithSectionsAndCode();
        BatchDocumentRequest request = new BatchDocumentRequest(
                List.of("security.adoc"), "3.27", false);
        given()
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/api/documents/batch")
                .then()
                .statusCode(200)
                .body("documents.size()", is(1))
                .body("documents[0].title", equalTo("Security Guide"))
                .body("documents[0].sections", notNullValue())
                .body("documents[0].sections.size()", greaterThan(0))
                .body("documents[0].codeBlocks", notNullValue())
                .body("documents[0].codeBlocks.size()", greaterThan(0));
    }

    @Test
    void testBatchBriefTrueWithPartialFailure() {
        seedDocWithSectionsAndCode();
        BatchDocumentRequest request = new BatchDocumentRequest(
                List.of("security.adoc", "nonexistent.adoc"), "3.27", true);
        given()
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/api/documents/batch")
                .then()
                .statusCode(200)
                .body("documents.size()", is(1))
                .body("documents[0].title", equalTo("Security Guide"))
                .body("documents[0]", not(hasKey("sections")))
                .body("documents[0]", not(hasKey("codeBlocks")))
                .body("errors.size()", is(1))
                .body("errors[0].path", equalTo("nonexistent.adoc"))
                .body("requestedCount", is(2))
                .body("retrievedCount", is(1))
                .body("errorCount", is(1));
    }

    @Test
    void testBatchFieldSelectionReturnsOnlySelectedFields() {
        seedDocWithSectionsAndCode();
        BatchDocumentRequest request = new BatchDocumentRequest(
                List.of("security.adoc"), "3.27", false);
        given()
                .contentType(ContentType.JSON)
                .body(request)
                .queryParam("fields", "documents,retrievedCount")
                .when()
                .post("/api/documents/batch")
                .then()
                .statusCode(200)
                .body("$", hasKey("documents"))
                .body("$", hasKey("retrievedCount"))
                .body("$", not(hasKey("errors")))
                .body("$", not(hasKey("requestedCount")))
                .body("$", not(hasKey("errorCount")));
    }

    @Test
    void testBatchBriefResponseIsSmallerThanFullResponse() {
        seedDocWithSectionsAndCode();

        BatchDocumentRequest fullRequest = new BatchDocumentRequest(
                List.of("security.adoc"), "3.27", false);
        Response fullResponse = given()
                .contentType(ContentType.JSON)
                .body(fullRequest)
                .when()
                .post("/api/documents/batch");
        fullResponse.then().statusCode(200);
        int fullSize = fullResponse.body().asByteArray().length;

        BatchDocumentRequest briefRequest = new BatchDocumentRequest(
                List.of("security.adoc"), "3.27", true);
        Response briefResponse = given()
                .contentType(ContentType.JSON)
                .body(briefRequest)
                .when()
                .post("/api/documents/batch");
        briefResponse.then().statusCode(200);
        int briefSize = briefResponse.body().asByteArray().length;

        assertThat(briefSize, lessThan(fullSize));
        assertThat(fullSize, greaterThan(briefSize * 2));
    }

    @Test
    void testBatchBriefTrueWithInvalidFieldsReturns400() {
        seedDocWithSectionsAndCode();
        BatchDocumentRequest request = new BatchDocumentRequest(
                List.of("security.adoc"), "3.27", true);
        given()
                .contentType(ContentType.JSON)
                .body(request)
                .queryParam("fields", "invalidField")
                .when()
                .post("/api/documents/batch")
                .then()
                .statusCode(400)
                .body("detail", containsString("Unknown field(s): invalidField"))
                .body("detail", containsString("Available fields:"));
    }

    // --- Seed helper ---

    private void seedDocWithSectionsAndCode() {
        String docContent = """
                = Security Guide
                :description: Introduction to security features.

                == Overview
                This is the overview section.
                It covers security basics.

                == Configuration
                Config details here with examples.

                [source,java]
                ----
                @RolesAllowed("admin")
                public class SecureResource {
                    @GET
                    public String secure() {
                        return "Secured";
                    }
                }
                ----

                That concludes the configuration.
                """;
        docStore.write("3.27", "security.adoc", docContent);
    }
}
