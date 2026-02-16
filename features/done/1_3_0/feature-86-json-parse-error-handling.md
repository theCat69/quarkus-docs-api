# Feature 86: JSON Parse Error Handling (400 Instead of 500)

> **Dependencies**: None — this feature is independent. It follows the same `AbstractProblemDetailMapper` pattern established in Feature 68. No other features need to be implemented first.

## Summary

When a client sends a malformed JSON body to `POST /api/documents/batch` (e.g., `not json` with `Content-Type: application/json`), the API returns HTTP 500 with `"An unexpected error occurred"` because no exception mapper exists for Jackson's `JsonProcessingException`. This is a bug: malformed input is a client error (400), not a server error (500). This feature adds a `JsonProcessingExceptionMapper` that catches `com.fasterxml.jackson.core.JsonProcessingException` and returns a proper RFC 7807 `ProblemDetail` response with status 400 and a descriptive error message.

## User Story

As an **AI agent consuming this API through an MCP server**, I want the API to return HTTP 400 with a clear error message when I send malformed JSON, so that I can distinguish between "my request body is invalid" (400) and "the server has an internal problem" (500), and avoid triggering unnecessary retry logic for what is actually a client-side formatting error.

## Motivation

### Current Behavior (Bug)

```
POST /api/documents/batch
Content-Type: application/json

not json
```

**Response (500):**
```
HTTP/1.1 500 Internal Server Error
Content-Type: application/json

{
    "type": "about:blank",
    "title": "Internal Server Error",
    "status": 500,
    "detail": "An unexpected error occurred",
    "instance": "api/documents/batch",
    "timestamp": "2026-02-16T10:30:00Z"
}
```

The `GenericExceptionMapper` catches the `JsonProcessingException` (or its subclass `JsonParseException`) that Jackson throws when it fails to deserialize the request body, and returns a generic 500. The AI agent sees a 500, assumes a server-side failure, and may retry the same malformed request multiple times — wasting resources and never getting a useful error message.

### Desired Behavior (Fixed)

```
POST /api/documents/batch
Content-Type: application/json

not json
```

**Response (400):**
```
HTTP/1.1 400 Bad Request
Content-Type: application/json

{
    "type": "about:blank",
    "title": "Bad Request",
    "status": 400,
    "detail": "Invalid JSON request body",
    "instance": "api/documents/batch",
    "timestamp": "2026-02-16T10:30:00Z"
}
```

The AI agent sees 400, knows the request body was malformed, and can fix the request format before retrying.

### Additional Malformed JSON Scenarios

| Input | Jackson Exception | Current Status | Expected Status |
|-------|-------------------|----------------|-----------------|
| `not json` | `JsonParseException` | 500 | 400 |
| `{invalid}` | `JsonParseException` | 500 | 400 |
| `{"paths": "not-an-array"}` | `MismatchedInputException` | 500 | 400 |
| `{"paths": [1, 2, 3]}` | `MismatchedInputException` | 500 | 400 |
| `""` (empty string) | `MismatchedInputException` | 500 | 400 |
| `{` (truncated) | `JsonParseException` | 500 | 400 |

All of these are subclasses of `JsonProcessingException`, so a single mapper catches them all.

---

## Scope / Requirements

### R0: Jackson Exception Hierarchy

`com.fasterxml.jackson.core.JsonProcessingException` is the base class for all Jackson serialization/deserialization errors. Its key subclasses include:

| Exception | Extends | When thrown |
|-----------|---------|------------|
| `JsonParseException` | `JsonProcessingException` | Syntax errors (e.g., `not json`, `{invalid}`, truncated JSON) |
| `JsonMappingException` | `JsonProcessingException` | Type mismatches, unknown properties |
| `MismatchedInputException` | `JsonMappingException` | Type coercion failures (e.g., string where array expected) |
| `UnrecognizedPropertyException` | `JsonMappingException` | Unknown JSON fields (if `FAIL_ON_UNKNOWN_PROPERTIES` is enabled) |

By mapping `JsonProcessingException` (the base), the mapper catches **all** of these. No need for separate mappers per subclass.

### R1: Create `JsonProcessingExceptionMapper`

**New file:** `src/main/java/com/fvd/common/exceptions/JsonProcessingExceptionMapper.java`

**Package:** `com.fvd.common.exceptions`

Create a `@Provider` mapper extending `AbstractProblemDetailMapper<JsonProcessingException>`:

