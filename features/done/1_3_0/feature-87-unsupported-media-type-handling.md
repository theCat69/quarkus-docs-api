# Feature 87: Unsupported Media Type Handling (415 Instead of 500)

> **Dependencies**: None — this feature is independent. It follows the same `AbstractProblemDetailMapper` pattern established in Feature 68. No other features need to be implemented first.

## Summary

When a client sends a request to `POST /api/documents/batch` with a `Content-Type` other than `application/json` (e.g., `Content-Type: text/plain`), the API returns HTTP 500 with `"An unexpected error occurred"` because no exception mapper exists for `jakarta.ws.rs.NotSupportedException`. This is a bug: an unsupported content type is a client error (415), not a server error (500). This feature adds a `NotSupportedExceptionMapper` that catches `jakarta.ws.rs.NotSupportedException` and returns a proper RFC 7807 `ProblemDetail` response with status 415 and a descriptive error message indicating that `application/json` is the supported content type.

## User Story

As an **AI agent consuming this API through an MCP server**, I want the API to return HTTP 415 with a clear error message when I send a request with the wrong `Content-Type` header, so that I can distinguish between "my content type is wrong" (415) and "the server has an internal problem" (500), and immediately correct the `Content-Type` to `application/json` rather than retrying with the same wrong header.

## Motivation

### Current Behavior (Bug)

```
POST /api/documents/batch
Content-Type: text/plain

{"paths": ["security-overview.adoc"], "version": "3.27"}
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

The `GenericExceptionMapper` catches the `NotSupportedException` that JAX-RS throws when the request's `Content-Type` doesn't match any `@Consumes` annotation, and returns a generic 500. The AI agent sees 500, interprets it as a transient server failure, and retries the same request — never fixing the `Content-Type` header because the error message gives no indication of the actual problem.

### Desired Behavior (Fixed)

```
POST /api/documents/batch
Content-Type: text/plain

{"paths": ["security-overview.adoc"], "version": "3.27"}
```

**Response (415):**
```
HTTP/1.1 415 Unsupported Media Type
Content-Type: application/json

{
    "type": "about:blank",
    "title": "Unsupported Media Type",
    "status": 415,
    "detail": "The request content type is not supported. Supported: application/json",
    "instance": "api/documents/batch",
    "timestamp": "2026-02-16T10:30:00Z"
}
```

The AI agent sees 415, understands the content type is wrong, reads the detail message to learn that `application/json` is required, and retries with the correct `Content-Type` header.

### Content-Type Scenarios

| Content-Type | Endpoint | Current Status | Expected Status |
|-------------|----------|----------------|-----------------|
| `text/plain` | `POST /api/documents/batch` | 500 | 415 |
| `application/xml` | `POST /api/documents/batch` | 500 | 415 |
| `text/html` | `POST /api/documents/batch` | 500 | 415 |
| `multipart/form-data` | `POST /api/documents/batch` | 500 | 415 |
| `application/x-www-form-urlencoded` | `POST /api/documents/batch` | 500 | 415 |
| (no Content-Type header) | `POST /api/documents/batch` | 500 | 415 |
| `application/json` | `POST /api/documents/batch` | 200 | 200 (no change) |

All wrong content types produce the same `jakarta.ws.rs.NotSupportedException`, so a single mapper catches them all.

### Difference from 406 Not Acceptable

This feature handles **request** content type (what the client *sends*), not **response** content type (what the client *accepts*). The existing `NotAcceptableExceptionMapper` (Feature 68) handles `Accept` header mismatches (406). This feature handles `Content-Type` header mismatches (415):

| HTTP Header | Error Code | Exception | Mapper |
|-------------|-----------|-----------|--------|
| `Accept: application/xml` | 406 Not Acceptable | `NotAcceptableException` | `NotAcceptableExceptionMapper` (exists) |
| `Content-Type: text/plain` | 415 Unsupported Media Type | `NotSupportedException` | `NotSupportedExceptionMapper` (**new**) |

---

## Scope / Requirements

### R0: JAX-RS `NotSupportedException` Background

`jakarta.ws.rs.NotSupportedException` is thrown by the JAX-RS runtime when a request's `Content-Type` header doesn't match any `@Consumes` annotation on the matched resource method. In this API:

- The `POST /api/documents/batch` endpoint in `DocumentResource` consumes `application/json` (via `@Consumes(MediaType.APPLICATION_JSON)` or the implicit default in Quarkus RESTEasy Reactive when a JSON body parameter is declared).
- When a client sends `Content-Type: text/plain`, JAX-RS cannot find a `MessageBodyReader` for the declared body type (`BatchDocumentRequest`) with that media type, and throws `NotSupportedException`.

`NotSupportedException` extends `ClientErrorException` which extends `WebApplicationException` which extends `RuntimeException` which extends `Exception`. The `GenericExceptionMapper<Exception>` catches it because no more-specific mapper exists.

### R1: Create `NotSupportedExceptionMapper`

**New file:** `src/main/java/com/fvd/common/exceptions/NotSupportedExceptionMapper.java`

**Package:** `com.fvd.common.exceptions`

Create a `@Provider` mapper extending `AbstractProblemDetailMapper<NotSupportedException>`:

```java
package com.fvd.common.exceptions;

