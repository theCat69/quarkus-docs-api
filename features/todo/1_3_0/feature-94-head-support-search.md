# Feature 94: HEAD Support on Search Endpoints

> **Dependencies**: None. JAX-RS automatically supports HEAD for GET endpoints, but custom headers (`X-Total-Count`) need explicit implementation.

## Summary

AI agents sometimes need to check if a search would return results (and how many) without downloading the full response body. HTTP HEAD requests return the same headers as GET but with no body. JAX-RS supports HEAD natively for GET endpoints, but the response headers must include useful metadata — specifically an `X-Total-Count` header — for HEAD to be meaningful. This feature adds `X-Total-Count` to GET responses on search endpoints and verifies that HEAD requests work correctly.

## User Story

As an **AI agent deciding whether to execute a full search**, I want to send a HEAD request to check the result count (`X-Total-Count`) without downloading the response body, so that I can make efficient decisions about which queries to pursue.

## Motivation

### Current Behavior

```
HEAD /api/search?keywords=security
→ 200 OK
Content-Type: application/json
(no useful metadata headers)
```

HEAD works (JAX-RS handles it), but the response has no custom headers indicating result count. The agent must issue a full GET to learn anything.

### Desired Behavior

```
HEAD /api/search?keywords=security
→ 200 OK
X-Total-Count: 42
Content-Type: application/json
(no body)
```

The agent can now see that 42 results exist and decide whether to fetch them.

---

## Scope / Requirements

### R1: Add `X-Total-Count` Header to Paginated Responses

**New file:** `src/main/java/com/fvd/common/filters/TotalCountHeaderFilter.java`

A `ContainerResponseFilter` that reads the response entity and adds `X-Total-Count` if it's a `PaginatedResponse`:

```java
package com.fvd.common.filters;

import com.fvd.api.dto.PaginatedResponse;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

@Provider
@Priority(Priorities.ENTITY_CODER - 100) // 3900 — runs before FieldSelectionFilter (4000) so entity is still a typed DTO
public class TotalCountHeaderFilter implements ContainerResponseFilter {

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        Object entity = response.getEntity();
        if (entity instanceof PaginatedResponse<?> paginated) {
            response.getHeaders().putSingle("X-Total-Count",
                    String.valueOf(paginated.getTotalCount()));
        }
    }
}
```

**Design decisions:**
- `@Priority(Priorities.ENTITY_CODER - 100)` (3900) runs before `FieldSelectionFilter` (4000) which converts the entity to `byte[]`, so at this point the entity is still a typed `PaginatedResponse` DTO that can be read via `instanceof`
- Only adds the header when the entity is a `PaginatedResponse` subclass
- Works for all paginated endpoints: `/api/search`, `/api/documents` (search mode), `/api/code-samples`, `/api/documents/related`

### R2: JAX-RS HEAD Behavior

JAX-RS automatically handles HEAD requests for endpoints that define GET methods:
1. The container invokes the GET handler
2. The response entity is computed (including all filters)
3. The body is discarded before sending
4. All headers (including `X-Total-Count`) are preserved

**No explicit `@HEAD` methods are needed.** The filter adds the header, and JAX-RS strips the body for HEAD requests.

### R3: Endpoints Affected

| Endpoint | Returns `PaginatedResponse`? | HEAD useful? |
|----------|------------------------------|--------------|
| `GET /api/search` | Yes (`QuickSearchResponse`) | Yes |
| `GET /api/documents?keywords=...` | Yes (`DocumentSearchResponse`) | Yes |
| `GET /api/code-samples` | Yes (`CodeSampleSearchResponse`) | Yes |
| `GET /api/documents/related` | Yes (`RelatedDocumentResponse`) | Yes |
| `GET /api/documents?path=...` | No (`DocumentResponse`) | No header, but HEAD still works |
| `GET /api/catalog` | No (`CatalogResponse`) | No header, HEAD still works |
| `GET /api/meta` | No (`MetaResponse`) | No header, HEAD still works |

---

## Request/Response Examples

### Example 1: HEAD on search

**Request:**
```
HEAD /api/search?keywords=security&version=3.27
```