```java
package com.fvd.common.exceptions;

import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;

/**
 * Maps Jackson JsonProcessingException to RFC 7807 Problem Details response
 * with 400 Bad Request. Catches all JSON deserialization errors including
 * parse errors, type mismatches, and mapping failures.
 */
@Provider
@Slf4j
public class JsonProcessingExceptionMapper extends AbstractProblemDetailMapper<JsonProcessingException> {

    @Override
    protected Response.Status getStatus() {
        return Response.Status.BAD_REQUEST;
    }

    @Override
    protected String getTitle() {
        return "Bad Request";
    }

    @Override
    protected String getDetail(JsonProcessingException exception) {
        log.debug("JSON parse error: {}", exception.getOriginalMessage());
        return "Invalid JSON request body";
    }
}
```

**Key design decisions:**

1. **Static detail message:** The `getDetail()` method returns a fixed `"Invalid JSON request body"` string rather than exposing `exception.getMessage()`. Jackson's error messages can contain internal implementation details (class names, field paths, line/column numbers) that could leak server internals. A static message is safe and sufficient for AI agents — they need to know the body was malformed, not the exact parse failure position.

2. **Debug logging:** The original exception message is logged at `DEBUG` level via `log.debug()` so developers can diagnose issues in development without exposing details to clients. This follows the same pattern as `GenericExceptionMapper` which logs at `ERROR` — but JSON parse errors are client errors, so `DEBUG` is appropriate.

3. **`@Slf4j` annotation:** Uses Lombok's `@Slf4j` for the logger, consistent with `GenericExceptionMapper`.

4. **No `@Priority` needed:** JAX-RS selects the most specific `ExceptionMapper`. `JsonProcessingException` is more specific than `Exception`, so this mapper takes priority over `GenericExceptionMapper` automatically.

5. **Exception type choice:** Mapping `JsonProcessingException` (not `JsonParseException`) ensures all JSON deserialization errors are caught — including `JsonMappingException`, `MismatchedInputException`, and `UnrecognizedPropertyException`. This is the broadest useful catch without interfering with other mappers.

### R2: Verify No Conflict with Existing Mappers

The new mapper must not conflict with existing exception mappers:

| Existing Mapper | Exception Type | Conflict? |
|----------------|---------------|-----------|
| `GenericExceptionMapper` | `Exception` | No — `JsonProcessingException` is more specific |
| `InvalidInputExceptionMapper` | `InvalidInputException` | No — `InvalidInputException` does not extend `JsonProcessingException` |
| `ParamExceptionMapper` | `BadRequestException` | No — `JsonProcessingException` does not extend `BadRequestException` |
| `DocNotFoundExceptionMapper` | `DocNotFoundException` | No — unrelated hierarchy |
| `NotFoundExceptionMapper` | `NotFoundException` | No — unrelated hierarchy |
| `NotAllowedExceptionMapper` | `NotAllowedException` | No — unrelated hierarchy |
| `NotAcceptableExceptionMapper` | `NotAcceptableException` | No — unrelated hierarchy |
| `UpstreamExceptionMapper` | `UpstreamException` | No — unrelated hierarchy |

`JsonProcessingException` extends `java.io.IOException` which extends `java.lang.Exception`. The only mapper that could also catch it is `GenericExceptionMapper<Exception>`, but `JsonProcessingExceptionMapper<JsonProcessingException>` is more specific and wins.

### R3: Update Tests in `ProblemDetailErrorResponseTest`

**File:** `src/test/java/com/fvd/api/resources/ProblemDetailErrorResponseTest.java`

Add new test methods to verify the fix:

```java
@Test
void testMalformedJsonBodyReturnsBadRequest() {
    given()
            .contentType(ContentType.JSON)
            .body("not json")
            .when()
            .post("/api/documents/batch")
            .then()
            .statusCode(400)
            .body("type", equalTo("about:blank"))
            .body("title", equalTo("Bad Request"))
            .body("status", equalTo(400))
            .body("detail", equalTo("Invalid JSON request body"))
            .body("instance", containsString("documents/batch"))
            .body("timestamp", notNullValue());
}

@Test
void testTruncatedJsonBodyReturnsBadRequest() {
    given()
            .contentType(ContentType.JSON)
            .body("{\"paths\":")
            .when()
            .post("/api/documents/batch")
            .then()
            .statusCode(400)
            .body("title", equalTo("Bad Request"))
            .body("status", equalTo(400))
            .body("detail", equalTo("Invalid JSON request body"));
}

@Test
void testInvalidJsonSyntaxReturnsBadRequest() {
    given()
            .contentType(ContentType.JSON)
            .body("{invalid}")
            .when()
            .post("/api/documents/batch")
            .then()
            .statusCode(400)
            .body("title", equalTo("Bad Request"))
            .body("status", equalTo(400))
            .body("detail", equalTo("Invalid JSON request body"));
}

@Test
void testEmptyBodyReturnsBadRequest() {
    given()
            .contentType(ContentType.JSON)
            .body("")
            .when()
            .post("/api/documents/batch")
            .then()
            .statusCode(400)
            .body("title", equalTo("Bad Request"))
            .body("status", equalTo(400));
}
```