import jakarta.ws.rs.NotSupportedException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

/**
 * Maps Jakarta NotSupportedException to RFC 7807 Problem Details response
 * with 415 Unsupported Media Type. Thrown when the request Content-Type
 * does not match any @Consumes annotation on the matched resource method.
 */
@Provider
public class NotSupportedExceptionMapper extends AbstractProblemDetailMapper<NotSupportedException> {

    @Override
    protected Response.Status getStatus() {
        return Response.Status.UNSUPPORTED_MEDIA_TYPE;
    }

    @Override
    protected String getTitle() {
        return "Unsupported Media Type";
    }

    @Override
    protected String getDetail(NotSupportedException exception) {
        return "The request content type is not supported. Supported: application/json";
    }
}
```

**Key design decisions:**

1. **Static detail message:** The `getDetail()` method returns a fixed message `"The request content type is not supported. Supported: application/json"` rather than exposing `exception.getMessage()`. The JAX-RS exception message may contain internal details. The static message tells the AI agent exactly what content type to use.

2. **No `@Slf4j` needed:** Unlike `JsonProcessingExceptionMapper`, there's no need to log the original message at DEBUG level. The `Content-Type` mismatch is a straightforward client error with no diagnostic value beyond "wrong content type". The `Content-Type` header value is already visible in access logs.

3. **No `@Priority` needed:** JAX-RS selects the most specific `ExceptionMapper`. `NotSupportedException` is more specific than `Exception`, so this mapper takes priority over `GenericExceptionMapper` automatically.

4. **`Response.Status.UNSUPPORTED_MEDIA_TYPE`:** JAX-RS defines `Response.Status.UNSUPPORTED_MEDIA_TYPE` as HTTP 415. This is a standard enum value — no custom status code needed.

5. **"Supported: application/json" in detail:** The detail message explicitly states the supported content type. This is actionable information for AI agents — they can immediately retry with `Content-Type: application/json`. If the API ever supports additional content types, this message should be updated.

### R2: Verify No Conflict with Existing Mappers

The new mapper must not conflict with existing exception mappers:

| Existing Mapper | Exception Type | Conflict? |
|----------------|---------------|-----------|
| `GenericExceptionMapper` | `Exception` | No — `NotSupportedException` is more specific |
| `NotAcceptableExceptionMapper` | `NotAcceptableException` | No — different exception type. `NotAcceptableException` handles `Accept` header (406); `NotSupportedException` handles `Content-Type` header (415) |
| `NotAllowedExceptionMapper` | `NotAllowedException` | No — different exception type |
| `ParamExceptionMapper` | `BadRequestException` | No — `NotSupportedException` does not extend `BadRequestException` |
| `InvalidInputExceptionMapper` | `InvalidInputException` | No — unrelated hierarchy |
| `NotFoundExceptionMapper` | `NotFoundException` | No — unrelated hierarchy |
| `DocNotFoundExceptionMapper` | `DocNotFoundException` | No — unrelated hierarchy |
| `UpstreamExceptionMapper` | `UpstreamException` | No — unrelated hierarchy |

`NotSupportedException` extends `ClientErrorException` extends `WebApplicationException` extends `RuntimeException` extends `Exception`. The only mapper that could also catch it is `GenericExceptionMapper<Exception>`, but `NotSupportedExceptionMapper<NotSupportedException>` is more specific and wins.

### R3: Update Tests in `ProblemDetailErrorResponseTest`

**File:** `src/test/java/com/fvd/api/resources/ProblemDetailErrorResponseTest.java`

Add new test methods to verify the fix:

```java
@Test
void testTextPlainContentTypeReturnsUnsupportedMediaType() {
    given()
            .contentType(ContentType.TEXT)
            .body("{\"paths\": [\"security.adoc\"], \"version\": \"3.27\"}")
            .when()
            .post("/api/documents/batch")
            .then()
            .statusCode(415)
            .body("type", equalTo("about:blank"))
            .body("title", equalTo("Unsupported Media Type"))
            .body("status", equalTo(415))
            .body("detail", equalTo("The request content type is not supported. Supported: application/json"))
            .body("instance", containsString("documents/batch"))
            .body("timestamp", notNullValue());
}