**Response (200):**
```
HTTP/1.1 200 OK
X-Total-Count: 42
Content-Type: application/json
Cache-Control: public, max-age=3600
ETag: "a1b2c3d4e5f6a7b8"
```

No body.

### Example 2: HEAD on code samples

**Request:**
```
HEAD /api/code-samples?keywords=rest+endpoint
```

**Response (200):**
```
HTTP/1.1 200 OK
X-Total-Count: 15
Content-Type: application/json
```

### Example 3: GET also includes the header

**Request:**
```
GET /api/search?keywords=security
```

**Response (200):**
```
HTTP/1.1 200 OK
X-Total-Count: 42
Content-Type: application/json

{
    "results": [...],
    "totalCount": 42,
    "returnedCount": 20,
    ...
}
```

`X-Total-Count` is present on both GET and HEAD responses.

---

## Tasks

- [ ] Create `TotalCountHeaderFilter` in `com.fvd.common.filters`
- [ ] Filter checks if entity is `PaginatedResponse` and adds `X-Total-Count` header
- [ ] Set filter priority to `Priorities.ENTITY_CODER - 100` (3900) — before `FieldSelectionFilter` (4000) so entity is still a typed DTO
- [ ] Add unit test: paginated entity adds `X-Total-Count` header
- [ ] Add unit test: non-paginated entity does not add `X-Total-Count` header
- [ ] Add unit test: null entity does not add header
- [ ] Add integration test: `GET /api/search?keywords=security` includes `X-Total-Count` header
- [ ] Add integration test: `HEAD /api/search?keywords=security` returns `X-Total-Count` header and no body
- [ ] Add integration test: `HEAD /api/code-samples?keywords=rest` returns `X-Total-Count` header
- [ ] Add integration test: `HEAD /api/documents?path=security-overview.adoc` returns 200 (no `X-Total-Count` — not paginated)
- [ ] Run `./gradlew test` — all tests pass

---

## Acceptance Criteria

1. `GET /api/search?keywords=security` response includes `X-Total-Count` header with the total result count
2. `HEAD /api/search?keywords=security` returns `X-Total-Count` header with no response body
3. `X-Total-Count` value matches the `totalCount` field in the response body
4. `GET /api/code-samples?keywords=rest` includes `X-Total-Count`
5. `GET /api/documents?keywords=security` includes `X-Total-Count`
6. Non-paginated responses (`GET /api/catalog`, `GET /api/meta`) do not have `X-Total-Count`
7. HEAD requests return the same headers as GET (minus body)
8. `./gradlew test` passes with zero failures

---

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| JAX-RS HEAD implementation executes the full GET handler (including search) | Expected | Low | This is by design — JAX-RS runs the GET handler and strips the body. The search cost is the same as GET. If this becomes a performance concern, explicit `@HEAD` methods with count-only queries can be added later. |
| `PaginatedResponse` uses `@Data` with `protected` fields — `getTotalCount()` generated by Lombok | Low | Low | `@Data` generates public getters; `getTotalCount()` is available. Verify in unit test. |
| Filter priority conflicts with `CorrelationIdFilter` (Feature 91) | Low | Low | `TotalCountHeaderFilter` at 3900, `CorrelationIdFilter` at 3000 — no conflict. They operate on different headers. |
| `X-Total-Count` is not a standard HTTP header | Low | Low | `X-Total-Count` is a widely adopted convention (GitHub API, many REST APIs). AI agents and MCP servers can be configured to read it. |

---

## Estimated Effort

| Task | Hours |
|------|-------|
| Create `TotalCountHeaderFilter` | 0.75 |
| Unit tests for filter | 1.0 |
| Integration tests (GET and HEAD) | 1.5 |
| Run full test suite | 0.25 |
| **Total** | **~3.5 hours** |

---

## Files Modified

### New Production Files (1 file)
- `src/main/java/com/fvd/common/filters/TotalCountHeaderFilter.java` — response filter adding `X-Total-Count` header for paginated responses

### New Test Files (2 files)
- `src/test/java/com/fvd/common/filters/TotalCountHeaderFilterTest.java` — unit tests
- `src/test/java/com/fvd/common/filters/HeadRequestIntegrationTest.java` — integration tests for HEAD support and `X-Total-Count`

---

END OF FILE