### R4: Add Unit Test for `JsonProcessingExceptionMapper`

**New file:** `src/test/java/com/fvd/common/exceptions/JsonProcessingExceptionMapperTest.java`

```java
package com.fvd.common.exceptions;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fvd.common.resources.ProblemDetail;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JsonProcessingExceptionMapperTest {

    @Test
    void shouldReturnBadRequestStatus() {
        JsonProcessingExceptionMapper mapper = new JsonProcessingExceptionMapper();
        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getPath()).thenReturn("api/documents/batch");
        mapper.uriInfo = uriInfo;

        JsonProcessingException exception = new JsonParseException(null, "Unexpected character");
        Response response = mapper.toResponse(exception);

        assertThat(response.getStatus()).isEqualTo(400);
    }

    @Test
    void shouldReturnBadRequestTitle() {
        JsonProcessingExceptionMapper mapper = new JsonProcessingExceptionMapper();
        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getPath()).thenReturn("api/documents/batch");
        mapper.uriInfo = uriInfo;

        JsonProcessingException exception = new JsonParseException(null, "Unexpected character");
        Response response = mapper.toResponse(exception);
        ProblemDetail problem = (ProblemDetail) response.getEntity();

        assertThat(problem.title).isEqualTo("Bad Request");
    }

    @Test
    void shouldReturnStaticDetailMessage() {
        JsonProcessingExceptionMapper mapper = new JsonProcessingExceptionMapper();
        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getPath()).thenReturn("api/documents/batch");
        mapper.uriInfo = uriInfo;

        JsonProcessingException exception = new JsonParseException(null, "Unexpected character 'n'");
        Response response = mapper.toResponse(exception);
        ProblemDetail problem = (ProblemDetail) response.getEntity();

        assertThat(problem.detail).isEqualTo("Invalid JSON request body");
    }

    @Test
    void shouldIncludeInstancePath() {
        JsonProcessingExceptionMapper mapper = new JsonProcessingExceptionMapper();
        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getPath()).thenReturn("api/documents/batch");
        mapper.uriInfo = uriInfo;

        JsonProcessingException exception = new JsonParseException(null, "Unexpected");
        Response response = mapper.toResponse(exception);
        ProblemDetail problem = (ProblemDetail) response.getEntity();

        assertThat(problem.instance).isEqualTo("api/documents/batch");
    }

    @Test
    void shouldReturnAboutBlankType() {
        JsonProcessingExceptionMapper mapper = new JsonProcessingExceptionMapper();
        mapper.uriInfo = null;

        JsonProcessingException exception = new JsonParseException(null, "error");
        Response response = mapper.toResponse(exception);
        ProblemDetail problem = (ProblemDetail) response.getEntity();

        assertThat(problem.type).isEqualTo("about:blank");
    }

    @Test
    void shouldReturnTimestamp() {
        JsonProcessingExceptionMapper mapper = new JsonProcessingExceptionMapper();
        mapper.uriInfo = null;

        JsonProcessingException exception = new JsonParseException(null, "error");
        Response response = mapper.toResponse(exception);
        ProblemDetail problem = (ProblemDetail) response.getEntity();

        assertThat(problem.timestamp).isNotNull();
        assertThat(problem.timestamp).isNotEmpty();
    }

    @Test
    void shouldReturnJsonMediaType() {
        JsonProcessingExceptionMapper mapper = new JsonProcessingExceptionMapper();
        mapper.uriInfo = null;

        JsonProcessingException exception = new JsonParseException(null, "error");
        Response response = mapper.toResponse(exception);

        assertThat(response.getMediaType().toString()).isEqualTo("application/json");
    }
}
```

---

