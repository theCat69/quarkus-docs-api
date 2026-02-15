# Feature 68: Fix HTTP Exception Mapper Bugs

## Summary

Three categories of HTTP requests return incorrect status codes because the `GenericExceptionMapper` (500) catches Jakarta RS exceptions that should be mapped to specific 4xx responses. Non-numeric query parameters like `limit=abc` return 404 instead of 400; `Accept: application/xml` returns 500 instead of 406; and unsupported HTTP methods (POST, DELETE) on GET-only endpoints return 500 instead of 405. This feature adds three new `@Provider` exception mappers following the existing `AbstractProblemDetailMapper` pattern.

## User Story

As an **AI agent consuming this API through MCP**, I need the API to return correct HTTP status codes for client errors so that I can distinguish between "my request was malformed" (400), "this media type is not supported" (406), and "this HTTP method is not supported" (405), rather than seeing a misleading 404 or a generic 500 that triggers unnecessary retry logic.

## Motivation

### Bug 1: Non-numeric `limit` returns 404 instead of 400

**Reproduction:**
```
GET /api/search?keywords=rest&limit=abc
```

**Current behavior:** 404 with `"detail": "Resource not found: api/search"`
**Expected behavior:** 400 with `"detail": "Invalid value for parameter 'limit': 'abc' is not a valid integer"`

**Root cause:** The `limit` query parameter is declared as `@QueryParam("limit") Integer limit` in `SearchResource` (line 89). When JAX-RS cannot coerce `"abc"` to `Integer`, it throws a `jakarta.ws.rs.ext.ParamConverter` failure wrapped in `org.jboss.resteasy.spi.ResteasyBadRequestException` (which extends `jakarta.ws.rs.BadRequestException`) or a JAX-RS `NotFoundException` depending on the framework version. In Quarkus RESTEasy Reactive, this is typically a `jakarta.ws.rs.NotFoundException` wrapping a `ParamException`, which is caught by `NotFoundExceptionMapper` and returned as 404. The actual error is a bad request (invalid parameter type), not a missing resource.

### Bug 2: `Accept: application/xml` returns 500 instead of 406

**Reproduction:**
```
GET /api/search?keywords=rest -H "Accept: application/xml"
```

**Current behavior:** 500 with `"detail": "An unexpected error occurred"`
**Expected behavior:** 406 with `"detail": "The requested media type 'application/xml' is not supported. Supported: application/json"`

**Root cause:** All resources are annotated with `@Produces(MediaType.APPLICATION_JSON)`. When a client sends `Accept: application/xml`, the framework throws `jakarta.ws.rs.NotAcceptableException`. No mapper exists for this exception, so it falls through to `GenericExceptionMapper` which returns 500.

### Bug 3: POST/DELETE on GET-only endpoints returns 500 instead of 405

**Reproduction:**
```
POST /api/documents?keywords=security
DELETE /api/search?keywords=rest
```

**Current behavior:** 500 with `"detail": "An unexpected error occurred"`
**Expected behavior:** 405 with `"detail": "HTTP method POST is not allowed on this resource. Allowed methods: GET"`

**Root cause:** All API resources only define `@GET` methods. When a client sends POST or DELETE, the framework throws `jakarta.ws.rs.NotAllowedException`. No mapper exists for this exception, so it falls through to `GenericExceptionMapper` which returns 500.

---

## Requirements

### R1: Add `NotAllowedExceptionMapper` for 405 Method Not Allowed

**New file:** `src/main/java/com/fvd/common/exceptions/NotAllowedExceptionMapper.java`

Create a `@Provider` mapper extending `AbstractProblemDetailMapper<jakarta.ws.rs.NotAllowedException>`:

```java
package com.fvd.common.exceptions;

import jakarta.ws.rs.NotAllowedException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

/**
 * Maps Jakarta NotAllowedException to RFC 7807 Problem Details response with 405 Method Not Allowed.
 */
@Provider
public class NotAllowedExceptionMapper extends AbstractProblemDetailMapper<NotAllowedException> {

    @Override
    protected Response.Status getStatus() {
        return Response.Status.METHOD_NOT_ALLOWED;
    }

    @Override
    protected String getTitle() {
        return "Method Not Allowed";
    }

    @Override
    protected String getDetail(NotAllowedException exception) {
        return exception.getMessage();
    }
}
```

