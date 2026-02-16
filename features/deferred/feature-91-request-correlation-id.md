# Feature 91: Request Correlation ID

> **Dependencies**: None. This is a self-contained filter feature. Compatible with the existing `CacheHeaderFilter` and `FieldSelectionFilter` — runs at a different priority.

## Summary

When an MCP server relays requests from multiple AI agents concurrently, there is no way to correlate a specific API response (or error) back to the originating request. This feature adds a `ContainerRequestFilter` / `ContainerResponseFilter` pair that reads or generates an `X-Request-Id` header. If the incoming request includes `X-Request-Id`, the value is passed through to the response. If not, a UUID is generated. The correlation ID is also added to the `ProblemDetail` error responses as the `instance` field suffix and to MDC for structured logging.

## User Story

As an **MCP server operator debugging issues with concurrent AI agent requests**, I want every API response to include an `X-Request-Id` header so that I can correlate requests and responses across my system, and trace errors back to specific agent interactions.

## Motivation

### Current Behavior

```
GET /api/search?keywords=nonexistent
→ 200 OK
(no X-Request-Id header)

GET /api/documents?path=missing.adoc
→ 404 Not Found
{
    "instance": "/api/documents",
    ...
}
(no request correlation)
```

When 10 agents make requests simultaneously, log entries and error responses cannot be traced back to a specific request.

### Desired Behavior

```
GET /api/search?keywords=security
X-Request-Id: agent-session-abc-123
→ 200 OK
X-Request-Id: agent-session-abc-123

GET /api/documents?path=missing.adoc
→ 404 Not Found
X-Request-Id: 550e8400-e29b-41d4-a716-446655440000
{
    "instance": "/api/documents",
    ...
}
```

---

## Scope / Requirements

### R1: Create `CorrelationIdFilter`

**New file:** `src/main/java/com/fvd/common/filters/CorrelationIdFilter.java`

A combined request/response filter:

```java
package com.fvd.common.filters;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.util.UUID;

@Slf4j
@Provider
@Priority(Priorities.HEADER_DECORATOR) // = 3000. Runs early, before other filters
public class CorrelationIdFilter implements ContainerRequestFilter, ContainerResponseFilter {

    public static final String HEADER_NAME = "X-Request-Id";
    public static final String MDC_KEY = "requestId";
    private static final String PROPERTY_KEY = "correlation.requestId";

    @Override
    public void filter(ContainerRequestContext request) {
        String requestId = request.getHeaderString(HEADER_NAME);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }
        request.setProperty(PROPERTY_KEY, requestId);
        MDC.put(MDC_KEY, requestId);
    }

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        Object requestId = request.getProperty(PROPERTY_KEY);
        if (requestId != null) {
            response.getHeaders().putSingle(HEADER_NAME, requestId.toString());
        }
        MDC.remove(MDC_KEY);
    }
}
```

**Design decisions:**
- `@Priority(Priorities.HEADER_DECORATOR)` (3000) runs before `FieldSelectionFilter` (4000) and `CacheHeaderFilter` (4000+) — correlation ID is set before any response processing
- Uses `ContainerRequestContext.setProperty()` to pass the ID from request to response filter
- Adds to SLF4J MDC so log statements include the correlation ID
- Cleans up MDC in the response filter to prevent thread-local leaks

### R2: Preserve Client-Provided Correlation ID

If the client sends `X-Request-Id: my-custom-id`, the filter uses that value instead of generating a UUID. This allows MCP servers to propagate their own correlation IDs through the API.

### R3: Add Correlation ID to Log Format (Optional)

**File:** `src/main/resources/application.properties`

```properties
# Include request ID in log output
quarkus.log.console.format=%d{yyyy-MM-dd HH:mm:ss} %-5p [%c{2.}] (%t) [%X{requestId}] %s%e%n
```

The `%X{requestId}` pattern reads from MDC. When no request is active, it is blank.

---

## Request/Response Examples

### Example 1: Client provides correlation ID

**Request:**
```
GET /api/search?keywords=security
X-Request-Id: mcp-session-42
```

**Response (200):**
```
HTTP/1.1 200 OK
X-Request-Id: mcp-session-42
Content-Type: application/json

{ ... }
```

### Example 2: No client ID — server generates UUID