## Technical Design

### Exception Flow (Before Fix)

```
Client sends: POST /api/documents/batch with body "not json"
    │
    ├── Quarkus RESTEasy Reactive receives request
    ├── Jackson ObjectMapper.readValue() fails
    ├── Throws: com.fasterxml.jackson.core.JsonParseException
    │           (extends JsonProcessingException extends IOException extends Exception)
    │
    ├── JAX-RS looks for ExceptionMapper<JsonParseException> → not found
    ├── JAX-RS looks for ExceptionMapper<JsonProcessingException> → not found
    ├── JAX-RS looks for ExceptionMapper<IOException> → not found
    ├── JAX-RS looks for ExceptionMapper<Exception> → GenericExceptionMapper
    │
    └── GenericExceptionMapper.toResponse() → 500 "An unexpected error occurred"
```

### Exception Flow (After Fix)

```
Client sends: POST /api/documents/batch with body "not json"
    │
    ├── Quarkus RESTEasy Reactive receives request
    ├── Jackson ObjectMapper.readValue() fails
    ├── Throws: com.fasterxml.jackson.core.JsonParseException
    │           (extends JsonProcessingException extends IOException extends Exception)
    │
    ├── JAX-RS looks for ExceptionMapper<JsonParseException> → not found
    ├── JAX-RS looks for ExceptionMapper<JsonProcessingException> → FOUND ✓
    │
    └── JsonProcessingExceptionMapper.toResponse() → 400 "Invalid JSON request body"
```

### Mapper Registration

```
ExceptionMapper hierarchy (ordered by specificity):
    GenericExceptionMapper<Exception>                    → 500 (catch-all)
    ├── JsonProcessingExceptionMapper<JsonProcessingException> → 400 (NEW)
    ├── InvalidInputExceptionMapper<InvalidInputException>     → 400
    ├── ParamExceptionMapper<BadRequestException>              → 400
    ├── NotFoundExceptionMapper<NotFoundException>             → 404
    ├── DocNotFoundExceptionMapper<DocNotFoundException>       → 404
    ├── NotAllowedExceptionMapper<NotAllowedException>         → 405
    ├── NotAcceptableExceptionMapper<NotAcceptableException>   → 406
    └── UpstreamExceptionMapper<UpstreamException>             → 502
```

### Security Consideration: Detail Message

The `getDetail()` method intentionally returns a **static** message (`"Invalid JSON request body"`) rather than the Jackson exception message. Jackson's messages contain implementation details:

```
// Jackson's actual error message (NEVER exposed to client):
"Unexpected character ('n' (code 110)): was expecting double-quote to start field name\n at [Source: (String)\"not json\"; line: 1, column: 3]"
```

Exposing this would reveal:
- Internal class names and parser state
- Exact input position (line/column)
- Parser implementation details

The static message tells the client exactly what they need to know: "your JSON is invalid". The original message is logged at `DEBUG` level for developer troubleshooting.

---

## Request/Response Examples

### Example 1: Completely invalid body

**Request:**
```
POST /api/documents/batch
Content-Type: application/json

not json
```

**Response (400):**
```
HTTP/1.1 400 Bad Request
Content-Type: application/json

{
    "type": "about:blank",
    "title": "Bad Request",
    "status": 400,
    "detail": "Invalid JSON request body",
    "instance": "api/documents/batch",
    "timestamp": "2026-02-16T10:30:00Z"
}
```

### Example 2: Truncated JSON

**Request:**
```
POST /api/documents/batch
Content-Type: application/json

{"paths":
```

**Response (400):**
```
HTTP/1.1 400 Bad Request
Content-Type: application/json

{
    "type": "about:blank",
    "title": "Bad Request",
    "status": 400,
    "detail": "Invalid JSON request body",
    "instance": "api/documents/batch",
    "timestamp": "2026-02-16T10:30:00Z"
}
```

### Example 3: Invalid JSON syntax

**Request:**
```
POST /api/documents/batch
Content-Type: application/json

{invalid}
```

**Response (400):**
```
HTTP/1.1 400 Bad Request
Content-Type: application/json

{
    "type": "about:blank",
    "title": "Bad Request",
    "status": 400,
    "detail": "Invalid JSON request body",
    "instance": "api/documents/batch",
    "timestamp": "2026-02-16T10:30:00Z"
}
```

### Example 4: Wrong type in JSON (string instead of array)