The `NotAllowedException.getMessage()` already contains useful information like `"HTTP 405 Method Not Allowed"`. If the message is generic, enhance it to include the request path from `uriInfo`.

### R2: Add `NotAcceptableExceptionMapper` for 406 Not Acceptable

**New file:** `src/main/java/com/fvd/common/exceptions/NotAcceptableExceptionMapper.java`

Create a `@Provider` mapper extending `AbstractProblemDetailMapper<jakarta.ws.rs.NotAcceptableException>`:

```java
package com.fvd.common.exceptions;

import jakarta.ws.rs.NotAcceptableException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

/**
 * Maps Jakarta NotAcceptableException to RFC 7807 Problem Details response with 406 Not Acceptable.
 */
@Provider
public class NotAcceptableExceptionMapper extends AbstractProblemDetailMapper<NotAcceptableException> {

    @Override
    protected Response.Status getStatus() {
        return Response.Status.NOT_ACCEPTABLE;
    }

    @Override
    protected String getTitle() {
        return "Not Acceptable";
    }

    @Override
    protected String getDetail(NotAcceptableException exception) {
        return "The requested media type is not supported. Supported: application/json";
    }
}
```

### R3: Add `ParamExceptionMapper` for 400 Bad Request on Parameter Binding Failures

**New file:** `src/main/java/com/fvd/common/exceptions/ParamExceptionMapper.java`

This is the trickiest mapper. In Quarkus RESTEasy Reactive, parameter binding failures throw different exception types depending on the framework version. The approach is to catch `jakarta.ws.rs.BadRequestException` (the parent of parameter coercion errors in most JAX-RS implementations) and return 400:

```java
package com.fvd.common.exceptions;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

/**
 * Maps Jakarta BadRequestException (including query parameter type coercion failures)
 * to RFC 7807 Problem Details response with 400 Bad Request.
 */
@Provider
public class ParamExceptionMapper extends AbstractProblemDetailMapper<BadRequestException> {

    @Override
    protected Response.Status getStatus() {
        return Response.Status.BAD_REQUEST;
    }

    @Override
    protected String getTitle() {
        return "Bad Request";
    }

    @Override
    protected String getDetail(BadRequestException exception) {
        return exception.getMessage() != null
                ? exception.getMessage()
                : "Invalid request parameter";
    }
}
```

**Important implementation note:** If Quarkus RESTEasy Reactive wraps the parameter error as a `NotFoundException` (rather than `BadRequestException`), the existing `NotFoundExceptionMapper` is what catches it. In that case, the fix requires modifying `NotFoundExceptionMapper` to inspect the cause chain: if the cause is a parameter coercion error (e.g., `NumberFormatException` in the cause chain), return 400 instead of 404. The implementer must verify which exception type Quarkus RESTEasy Reactive actually throws by running the reproduction case and inspecting the logs. The `GenericExceptionMapper` already logs the exception class, so adding a temporary log statement or breakpoint will reveal the actual exception type.

**Fallback approach** — if the exception is indeed a `NotFoundException` wrapping a parameter error, modify `NotFoundExceptionMapper`:

```java
@Override
protected Response.Status getStatus() {
    return Response.Status.NOT_FOUND;
}

// Add a check in toResponse or override the base behavior:
// If exception.getCause() is NumberFormatException or similar,
// return 400 instead of 404.
```

However, since `AbstractProblemDetailMapper.toResponse()` is `final`, this approach would require either:
1. Checking the cause in `getStatus()` / `getDetail()` — cleanest approach
2. Creating a separate mapper for the specific Quarkus parameter exception type

The implementer should investigate the exact exception hierarchy in Quarkus RESTEasy Reactive and choose the appropriate approach.

### R4: Update Tests in `ProblemDetailErrorResponseTest`

**File:** `src/test/java/com/fvd/api/resources/ProblemDetailErrorResponseTest.java`

Add three new test methods to the existing test class:

