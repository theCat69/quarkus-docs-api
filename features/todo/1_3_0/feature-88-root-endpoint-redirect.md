# Feature 88: Root Endpoint Redirect to Meta

> **Dependencies**: Feature 71 (API Meta/Capabilities Endpoint) must already be implemented — it is. No other dependencies.

## Summary

`GET /` currently returns a 404 error because no JAX-RS resource is mapped to the root path. AI agents often start by hitting the root URL to discover API capabilities. This feature adds a simple root resource that returns a 200 JSON response with a welcome message and links to `/api/meta` and `/q/openapi`, guiding agents to the right entry points immediately.

## User Story

As an **AI agent connecting to the Quarkus Docs API for the first time**, I want `GET /` to return a helpful response instead of 404 so that I can immediately discover the API's meta endpoint and start making useful requests without prior configuration.

## Motivation

### Current Behavior

```
GET /
→ 404 Not Found
{
    "type": "about:blank",
    "title": "Not Found",
    "status": 404,
    "detail": "Resource not found",
    "instance": "/",
    "timestamp": "2026-02-16T10:00:00Z"
}
```

AI agents and MCP servers that probe the root URL get a 404, which may cause them to assume the API is down or misconfigured.

### Desired Behavior

```
GET /
→ 200 OK
{
    "message": "Quarkus Docs API",
    "documentation": "/api/meta",
    "openapi": "/q/openapi"
}
```

A lightweight JSON response that acts as a signpost. No redirect — a 200 with links is more predictable for programmatic consumers than a 301/307 that requires following redirects.

---

## Scope / Requirements

### R1: Create `RootResource`

**New file:** `src/main/java/com/fvd/api/resources/RootResource.java`

```java
package com.fvd.api.resources;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.Map;

@Path("/")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Root", description = "API entry point")
public class RootResource {

    @GET
    @Operation(
            summary = "API entry point",
            description = "Returns a welcome message with links to API documentation endpoints. " +
                    "Start here to discover the API."
    )
    public Map<String, String> root() {
        return Map.of(
                "message", "Quarkus Docs API",
                "documentation", "/api/meta",
                "openapi", "/q/openapi"
        );
    }
}
```

**Design decisions:**
- Returns `Map<String, String>` — no dedicated DTO for 3 static fields
- Returns 200 (not 301/307) — AI agents and MCP servers handle 200 JSON better than redirects
- No `Cache-Control` header — the `CacheHeaderFilter` skips non-`api/` paths, but a root response is tiny and rarely repeated
- No version parameter — this is a static signpost

### R2: Update MetaService Endpoints List

**File:** `src/main/java/com/fvd/api/services/MetaService.java`

Add the root endpoint to the endpoints list in `buildEndpoints()` so `/api/meta` reflects the new root:

```java
private EndpointMeta buildRootEndpoint() {
    return new EndpointMeta(
            "GET",
            "/",
            "API entry point",
            "Returns a welcome message with links to /api/meta and /q/openapi. " +
                    "Hit this first if you don't know where to start.",
            List.of()
    );
}
```

---

## Request/Response Examples

### Example 1: Root endpoint

**Request:**
```
GET /
```

**Response (200):**
```json
{
    "message": "Quarkus Docs API",
    "documentation": "/api/meta",
    "openapi": "/q/openapi"
}
```

---

## Tasks

- [ ] Create `RootResource` in `com.fvd.api.resources` with `GET /` returning a `Map<String, String>`
- [ ] Add OpenAPI annotations (`@Operation`, `@Tag`)
- [ ] Add root endpoint to `MetaService.buildEndpoints()` list
- [ ] Add integration test: `GET /` returns 200 with `message`, `documentation`, `openapi` fields
- [ ] Add integration test: `documentation` field value is `/api/meta`
- [ ] Add integration test: response `Content-Type` is `application/json`
- [ ] Update MetaService unit tests to expect 9 endpoints instead of 8
- [ ] Run `./gradlew test` — all tests pass

---

## Acceptance Criteria

1. `GET /` returns 200 with `Content-Type: application/json`
2. Response body contains `"message": "Quarkus Docs API"`
3. Response body contains `"documentation": "/api/meta"`
4. Response body contains `"openapi": "/q/openapi"`
5. `/api/meta` endpoints list includes the root endpoint
6. No existing endpoints are affected
7. `./gradlew test` passes with zero failures

---

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| `@Path("/")` conflicts with Quarkus internal paths | Low | Medium | Quarkus serves `/q/*` paths separately; JAX-RS root path does not interfere |
| `Map.of()` serialization order is non-deterministic | Low | Low | JSON field order is not guaranteed; agents parse by key name |
| `CacheHeaderFilter` adds unexpected headers to root | Eliminated | — | Filter checks `path.startsWith("api/")` — root path `/` is skipped |

---

## Estimated Effort

| Task | Hours |
|------|-------|
| Create `RootResource` | 0.5 |
| Update `MetaService` | 0.25 |
| Integration tests | 0.75 |
| Update MetaService unit tests | 0.25 |
| Run full test suite | 0.25 |
| **Total** | **~2.0 hours** |

---

## Files Modified

### New Production Files (1 file)
- `src/main/java/com/fvd/api/resources/RootResource.java` — `GET /` returning JSON signpost

### Modified Production Files (1 file)
- `src/main/java/com/fvd/api/services/MetaService.java` — add root endpoint to endpoints list

### New Test Files (1 file)
- `src/test/java/com/fvd/api/resources/RootResourceTest.java` — integration tests for `GET /`

### Modified Test Files (1 file)
- `src/test/java/com/fvd/api/services/MetaServiceTest.java` — update endpoint count assertion (8 → 9)

---

END OF FILE
