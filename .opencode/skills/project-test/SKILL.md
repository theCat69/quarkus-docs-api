---
name: project-test
description: Project-specific testing guidelines, test framework conventions, patterns, and coverage requirements
---

# Project Testing Guidelines

This project uses **TDD** — tests are written or updated **before** implementation. Tests are the primary safety net and serve as living documentation of system behavior.

---

## Test Framework

| Tool | Role |
|------|------|
| JUnit 5 | Test runner and lifecycle annotations |
| `@QuarkusTest` | Full Quarkus application context for integration tests |
| RestAssured | HTTP assertions for integration tests |
| AssertJ | Fluent assertions for unit tests |
| Hamcrest | JSON body matchers in RestAssured assertions |
| Mockito (`5.18.0`) | Mocking for unit tests |
| mockito-junit-jupiter | JUnit 5 Mockito integration |
| WireMock (Quarkiverse `1.5.3`) | HTTP stubbing for external API calls |
| Quarkus DevServices | Auto-starts PostgreSQL (Testcontainers) in `%test` profile |

---

## Test Location & File Naming

- All test sources live in `src/test/java/com/fvd/`.
- Test class name = source class name + `Test` suffix (e.g., `SearchResourceTest`).
- Mirror the package structure of the class under test.

```
src/test/java/com/fvd/
  api/resources/   → SearchResourceTest, DocumentResourceTest, ...
  api/services/    → DocumentServiceTest, ...
  indexs/stores/   → DocChunkStoreTest, ...
  search/          → DocChunkSearchServiceTest, ...
  common/          → InputValidatorTest, ...
```

---

## Test Profile (`%test`)

The `%test` profile is automatically active when running `./gradlew test`. It configures:

| Setting | Value |
|---------|-------|
| Cache directory | `build/test-cache` (isolated from runtime) |
| PostgreSQL | Managed by Quarkus DevServices (Testcontainers) |
| REST clients | Pointed at WireMock (no real GitHub API calls) |
| Scheduler | Disabled |
| Quarkiverse ingestion | Disabled |
| Minimum keyword score | `1` (lowered for test data) |

---

## Test Types

### Integration Tests (`@QuarkusTest`)
For HTTP endpoints, CDI beans, configuration, and database interactions. Uses the full Quarkus application context.

```java
@QuarkusTest
class SearchResourceTest extends AbstractApiResourceTest {

    @BeforeEach
    void seedTestData() {
        seedDocChunks("main", List.of(...));
    }

    @Test
    void shouldReturnRankedResultsForSearchQuery() {
        given()
                .queryParam("q", "reactive")
                .queryParam("version", "main")
        .when()
                .get("/api/search")
        .then()
                .statusCode(200)
                .body("results.size()", greaterThan(0))
                .body("results[0].score", greaterThan(0f));
    }
}
```

### Unit Tests
For pure logic: parsing, validation, scoring, indexing. Plain JUnit 5, no CDI container. Use AssertJ for assertions and Mockito for dependency isolation.

```java
class InputValidatorTest {

    @Test
    void shouldRejectVersionWithDotDot() {
        assertThatThrownBy(() -> InputValidator.validateVersion("../etc"))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("..");
    }
}
```

---

## Writing Tests

- **One behavior per test method.** Keep tests focused and readable.
- Keep test data local to the test class — do not share mutable state between tests.
- Do not mix assertion styles within the same test class — pick AssertJ or Hamcrest and stay consistent.
- Use the **AAA pattern**: Arrange (set up mocks/data) → Act (call endpoint or method) → Assert (verify response).
- Test descriptive names: `shouldReturnBadRequestForEmptyKeywords`, `shouldReturnNotFoundWhenDocMissing`.

---

## Static Imports for Test Fluency

```java
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
```

---

## RestAssured Assertion Pattern (Integration Tests)

```java
given()
        .queryParam("keywords", "security,oidc")
        .queryParam("version", "main")
.when()
        .get("/api/search/files")
.then()
        .statusCode(200)
        .body("results.size()", greaterThan(0));
```

For error responses:

```java
given()
        .queryParam("version", "main")
.when()
        .get("/api/search")
.then()
        .statusCode(400)
        .body("detail", containsString("must not be empty"));
```

---

## AssertJ Pattern (Unit Tests)

```java
assertThat(result).isNotNull();
assertThat(result.score()).isGreaterThan(0.0);
assertThat(result.matchedKeywords()).contains("security");
```

---

## Mocking & Fixtures

- Use `@InjectMock` (Quarkus CDI mocking) or Mockito `@ExtendWith(MockitoExtension.class)` for unit tests.
- Use WireMock for all external API stubbing (GitHub API, repository download clients). **Never make real external calls in tests.**
- WireMock stub mappings: `src/test/resources/mappings/`
- WireMock response bodies: `src/test/resources/__files/`
- WireMock port is dynamic, injected via `${quarkus.wiremock.devservices.port}`.

---

## Database Tests

- PostgreSQL is managed by Quarkus DevServices (Testcontainers) in the `%test` profile — no manual setup needed.
- Liquibase migrations run automatically at test startup (`quarkus.liquibase.migrate-at-start=true`).
- Seed test data in `@BeforeEach` — do not rely on persistent state from other tests.
- Verify FTS queries with known seed data inserted before each test.
- Test edge cases: empty search query, special characters, very long input, zero results.

---

## Coverage Requirements

- Aim for **80%+ coverage** on service and store layers.
- REST resources are covered by integration tests.
- Unit tests cover pure logic: parsing, validation, scoring, data transformation.

---

## Running Tests

| Task | Command |
|------|---------|
| All tests | `./gradlew test` |
| Single class | `./gradlew test --tests "com.fvd.api.resources.SearchResourceTest"` |
| Single method | `./gradlew test --tests "com.fvd.api.resources.SearchResourceTest.shouldReturnRankedResultsForSearchQuery"` |

**Never run** `./gradlew testNative` unless explicitly requested.

---

## What to Report After Running Tests

- Total tests: run / passed / failed / skipped
- For each failure: test class, method name, failure message
- For build failures (compilation errors): the full compilation error