```java
@Test
void testNonNumericLimitReturnsBadRequest() {
    given()
            .queryParam("keywords", "rest")
            .queryParam("limit", "abc")
            .when().get("/api/search")
            .then()
            .statusCode(400)
            .body("type", equalTo("about:blank"))
            .body("title", equalTo("Bad Request"))
            .body("status", equalTo(400))
            .body("detail", notNullValue())
            .body("instance", containsString("search"))
            .body("timestamp", notNullValue());
}

@Test
void testUnsupportedMediaTypeReturnsNotAcceptable() {
    given()
            .accept("application/xml")
            .queryParam("keywords", "rest")
            .when().get("/api/search")
            .then()
            .statusCode(406)
            .body("type", equalTo("about:blank"))
            .body("title", equalTo("Not Acceptable"))
            .body("status", equalTo(406))
            .body("detail", containsString("application/json"))
            .body("instance", containsString("search"))
            .body("timestamp", notNullValue());
}

@Test
void testPostOnGetOnlyEndpointReturnsMethodNotAllowed() {
    given()
            .queryParam("keywords", "security")
            .when().post("/api/search")
            .then()
            .statusCode(405)
            .body("type", equalTo("about:blank"))
            .body("title", equalTo("Method Not Allowed"))
            .body("status", equalTo(405))
            .body("instance", containsString("search"))
            .body("timestamp", notNullValue());
}
```

**Additional test scenarios (optional but recommended):**

```java
@Test
void testDeleteOnGetOnlyEndpointReturnsMethodNotAllowed() {
    given()
            .when().delete("/api/documents")
            .then()
            .statusCode(405)
            .body("title", equalTo("Method Not Allowed"))
            .body("status", equalTo(405));
}

@Test
void testNonNumericOffsetReturnsBadRequest() {
    given()
            .queryParam("keywords", "rest")
            .queryParam("offset", "xyz")
            .when().get("/api/search")
            .then()
            .statusCode(400)
            .body("title", equalTo("Bad Request"))
            .body("status", equalTo(400));
}

@Test
void testAcceptApplicationXmlOnCatalogReturnsNotAcceptable() {
    given()
            .accept("application/xml")
            .when().get("/api/catalog")
            .then()
            .statusCode(406)
            .body("title", equalTo("Not Acceptable"))
            .body("status", equalTo(406));
}
```

---

## Implementation Notes

### Exception Hierarchy in Quarkus RESTEasy Reactive

The implementer must verify which exception type is actually thrown for Bug 1 (non-numeric `limit`). Common possibilities:

| Scenario | Quarkus RESTEasy Reactive Exception | Parent Class |
|----------|-------------------------------------|--------------|
| Non-numeric Integer param | `org.jboss.resteasy.reactive.server.core.parameters.ParameterExtractor` failure → wrapped in `BadRequestException` or `NotFoundException` | Depends on version |
| Missing `@Produces` match | `jakarta.ws.rs.NotAcceptableException` | `ClientErrorException` |
| Wrong HTTP method | `jakarta.ws.rs.NotAllowedException` | `ClientErrorException` |

**Verification approach:**
1. Run `GET /api/search?keywords=rest&limit=abc` against the running dev server
2. Check the server log for the exception class name (logged by `GenericExceptionMapper` or `NotFoundExceptionMapper`)
3. Implement the mapper for the actual exception type thrown

### Mapper Priority

JAX-RS selects the most specific `ExceptionMapper`. The new mappers handle specific exception types (`NotAllowedException`, `NotAcceptableException`, `BadRequestException`) which are all more specific than `Exception`, so they will take priority over `GenericExceptionMapper`. No explicit `@Priority` annotation is needed.

However, if a `ParamExceptionMapper<BadRequestException>` is added, it must coexist with `InvalidInputExceptionMapper<InvalidInputException>`. Since `InvalidInputException` does not extend `BadRequestException`, there is no conflict.

### No Breaking API Changes

These fixes only change error responses that were already incorrect. Clients receiving 404/500 for these cases were getting wrong information. Returning the correct 400/405/406 codes is a bugfix, not a breaking change.

---

## Tasks