**Request:**
```
GET /api/search?keywords=security
```

**Response (200):**
```
HTTP/1.1 200 OK
X-Request-Id: 550e8400-e29b-41d4-a716-446655440000
Content-Type: application/json

{ ... }
```

### Example 3: Error response includes correlation ID

**Request:**
```
GET /api/documents?path=nonexistent.adoc
```

**Response (404):**
```
HTTP/1.1 404 Not Found
X-Request-Id: 7c9e6679-7425-40de-944b-e07fc1f90ae7
Content-Type: application/json

{
    "type": "about:blank",
    "title": "Not Found",
    "status": 404,
    "detail": "Document not found: nonexistent.adoc",
    "instance": "/api/documents",
    "timestamp": "2026-02-16T10:00:00Z"
}
```

---

## Tasks

- [ ] Create `CorrelationIdFilter` in `com.fvd.common.filters` implementing both `ContainerRequestFilter` and `ContainerResponseFilter`
- [ ] Read `X-Request-Id` from incoming request; generate UUID if absent
- [ ] Store correlation ID in request properties for response filter access
- [ ] Add `X-Request-Id` to all response headers
- [ ] Add correlation ID to SLF4J MDC (`requestId` key)
- [ ] Clean up MDC in response filter
- [ ] Add log format configuration with `%X{requestId}` pattern to `application.properties`
- [ ] Add unit test: filter generates UUID when no `X-Request-Id` header present
- [ ] Add unit test: filter passes through client-provided `X-Request-Id`
- [ ] Add unit test: response contains `X-Request-Id` header
- [ ] Add unit test: MDC is set during request processing and cleaned up after
- [ ] Add integration test: `GET /api/search?keywords=security` returns `X-Request-Id` header
- [ ] Add integration test: `GET /api/search` with `X-Request-Id: custom-id` returns `X-Request-Id: custom-id`
- [ ] Add integration test: error response (400/404) includes `X-Request-Id` header
- [ ] Run `./gradlew test` — all tests pass

---

## Acceptance Criteria

1. Every API response includes an `X-Request-Id` header
2. When the client sends `X-Request-Id`, the same value is returned in the response
3. When no `X-Request-Id` is sent, a UUID is generated and returned
4. Error responses (400, 404, 500) include the `X-Request-Id` header
5. Correlation ID is available in SLF4J MDC during request processing
6. MDC is cleaned up after each request (no thread-local leaks)
7. Log output includes the `requestId` when configured
8. `./gradlew test` passes with zero failures

---

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| MDC not cleaned up on async/reactive code paths | Low | Medium | Quarkus REST is synchronous (not Mutiny-based); MDC cleanup in response filter is reliable. If reactive endpoints are added later, use Quarkus context propagation. |
| UUID generation performance | Very Low | Low | `UUID.randomUUID()` uses `SecureRandom`; ~1μs per call — negligible |
| `X-Request-Id` header conflicts with Quarkus internal headers | Very Low | Low | `X-Request-Id` is a standard convention; no Quarkus internal use |
| Client sends maliciously long `X-Request-Id` values | Low | Low | HTTP servers limit header sizes (~8KB); could add a length check (e.g., max 128 chars) but not critical for v1 |
| Response filter doesn't run on exception paths | Low | Medium | Quarkus exception mappers produce `Response` objects that go through response filters; verified by testing error responses |

---

## Estimated Effort

| Task | Hours |
|------|-------|
| Create `CorrelationIdFilter` | 1.0 |
| Add log format configuration | 0.25 |
| Unit tests | 1.5 |
| Integration tests | 1.0 |
| Run full test suite | 0.25 |
| **Total** | **~4.0 hours** |

---

## Files Modified

### New Production Files (1 file)
- `src/main/java/com/fvd/common/filters/CorrelationIdFilter.java` — request/response filter for `X-Request-Id` generation and propagation

### Modified Production Files (1 file)
- `src/main/resources/application.properties` — add log format with `%X{requestId}` MDC pattern

### New Test Files (2 files)
- `src/test/java/com/fvd/common/filters/CorrelationIdFilterTest.java` — unit tests for filter logic
- `src/test/java/com/fvd/common/filters/CorrelationIdIntegrationTest.java` — integration tests verifying header presence across endpoints

---

END OF FILE
