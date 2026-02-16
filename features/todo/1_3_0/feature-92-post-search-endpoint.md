# Feature 92: POST /api/search for Complex Queries

> **Dependencies**: None. This adds a new HTTP method to the existing `SearchResource`. The existing `GET /api/search` remains unchanged.

## Summary

AI agents constructing complex search queries with long keyword lists, multiple filters, and specific pagination parameters may hit URL length limits (~2000 characters) when using `GET /api/search`. This feature adds a `POST /api/search` endpoint that accepts a JSON body with all search parameters. Both `GET` and `POST` return the same `QuickSearchResponse`. The POST body mirrors the GET query parameters: `keywords`, `version`, `subject`, `extension`, `limit`, `offset`.

## User Story

As an **AI agent building complex multi-keyword search queries**, I want to submit search parameters as a JSON body via POST so that I am not constrained by URL length limits and can construct structured queries programmatically without URL encoding concerns.

## Motivation

### Current Behavior

```
GET /api/search?keywords=security+authentication+oidc+jwt+keycloak+authorization+rbac&subject=security&extension=quarkus-oidc&version=3.27&limit=50&offset=0
```

With many keywords and filters, the URL can grow long. URL encoding of special characters adds further length. While 2000 characters is rarely reached with this API, the GET approach is also less ergonomic for programmatic construction by AI agents.

### Desired Behavior

```
POST /api/search
Content-Type: application/json

{
    "keywords": "security authentication oidc jwt keycloak",
    "version": "3.27",
    "subject": "security",
    "extension": "quarkus-oidc",
    "limit": 50,
    "offset": 0
}
→ 200 OK (same QuickSearchResponse as GET)
```

---

## Scope / Requirements

### R1: Create Search Request DTO

**New file:** `src/main/java/com/fvd/api/dto/SearchRequest.java`

```java
package com.fvd.api.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@RegisterForReflection
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Search request body for POST /api/search")
public class SearchRequest {

    @Schema(description = "Space-separated search keywords", required = true,
            example = "security authentication oidc")
    public String keywords;

    @Schema(description = "Quarkus version branch or tag. Defaults to 'main' if omitted.",
            defaultValue = "main", example = "3.27")
    public String version;

    @Schema(description = "Subject filter", example = "security")
    public String subject;

    @Schema(description = "Extension filter", example = "quarkus-oidc")
    public String extension;

    @Schema(description = "Maximum number of results (default 20, max 100)",
            defaultValue = "20", example = "20")
    public Integer limit;

    @Schema(description = "Pagination offset (default 0)",
            defaultValue = "0", example = "0")
    public Integer offset;
}
```

### R2: Add POST Method to SearchResource

**File:** `src/main/java/com/fvd/api/resources/SearchResource.java`

Add a new `POST` method alongside the existing `GET`:

```java
@POST
@Consumes(MediaType.APPLICATION_JSON)
@Operation(
        summary = "Quick discovery search (POST)",
        description = "Same as GET /api/search but accepts parameters as a JSON body. " +
                "Useful for complex queries or when URL length limits are a concern. " +
                "Returns the same QuickSearchResponse."
)
@APIResponse(
        responseCode = "200",
        description = "Search results returned successfully",
        content = @Content(schema = @Schema(implementation = QuickSearchResponse.class))
)
@APIResponse(
        responseCode = "400",
        description = "Invalid input parameters or keywords not provided",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))
)
public QuickSearchResponse searchPost(SearchRequest request) {
    if (request == null) {
        throw new InvalidInputException("Request body is required");
    }

    SearchParams params = SearchParams.fromRaw(
            request.version, request.keywords, request.subject,
            request.extension, request.limit, request.offset);
    InputValidator.validateVersionExists(params.version(), cacheService.listCachedVersions());
    InputValidator.validateSubjectExists(params.subject(), subjectDeriver.getValidSubjectNames());

    return quickSearchService.search(params.version(), params.keywords(), params.subject(),
            params.extension(), params.limit(), params.offset());
}
```

**Key decisions:**
- Reuses `SearchParams.fromRaw()` for validation and normalization — same code path as GET
- Reuses `QuickSearchService.search()` — identical business logic
- `@Consumes(APPLICATION_JSON)` at method level (class only has `@Produces`)
- No `fields` query parameter on POST — field selection via the existing `FieldSelectionFilter` works with query params; callers can add `?fields=title,path` to the POST URL if needed

### R3: Update MetaService

**File:** `src/main/java/com/fvd/api/services/MetaService.java`

Add the POST search endpoint to the endpoints list:

```java
private EndpointMeta buildSearchPostEndpoint() {
    return new EndpointMeta(
            "POST",
            "/api/search",
            "Quick discovery search (POST)",
            "Same as GET /api/search but accepts parameters as a JSON body. " +
                    "Useful for complex queries that would exceed URL length limits.",
            List.of(bodyParam())
    );
}
```