**Request:**
```
POST /api/documents/batch
Content-Type: application/json

{"paths": "not-an-array"}
```

**Response (400):**
```
HTTP/1.1 400 Bad Request
Content-Type: application/json

{
    "type": "about:blank",
    "title": "Bad Request",
    "status": 400,
    "detail": "Invalid JSON request body",
    "instance": "api/documents/batch",
    "timestamp": "2026-02-16T10:30:00Z"
}
```

### Example 5: Empty body

**Request:**
```
POST /api/documents/batch
Content-Type: application/json

```

**Response (400):**
```
HTTP/1.1 400 Bad Request
Content-Type: application/json

{
    "type": "about:blank",
    "title": "Bad Request",
    "status": 400,
    "detail": "Invalid JSON request body",
    "instance": "api/documents/batch",
    "timestamp": "2026-02-16T10:30:00Z"
}
```

### Example 6: Valid JSON — no change in behavior

**Request:**
```
POST /api/documents/batch
Content-Type: application/json

{"paths": ["security-overview.adoc"], "version": "3.27"}
```

**Response (200):**
```
HTTP/1.1 200 OK
Content-Type: application/json

{
    "documents": [...],
    "errors": [],
    "requestedCount": 1,
    "retrievedCount": 1,
    "errorCount": 0
}
```

No change — the `JsonProcessingExceptionMapper` is never invoked for valid JSON.

---

## Implementation Notes

### Test Pattern

Integration tests follow the existing `ProblemDetailErrorResponseTest` pattern:
- Extend `AbstractApiResourceTest` for test infrastructure (`docStore`, `cleanTestCache`)
- Use RestAssured `.body()` to send raw strings (not DTOs) to trigger parse errors
- Assert all 6 ProblemDetail fields: `type`, `title`, `status`, `detail`, `instance`, `timestamp`
- Use `ContentType.JSON` with RestAssured to set `Content-Type: application/json`

Unit tests follow the `AbstractProblemDetailMapperTest` pattern:
- Instantiate the mapper directly
- Mock `UriInfo` and set it on the mapper
- Create `JsonParseException` instances for test inputs
- Assert `Response` status, entity fields, and media type

### Quarkus RESTEasy Reactive Behavior Note

In Quarkus RESTEasy Reactive, the exact exception thrown for malformed JSON may be:
- `com.fasterxml.jackson.core.JsonParseException` — for syntax errors
- `com.fasterxml.jackson.databind.exc.MismatchedInputException` — for type mismatches
- `com.fasterxml.jackson.databind.JsonMappingException` — for mapping failures

All of these extend `JsonProcessingException`, so the single mapper catches them all. The implementer should verify by sending malformed JSON to the running dev server and checking the log output from `GenericExceptionMapper` (which logs the exception class at `ERROR` level).

### Jackson Dependency

`com.fasterxml.jackson.core.JsonProcessingException` is part of `jackson-core`, which is already a transitive dependency of Quarkus RESTEasy Reactive Jackson (`quarkus-rest-jackson`). No new dependency is needed in `build.gradle`.

---

## Tasks

- [ ] Create `JsonProcessingExceptionMapper` in `src/main/java/com/fvd/common/exceptions/` extending `AbstractProblemDetailMapper<JsonProcessingException>` (R1)
- [ ] Annotate with `@Provider` and `@Slf4j`
- [ ] Implement `getStatus()` returning `Response.Status.BAD_REQUEST`
- [ ] Implement `getTitle()` returning `"Bad Request"`
- [ ] Implement `getDetail()` returning static `"Invalid JSON request body"` and logging original message at DEBUG level
- [ ] Add unit tests in `JsonProcessingExceptionMapperTest`:
    - Status code is 400
    - Title is "Bad Request"
    - Detail is static "Invalid JSON request body" (does not leak Jackson internals)
    - Instance path from UriInfo
    - Type is "about:blank"
    - Timestamp is present
    - Media type is application/json
- [ ] Add integration tests in `ProblemDetailErrorResponseTest`:
    - `"not json"` body → 400 with full ProblemDetail validation
    - Truncated JSON `{"paths":` → 400
    - Invalid syntax `{invalid}` → 400
    - Empty body `""` → 400
- [ ] Verify valid JSON requests to `POST /api/documents/batch` still return 200 (no regression)
- [ ] Verify existing `ProblemDetailErrorResponseTest` tests still pass
- [ ] Run `./gradlew test` — all tests pass

---