@Test
void testXmlContentTypeReturnsUnsupportedMediaType() {
    given()
            .contentType(ContentType.XML)
            .body("<request><paths><path>security.adoc</path></paths></request>")
            .when()
            .post("/api/documents/batch")
            .then()
            .statusCode(415)
            .body("title", equalTo("Unsupported Media Type"))
            .body("status", equalTo(415))
            .body("detail", containsString("application/json"));
}

@Test
void testHtmlContentTypeReturnsUnsupportedMediaType() {
    given()
            .contentType(ContentType.HTML)
            .body("<html><body>test</body></html>")
            .when()
            .post("/api/documents/batch")
            .then()
            .statusCode(415)
            .body("title", equalTo("Unsupported Media Type"))
            .body("status", equalTo(415));
}

@Test
void testJsonContentTypeStillWorksOnBatchEndpoint() {
    // Ensure valid JSON with correct Content-Type still works (no regression)
    given()
            .contentType(ContentType.JSON)
            .body("{\"paths\": [\"nonexistent.adoc\"], \"version\": \"3.27\"}")
            .when()
            .post("/api/documents/batch")
            .then()
            .statusCode(200);
}
```

### R4: Add Unit Test for `NotSupportedExceptionMapper`

**New file:** `src/test/java/com/fvd/common/exceptions/NotSupportedExceptionMapperTest.java`

```java
package com.fvd.common.exceptions;