- [ ] Investigate which exception Quarkus RESTEasy Reactive throws for non-numeric query param (Bug 1) — run reproduction case, check logs
- [ ] Create `NotAllowedExceptionMapper` in `src/main/java/com/fvd/common/exceptions/` (R1)
- [ ] Create `NotAcceptableExceptionMapper` in `src/main/java/com/fvd/common/exceptions/` (R2)
- [ ] Create `ParamExceptionMapper` (or modify `NotFoundExceptionMapper`) for parameter binding errors (R3)
- [ ] Add test: non-numeric `limit` returns 400 with ProblemDetail
- [ ] Add test: non-numeric `offset` returns 400 with ProblemDetail
- [ ] Add test: `Accept: application/xml` returns 406 with ProblemDetail
- [ ] Add test: `Accept: application/xml` on `/api/catalog` returns 406
- [ ] Add test: POST on `/api/search` returns 405 with ProblemDetail
- [ ] Add test: DELETE on `/api/documents` returns 405 with ProblemDetail
- [ ] Run `./gradlew test` — all tests pass
- [ ] Verify existing `ProblemDetailErrorResponseTest` tests still pass (no regressions)

---

## Acceptance Criteria

1. `GET /api/search?keywords=rest&limit=abc` returns **400** with RFC 7807 ProblemDetail containing `"title": "Bad Request"` and a detail message referencing the invalid parameter
2. `GET /api/search?keywords=rest&offset=xyz` returns **400** with RFC 7807 ProblemDetail (same pattern as limit)
3. `GET /api/search?keywords=rest` with `Accept: application/xml` returns **406** with ProblemDetail containing `"title": "Not Acceptable"` and detail mentioning `application/json`
4. `POST /api/search?keywords=rest` returns **405** with ProblemDetail containing `"title": "Method Not Allowed"`
5. `DELETE /api/documents` returns **405** with ProblemDetail containing `"title": "Method Not Allowed"`
6. All ProblemDetail responses include `type`, `title`, `status`, `detail`, `instance`, and `timestamp` fields
7. All existing tests in `ProblemDetailErrorResponseTest` continue to pass
8. `./gradlew test` passes with zero failures
9. No changes to existing API behavior for valid requests

---

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Bug 1 exception type varies by Quarkus/RESTEasy Reactive version | Medium | Medium | Investigate actual exception type before implementing; check both `BadRequestException` and `NotFoundException` cause chains |
| `ParamExceptionMapper<BadRequestException>` conflicts with `InvalidInputExceptionMapper<InvalidInputException>` | Very Low | High | `InvalidInputException` does not extend `BadRequestException`; no conflict expected. Verify with test |
| 406 response body may not be serializable to JSON if Accept header rejects JSON | Medium | Low | `AbstractProblemDetailMapper.toResponse()` explicitly sets `.type(MediaType.APPLICATION_JSON)`, overriding the Accept header for error responses |
| `NotAllowedException` response may need `Allow` header per HTTP spec | Medium | Low | Check if Quarkus adds it automatically; if not, consider adding it manually in the mapper or accept the deviation |
| New mappers might intercept framework-internal exceptions unintentionally | Low | Medium | Use specific exception types (not broad superclasses); run full test suite to verify no regressions |

---

## Dependencies

- **None** — this feature is independent and can be implemented without any other feature.
- The existing `AbstractProblemDetailMapper` base class and `ProblemDetail` record are stable and sufficient.
- The existing `ProblemDetailErrorResponseTest` and `AbstractApiResourceTest` provide the test infrastructure.

---

## Estimated Effort

| Task | Hours |
|------|-------|
| Investigate Bug 1 exception type (run reproduction, check logs) | 0.5 |
| Create `NotAllowedExceptionMapper` | 0.25 |
| Create `NotAcceptableExceptionMapper` | 0.25 |
| Create `ParamExceptionMapper` or modify `NotFoundExceptionMapper` | 0.5 |
| Write 6 integration tests in `ProblemDetailErrorResponseTest` | 1.0 |
| Run full test suite and fix regressions | 0.5 |
| **Total** | **~3 hours** |

---

## Files Modified

### Production Code (3 new files)
- `src/main/java/com/fvd/common/exceptions/NotAllowedExceptionMapper.java` — new, maps `NotAllowedException` → 405
- `src/main/java/com/fvd/common/exceptions/NotAcceptableExceptionMapper.java` — new, maps `NotAcceptableException` → 406
- `src/main/java/com/fvd/common/exceptions/ParamExceptionMapper.java` — new, maps `BadRequestException` → 400 (or alternatively modify `NotFoundExceptionMapper.java` if Bug 1 is caused by `NotFoundException`)

### Test Code (1 file modified)
- `src/test/java/com/fvd/api/resources/ProblemDetailErrorResponseTest.java` — add 6 new test methods for the three bug fixes

---

END OF FILE
