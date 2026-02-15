package com.fvd.api.resources;

import com.fvd.cache.services.WarmupStatusTracker;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.is;

@QuarkusTest
class StatusResourceTest extends AbstractApiResourceTest {

    @Inject
    WarmupStatusTracker warmupStatusTracker;

    @BeforeEach
    void resetTracker() {
        warmupStatusTracker.reset();
    }

    @Test
    void testStatusEndpointReturns503WhenNotReady() {
        // In test profile, warmup never runs, so status should be not ready
        given()
                .when().get("/api/status")
                .then()
                .statusCode(503)
                .body("ready", equalTo(false))
                .body("warmupProgress", notNullValue());
    }

    @Test
    void testStatusEndpointReturns200WhenReady() {
        warmupStatusTracker.warmupCompleted();

        given()
                .when().get("/api/status")
                .then()
                .statusCode(200)
                .body("ready", equalTo(true))
                .body("cachedVersions", notNullValue())
                .body("warmupProgress", notNullValue())
                .body("warmupProgress.completed", notNullValue())
                .body("warmupProgress.total", notNullValue())
                .body("warmupProgress.versionsCompleted", notNullValue());
    }

    @Test
    void testStatusEndpointReturnsWarmupProgress() {
        warmupStatusTracker.warmupStarted(List.of("3.20", "3.27", "main"));
        warmupStatusTracker.versionStarted("3.20");
        warmupStatusTracker.versionCompleted("3.20");
        warmupStatusTracker.versionStarted("3.27");

        given()
                .when().get("/api/status")
                .then()
                .statusCode(503)
                .body("ready", equalTo(false))
                .body("warmupProgress.completed", equalTo(1))
                .body("warmupProgress.total", equalTo(3))
                .body("warmupProgress.versionsCompleted", hasItems("3.20"))
                .body("warmupProgress.currentVersion", equalTo("3.27"));
    }

    @Test
    void testStatusEndpointReturnsJsonContentType() {
        warmupStatusTracker.warmupCompleted();

        given()
                .when().get("/api/status")
                .then()
                .contentType("application/json");
    }

    @Test
    void testStatusEndpointReturnsCachedVersions() {
        warmupStatusTracker.warmupCompleted();
        docStore.write("main", "test.adoc", "= Test\nContent");

        given()
                .when().get("/api/status")
                .then()
                .statusCode(200)
                .body("cachedVersions", hasItems("main"));
    }

    @Test
    void testStatusEndpointCurrentVersionIsNullAfterWarmupCompleted() {
        warmupStatusTracker.warmupStarted(List.of("3.20"));
        warmupStatusTracker.versionStarted("3.20");
        warmupStatusTracker.versionCompleted("3.20");
        warmupStatusTracker.warmupCompleted();

        given()
                .when().get("/api/status")
                .then()
                .statusCode(200)
                .body("ready", equalTo(true))
                .body("warmupProgress.currentVersion", is(nullValue()));
    }
}