import com.fvd.common.resources.ProblemDetail;
import jakarta.ws.rs.NotSupportedException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NotSupportedExceptionMapperTest {

    @Test
    void shouldReturnUnsupportedMediaTypeStatus() {
        NotSupportedExceptionMapper mapper = new NotSupportedExceptionMapper();
        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getPath()).thenReturn("api/documents/batch");
        mapper.uriInfo = uriInfo;

        NotSupportedException exception = new NotSupportedException("text/plain is not supported");
        Response response = mapper.toResponse(exception);

        assertThat(response.getStatus()).isEqualTo(415);
    }

    @Test
    void shouldReturnUnsupportedMediaTypeTitle() {
        NotSupportedExceptionMapper mapper = new NotSupportedExceptionMapper();
        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getPath()).thenReturn("api/documents/batch");
        mapper.uriInfo = uriInfo;

        NotSupportedException exception = new NotSupportedException("text/plain is not supported");
        Response response = mapper.toResponse(exception);
        ProblemDetail problem = (ProblemDetail) response.getEntity();

        assertThat(problem.title).isEqualTo("Unsupported Media Type");
    }

    @Test
    void shouldReturnStaticDetailWithSupportedType() {
        NotSupportedExceptionMapper mapper = new NotSupportedExceptionMapper();
        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getPath()).thenReturn("api/documents/batch");
        mapper.uriInfo = uriInfo;

        NotSupportedException exception = new NotSupportedException("text/plain is not supported");
        Response response = mapper.toResponse(exception);
        ProblemDetail problem = (ProblemDetail) response.getEntity();

        assertThat(problem.detail).isEqualTo("The request content type is not supported. Supported: application/json");
    }

    @Test
    void shouldIncludeInstancePath() {
        NotSupportedExceptionMapper mapper = new NotSupportedExceptionMapper();
        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getPath()).thenReturn("api/documents/batch");
        mapper.uriInfo = uriInfo;

        NotSupportedException exception = new NotSupportedException();
        Response response = mapper.toResponse(exception);
        ProblemDetail problem = (ProblemDetail) response.getEntity();

        assertThat(problem.instance).isEqualTo("api/documents/batch");
    }

    @Test
    void shouldReturnAboutBlankType() {
        NotSupportedExceptionMapper mapper = new NotSupportedExceptionMapper();
        mapper.uriInfo = null;

        NotSupportedException exception = new NotSupportedException();
        Response response = mapper.toResponse(exception);
        ProblemDetail problem = (ProblemDetail) response.getEntity();

        assertThat(problem.type).isEqualTo("about:blank");
    }

    @Test
    void shouldReturnTimestamp() {
        NotSupportedExceptionMapper mapper = new NotSupportedExceptionMapper();
        mapper.uriInfo = null;

        NotSupportedException exception = new NotSupportedException();
        Response response = mapper.toResponse(exception);
        ProblemDetail problem = (ProblemDetail) response.getEntity();

        assertThat(problem.timestamp).isNotNull();
        assertThat(problem.timestamp).isNotEmpty();
    }

    @Test
    void shouldReturnJsonMediaType() {
        NotSupportedExceptionMapper mapper = new NotSupportedExceptionMapper();
        mapper.uriInfo = null;

        NotSupportedException exception = new NotSupportedException();
        Response response = mapper.toResponse(exception);

        assertThat(response.getMediaType().toString()).isEqualTo("application/json");
    }

    @Test
    void shouldReturn415StatusCodeInProblemDetail() {
        NotSupportedExceptionMapper mapper = new NotSupportedExceptionMapper();
        mapper.uriInfo = null;

        NotSupportedException exception = new NotSupportedException();
        Response response = mapper.toResponse(exception);
        ProblemDetail problem = (ProblemDetail) response.getEntity();

        assertThat(problem.status).isEqualTo(415);
    }
}
```

---

## Technical Design

### Exception Flow (Before Fix)

```
Client sends: POST /api/documents/batch with Content-Type: text/plain
    │
    ├── Quarkus RESTEasy Reactive receives request
    ├── Looks for MessageBodyReader for BatchDocumentRequest with media type text/plain
    ├── No reader found → throws jakarta.ws.rs.NotSupportedException
    │   (extends ClientErrorException extends WebApplicationException
    │    extends RuntimeException extends Exception)
    │
    ├── JAX-RS looks for ExceptionMapper<NotSupportedException> → not found
    ├── JAX-RS looks for ExceptionMapper<ClientErrorException> → not found
    ├── JAX-RS looks for ExceptionMapper<WebApplicationException> → not found
    ├── JAX-RS looks for ExceptionMapper<RuntimeException> → not found
    ├── JAX-RS looks for ExceptionMapper<Exception> → GenericExceptionMapper
    │
    └── GenericExceptionMapper.toResponse() → 500 "An unexpected error occurred"
```

### Exception Flow (After Fix)

```
Client sends: POST /api/documents/batch with Content-Type: text/plain
    │
    ├── Quarkus RESTEasy Reactive receives request
    ├── Looks for MessageBodyReader for BatchDocumentRequest with media type text/plain
    ├── No reader found → throws jakarta.ws.rs.NotSupportedException
    │
    ├── JAX-RS looks for ExceptionMapper<NotSupportedException> → FOUND ✓
    │
    └── NotSupportedExceptionMapper.toResponse() → 415 "The request content type is not supported. Supported: application/json"
```

### Mapper Registration (After This Feature)

```
ExceptionMapper hierarchy (ordered by specificity):
    GenericExceptionMapper<Exception>                          → 500 (catch-all)
    ├── JsonProcessingExceptionMapper<JsonProcessingException> → 400 (Feature 86)
    ├── InvalidInputExceptionMapper<InvalidInputException>     → 400
    ├── ParamExceptionMapper<BadRequestException>              → 400
    ├── NotFoundExceptionMapper<NotFoundException>             → 404
    ├── DocNotFoundExceptionMapper<DocNotFoundException>       → 404
    ├── NotAllowedExceptionMapper<NotAllowedException>         → 405
    ├── NotAcceptableExceptionMapper<NotAcceptableException>   → 406
    ├── NotSupportedExceptionMapper<NotSupportedException>     → 415 (NEW)
    └── UpstreamExceptionMapper<UpstreamException>             → 502