### R4: CacheHeaderFilter Compatibility

The existing `CacheHeaderFilter` skips non-GET methods (`if (!"GET".equals(request.getMethod())) return;`). POST search responses will not receive cache headers. This is correct — POST responses should not be cached by HTTP intermediaries.

---

## Request/Response Examples

### Example 1: POST search

**Request:**
```
POST /api/search
Content-Type: application/json

{
    "keywords": "security authentication oidc",
    "version": "3.27",
    "subject": "security",
    "limit": 10
}
```

**Response (200):**
```json
{
    "results": [
        {
            "path": "security-overview.adoc",
            "title": "Security Overview",
            "subject": "security",
            "score": 15.2,
            "matchedKeywords": ["security", "authentication"],
            "snippet": "...overview of security and authentication features..."
        }
    ],
    "totalCount": 5,
    "returnedCount": 5,
    "offset": 0,
    "limit": 10,
    "hasMore": false
}
```

### Example 2: POST with missing keywords

**Request:**
```
POST /api/search
Content-Type: application/json

{
    "version": "3.27"
}
```

**Response (400):**
```json
{
    "type": "about:blank",
    "title": "Bad Request",
    "status": 400,
    "detail": "keywords must not be empty",
    "instance": "/api/search",
    "timestamp": "2026-02-16T10:00:00Z"
}
```

---

## Tasks

- [ ] Create `SearchRequest` DTO in `com.fvd.api.dto` with `@Schema` annotations
- [ ] Add `@POST` method to `SearchResource` with `@Consumes(APPLICATION_JSON)`
- [ ] Add OpenAPI annotations to POST method
- [ ] Add `@Consumes` import to `SearchResource`
- [ ] Reuse `SearchParams.fromRaw()` and `QuickSearchService.search()` — no new service logic
- [ ] Add POST search endpoint to `MetaService.buildEndpoints()`
- [ ] Add integration test: `POST /api/search` with valid body returns 200 and results
- [ ] Add integration test: POST returns same results as equivalent GET
- [ ] Add integration test: POST with missing keywords returns 400
- [ ] Add integration test: POST with null body returns 400
- [ ] Add integration test: POST with invalid version returns 400
- [ ] Update MetaService unit tests for new endpoint count
- [ ] Run `./gradlew test` — all tests pass

---

## Acceptance Criteria

1. `POST /api/search` accepts a JSON body with `keywords`, `version`, `subject`, `extension`, `limit`, `offset`
2. POST returns the same `QuickSearchResponse` as GET with equivalent parameters
3. POST with missing keywords returns 400 with RFC 7807 `ProblemDetail`
4. POST with null body returns 400
5. `GET /api/search` continues to work unchanged
6. POST search appears in `/api/meta` endpoints list
7. `CacheHeaderFilter` does not add cache headers to POST responses
8. `./gradlew test` passes with zero failures

---

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| POST for read operations confuses API consumers | Low | Low | Well-documented in OpenAPI and `/api/meta`; GET remains available; POST for search is a standard pattern (Elasticsearch, GitHub GraphQL) |
| `@Path("/api/search")` with both GET and POST causes JAX-RS routing confusion | Very Low | Medium | JAX-RS routes by HTTP method; GET and POST on the same path are distinct — standard pattern |
| POST responses not cached by HTTP intermediaries | Expected | Low | POST is not cacheable by HTTP spec; this is acceptable since the API has ETag-based caching only for GET |
| `fields` query parameter not available on POST body | Low | Low | Callers can add `?fields=title,path` as a query param on the POST URL; `FieldSelectionFilter` reads query params regardless of method |

---

## Estimated Effort

| Task | Hours |
|------|-------|
| Create `SearchRequest` DTO | 0.5 |
| Add POST method to `SearchResource` | 0.75 |
| Update `MetaService` | 0.25 |
| Integration tests (5 methods) | 1.5 |
| Update MetaService unit tests | 0.25 |
| Run full test suite | 0.25 |
| **Total** | **~3.5 hours** |

---

## Files Modified

### New Production Files (1 file)
- `src/main/java/com/fvd/api/dto/SearchRequest.java` — POST request body DTO

### Modified Production Files (2 files)
- `src/main/java/com/fvd/api/resources/SearchResource.java` — add `POST` method with `@Consumes(APPLICATION_JSON)`
- `src/main/java/com/fvd/api/services/MetaService.java` — add POST search endpoint to meta list

### Modified Test Files (1 file)
- `src/test/java/com/fvd/api/resources/SearchResourceTest.java` — add integration tests for POST search

### Modified Test Files (1 file)
- `src/test/java/com/fvd/api/services/MetaServiceTest.java` — update endpoint count assertion

---

END OF FILE