## Acceptance Criteria

1. `POST /api/documents/batch` with body `"not json"` and `Content-Type: application/json` returns **400** with RFC 7807 ProblemDetail containing `"title": "Bad Request"` and `"detail": "Invalid JSON request body"`
2. `POST /api/documents/batch` with truncated JSON body `{"paths":` returns **400** with ProblemDetail
3. `POST /api/documents/batch` with invalid syntax `{invalid}` returns **400** with ProblemDetail
4. `POST /api/documents/batch` with empty body returns **400** with ProblemDetail
5. All ProblemDetail responses include `type` ("about:blank"), `title`, `status`, `detail`, `instance`, and `timestamp` fields
6. The `detail` field does **not** contain Jackson internal error messages, class names, or line/column numbers
7. Valid JSON requests to `POST /api/documents/batch` continue to return 200 (no regression)
8. All existing tests in `ProblemDetailErrorResponseTest` continue to pass
9. `./gradlew test` passes with zero failures
10. No changes to existing API behavior for valid requests

---

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Quarkus RESTEasy Reactive wraps `JsonProcessingException` in another exception type before the mapper can catch it | Low | High | Verify by sending malformed JSON to dev server and checking `GenericExceptionMapper` logs for the actual exception class. If wrapped, create a mapper for the wrapper type instead |
| `JsonProcessingException` mapper catches serialization errors (response serialization failures) in addition to deserialization errors (request parsing failures) | Low | Medium | In practice, response serialization errors are rare and indicate a server bug. Returning 400 for these would be misleading. Monitor logs to detect if this occurs; if so, add a check to distinguish request vs response errors |
| Empty body may throw a different exception than `JsonProcessingException` (e.g., `NullPointerException` in the resource method) | Medium | Low | Test with empty body specifically. If a different exception is thrown, the empty-body test will catch it and the implementer can handle it appropriately |
| Logging at DEBUG level may not be visible in production | Low | Low | Acceptable — JSON parse errors are client errors, not server issues. Developers can enable DEBUG logging for the package if needed for troubleshooting |
| Future endpoints that accept JSON bodies automatically get this error handling | Very Low | Positive | This is a benefit, not a risk — all JSON-accepting endpoints will return proper 400 errors for malformed input |

---

## Estimated Effort

| Task | Hours |
|------|-------|
| Create `JsonProcessingExceptionMapper` | 0.25 |
| Unit tests for `JsonProcessingExceptionMapper` (7 tests) | 0.75 |
| Integration tests in `ProblemDetailErrorResponseTest` (4 tests) | 0.75 |
| Verify no regressions, run full test suite | 0.5 |
| **Total** | **~2.25 hours** |

---

## Files Modified

### New Production Files (1 file)
- `src/main/java/com/fvd/common/exceptions/JsonProcessingExceptionMapper.java` — `@Provider` mapper extending `AbstractProblemDetailMapper<JsonProcessingException>`, returns 400 with static detail message `"Invalid JSON request body"`

### New Test Files (1 file)
- `src/test/java/com/fvd/common/exceptions/JsonProcessingExceptionMapperTest.java` — unit tests for status, title, detail, instance, type, timestamp, media type

### Modified Test Files (1 file)
- `src/test/java/com/fvd/api/resources/ProblemDetailErrorResponseTest.java` — add 4 integration tests for malformed JSON body scenarios

### Unchanged Files
- `src/main/java/com/fvd/common/exceptions/AbstractProblemDetailMapper.java` — no changes; new mapper extends it
- `src/main/java/com/fvd/common/exceptions/GenericExceptionMapper.java` — no changes; `JsonProcessingException` is more specific than `Exception`
- `src/main/java/com/fvd/common/resources/ProblemDetail.java` — no changes
- `src/main/java/com/fvd/api/dto/BatchDocumentRequest.java` — no changes
- All other existing exception mappers — no changes

---

## Dependencies

- **Feature 68 (Fix HTTP Exception Mapper Bugs):** Already implemented. The `AbstractProblemDetailMapper` base class and existing mapper pattern are stable. This feature follows the exact same pattern.
- **No new library dependencies** — `com.fasterxml.jackson.core.JsonProcessingException` is already available via Quarkus RESTEasy Reactive Jackson (`quarkus-rest-jackson`).
- **No inter-feature dependency with Feature 87** — both features add independent exception mappers for different exception types. They can be implemented in any order or in parallel.

---

END OF FILE