```

### Symmetry with `NotAcceptableExceptionMapper`

The new mapper is the symmetric counterpart of `NotAcceptableExceptionMapper`:

| Aspect | NotAcceptableExceptionMapper | NotSupportedExceptionMapper |
|--------|-----------------------------|-----------------------------|
| HTTP header | `Accept` (response type) | `Content-Type` (request type) |
| Status code | 406 Not Acceptable | 415 Unsupported Media Type |
| Exception | `NotAcceptableException` | `NotSupportedException` |
| Direction | Client asks for wrong output format | Client sends wrong input format |
| Detail | "The requested media type is not supported. Supported: application/json" | "The request content type is not supported. Supported: application/json" |
| Affected methods | GET (all endpoints) | POST (batch endpoint) |

### When `NotSupportedException` Is Thrown

In this API, `NotSupportedException` is only relevant for endpoints that consume request bodies:

| Endpoint | Method | Consumes | Can trigger 415? |
|----------|--------|----------|-----------------|
| `POST /api/documents/batch` | POST | `application/json` | **Yes** |
| `GET /api/documents` | GET | N/A (no body) | No |
| `GET /api/search` | GET | N/A (no body) | No |
| `GET /api/catalog` | GET | N/A (no body) | No |
| `GET /api/code-samples` | GET | N/A (no body) | No |
| `GET /api/meta` | GET | N/A (no body) | No |

GET requests do not have request bodies, so `Content-Type` is irrelevant. Only `POST /api/documents/batch` can trigger a 415 error.

### Thread Safety

The mapper is stateless — no mutable instance fields. Thread-safe by design, consistent with all other exception mappers in the project.

---

## Request/Response Examples

### Example 1: text/plain content type

**Request:**
```
POST /api/documents/batch
Content-Type: text/plain

{"paths": ["security-overview.adoc"], "version": "3.27"}
```

**Response (415):**
```
HTTP/1.1 415 Unsupported Media Type
Content-Type: application/json

{
    "type": "about:blank",
    "title": "Unsupported Media Type",
    "status": 415,
    "detail": "The request content type is not supported. Supported: application/json",
    "instance": "api/documents/batch",
    "timestamp": "2026-02-16T10:30:00Z"
}
```

### Example 2: application/xml content type

**Request:**
```
POST /api/documents/batch
Content-Type: application/xml

<request><paths><path>security.adoc</path></paths></request>
```

**Response (415):**
```
HTTP/1.1 415 Unsupported Media Type
Content-Type: application/json

{
    "type": "about:blank",
    "title": "Unsupported Media Type",
    "status": 415,
    "detail": "The request content type is not supported. Supported: application/json",
    "instance": "api/documents/batch",
    "timestamp": "2026-02-16T10:30:00Z"
}
```

### Example 3: text/html content type

**Request:**
```
POST /api/documents/batch
Content-Type: text/html

<html><body>test</body></html>
```

**Response (415):**
```
HTTP/1.1 415 Unsupported Media Type
Content-Type: application/json

{
    "type": "about:blank",
    "title": "Unsupported Media Type",
    "status": 415,
    "detail": "The request content type is not supported. Supported: application/json",
    "instance": "api/documents/batch",
    "timestamp": "2026-02-16T10:30:00Z"
}
```

### Example 4: multipart/form-data content type

**Request:**
```
POST /api/documents/batch
Content-Type: multipart/form-data; boundary=----WebKitFormBoundary

------WebKitFormBoundary
Content-Disposition: form-data; name="file"; filename="test.json"
Content-Type: application/json

{"paths": ["security.adoc"]}
------WebKitFormBoundary--
```

**Response (415):**
```
HTTP/1.1 415 Unsupported Media Type
Content-Type: application/json

{
    "type": "about:blank",
    "title": "Unsupported Media Type",
    "status": 415,
    "detail": "The request content type is not supported. Supported: application/json",
    "instance": "api/documents/batch",
    "timestamp": "2026-02-16T10:30:00Z"
}
```

### Example 5: Correct content type — no change in behavior

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

No change — the `NotSupportedExceptionMapper` is never invoked for `application/json`.

### Example 6: GET request with wrong Accept header — handled by different mapper

**Request:**
```
GET /api/search?keywords=security
Accept: application/xml
```

**Response (406):**
```
HTTP/1.1 406 Not Acceptable
Content-Type: application/json

{
    "type": "about:blank",
    "title": "Not Acceptable",
    "status": 406,
    "detail": "The requested media type is not supported. Supported: application/json",
    "instance": "api/search",
    "timestamp": "2026-02-16T10:30:00Z"
}
```

This is handled by `NotAcceptableExceptionMapper` (already exists), **not** by the new `NotSupportedExceptionMapper`. Different header, different exception, different mapper.

---

## Implementation Notes

### Test Pattern

Integration tests follow the existing `ProblemDetailErrorResponseTest` pattern:
- Extend `AbstractApiResourceTest` for test infrastructure (`docStore`, `cleanTestCache`)
- Use RestAssured `.contentType(ContentType.TEXT)` or `.contentType(ContentType.XML)` to set wrong content types
- Assert all 6 ProblemDetail fields: `type`, `title`, `status`, `detail`, `instance`, `timestamp`
- Include a positive test verifying `ContentType.JSON` still works (regression guard)

Unit tests follow the `AbstractProblemDetailMapperTest` pattern:
- Instantiate the mapper directly
- Mock `UriInfo` and set it on the mapper
- Create `NotSupportedException` instances for test inputs
- Assert `Response` status, entity fields, and media type

### RestAssured Content-Type Constants

RestAssured provides `ContentType` enum values for common types:
- `ContentType.JSON` → `application/json`
- `ContentType.TEXT` → `text/plain`
- `ContentType.XML` → `application/xml`
- `ContentType.HTML` → `text/html`

Use these constants instead of raw strings for readability and type safety.

### No New Dependencies

`jakarta.ws.rs.NotSupportedException` is part of the Jakarta RESTful Web Services API (`jakarta.ws.rs-api`), which is already a dependency of Quarkus RESTEasy Reactive. No new dependency is needed in `build.gradle`.

---

## Tasks

- [ ] Create `NotSupportedExceptionMapper` in `src/main/java/com/fvd/common/exceptions/` extending `AbstractProblemDetailMapper<NotSupportedException>` (R1)
- [ ] Annotate with `@Provider`
- [ ] Implement `getStatus()` returning `Response.Status.UNSUPPORTED_MEDIA_TYPE`
- [ ] Implement `getTitle()` returning `"Unsupported Media Type"`
- [ ] Implement `getDetail()` returning static `"The request content type is not supported. Supported: application/json"`
- [ ] Add unit tests in `NotSupportedExceptionMapperTest`:
    - Status code is 415
    - Title is "Unsupported Media Type"
    - Detail is "The request content type is not supported. Supported: application/json"
    - Instance path from UriInfo
    - Type is "about:blank"
    - Timestamp is present
    - Media type is application/json
    - Status code in ProblemDetail body is 415
- [ ] Add integration tests in `ProblemDetailErrorResponseTest`:
    - `Content-Type: text/plain` → 415 with full ProblemDetail validation
    - `Content-Type: application/xml` → 415
    - `Content-Type: text/html` → 415
    - `Content-Type: application/json` → 200 (regression guard)
- [ ] Verify existing `ProblemDetailErrorResponseTest` tests still pass
- [ ] Run `./gradlew test` — all tests pass

---

## Acceptance Criteria

1. `POST /api/documents/batch` with `Content-Type: text/plain` returns **415** with RFC 7807 ProblemDetail containing `"title": "Unsupported Media Type"` and `"detail": "The request content type is not supported. Supported: application/json"`
2. `POST /api/documents/batch` with `Content-Type: application/xml` returns **415** with ProblemDetail
3. `POST /api/documents/batch` with `Content-Type: text/html` returns **415** with ProblemDetail
4. All ProblemDetail responses include `type` ("about:blank"), `title`, `status`, `detail`, `instance`, and `timestamp` fields
5. The `status` field in the ProblemDetail body is `415` (matches the HTTP status code)
6. The `detail` field contains `"application/json"` to guide clients toward the correct content type
7. `POST /api/documents/batch` with `Content-Type: application/json` and valid JSON body still returns 200 (no regression)
8. GET requests with wrong `Accept` header still return 406 (handled by `NotAcceptableExceptionMapper`, no interference)
9. All existing tests in `ProblemDetailErrorResponseTest` continue to pass
10. `./gradlew test` passes with zero failures
11. No changes to existing API behavior for valid requests

---

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Quarkus RESTEasy Reactive wraps `NotSupportedException` in another exception before the mapper can catch it | Very Low | High | `NotSupportedException` is a standard JAX-RS exception type; all JAX-RS implementations are required to throw it for content type mismatches. Verify by sending `text/plain` to dev server and checking `GenericExceptionMapper` logs for the actual exception class |
| `NotSupportedException` is also thrown for request body serialization failures (not just content type mismatches) | Very Low | Low | In JAX-RS, `NotSupportedException` is specifically defined for "HTTP 415 Unsupported Media Type" scenarios. Other serialization failures use different exception types |
| 415 response body may not be readable if client only accepts non-JSON types | Low | Low | `AbstractProblemDetailMapper.toResponse()` explicitly sets `.type(MediaType.APPLICATION_JSON)`, overriding the client's preference for error responses. The client may not be able to parse the error body, but the HTTP 415 status code alone is sufficient to diagnose the problem |
| Future endpoints that consume non-JSON content types (e.g., file upload) would get wrong error message | Very Low | Medium | The static detail message says "Supported: application/json". If a future endpoint supports other content types, the mapper should be updated to include them, or the endpoint should declare its own more-specific mapper. This is a known limitation of a static message |
| The `Content-Type` header might be case-sensitive or include charset parameters | Very Low | Very Low | JAX-RS handles content type parsing and matching internally. The mapper only catches the exception — it doesn't inspect the `Content-Type` header directly |

---

## Estimated Effort

| Task | Hours |
|------|-------|
| Create `NotSupportedExceptionMapper` | 0.25 |
| Unit tests for `NotSupportedExceptionMapper` (8 tests) | 0.75 |
| Integration tests in `ProblemDetailErrorResponseTest` (4 tests) | 0.75 |
| Verify no regressions, run full test suite | 0.5 |
| **Total** | **~2.25 hours** |

---

## Files Modified

### New Production Files (1 file)
- `src/main/java/com/fvd/common/exceptions/NotSupportedExceptionMapper.java` — `@Provider` mapper extending `AbstractProblemDetailMapper<NotSupportedException>`, returns 415 with detail message `"The request content type is not supported. Supported: application/json"`

### New Test Files (1 file)
- `src/test/java/com/fvd/common/exceptions/NotSupportedExceptionMapperTest.java` — unit tests for status, title, detail, instance, type, timestamp, media type, status-in-body

### Modified Test Files (1 file)
- `src/test/java/com/fvd/api/resources/ProblemDetailErrorResponseTest.java` — add 4 integration tests for unsupported content type scenarios plus 1 regression guard test

### Unchanged Files
- `src/main/java/com/fvd/common/exceptions/AbstractProblemDetailMapper.java` — no changes; new mapper extends it
- `src/main/java/com/fvd/common/exceptions/GenericExceptionMapper.java` — no changes; `NotSupportedException` is more specific than `Exception`
- `src/main/java/com/fvd/common/exceptions/NotAcceptableExceptionMapper.java` — no changes; handles different exception type (406 vs 415)
- `src/main/java/com/fvd/common/resources/ProblemDetail.java` — no changes
- `src/main/java/com/fvd/api/dto/BatchDocumentRequest.java` — no changes
- All other existing exception mappers — no changes

---

## Dependencies

- **Feature 68 (Fix HTTP Exception Mapper Bugs):** Already implemented. The `AbstractProblemDetailMapper` base class and existing mapper pattern are stable. This feature follows the exact same pattern.
- **No new library dependencies** — `jakarta.ws.rs.NotSupportedException` is already available via Quarkus RESTEasy Reactive (`jakarta.ws.rs-api`).
- **No inter-feature dependency with Feature 86** — both features add independent exception mappers for different exception types (`JsonProcessingException` for Feature 86, `NotSupportedException` for Feature 87). They can be implemented in any order or in parallel.

---

END OF FILE
