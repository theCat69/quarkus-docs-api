# Feature 84: HTTP Cache Headers for Versioned Content

> **Dependencies**: Feature 83 (Readiness & Warmup Status Endpoint) must be implemented first if the `/api/status` exclusion is to be tested end-to-end. Otherwise, this feature is self-contained. Compatible with Feature 74 (Response Field Selection) — requires adding `@Priority` to the existing `FieldSelectionFilter` so that the `CacheHeaderFilter` runs after it and computes ETags on the final (field-filtered) response body.

## Summary

The API returns no HTTP cache headers (`Cache-Control`, `ETag`), forcing MCP servers and HTTP clients to re-fetch identical content on every request. Since documentation content is versioned and changes infrequently (refreshed every 6 hours via `app.refresh.interval=6h`), responses can be safely cached. This feature adds `Cache-Control` and `ETag` headers to all API GET responses, with conditional GET support (`If-None-Match` → `304 Not Modified`), allowing MCP servers to cache responses locally and avoid redundant calls.

## User Story

As an **AI agent consuming the API through an MCP server**, I want the API to support HTTP caching (`ETag`, `Cache-Control`, `If-None-Match`) so that repeated requests for the same content return `304 Not Modified` instead of re-transmitting the full response body, reducing latency, bandwidth usage, and redundant processing in the MCP server.

## Motivation

### Current Behavior (No Cache Headers)

```
GET /api/documents?path=security-overview.adoc&version=3.27
→ 200 OK (15KB body, no Cache-Control, no ETag)

# 30 seconds later, same request:
GET /api/documents?path=security-overview.adoc&version=3.27
→ 200 OK (15KB body again, identical content re-transmitted)
```

Every request re-serializes and re-transmits the full response, even when the content has not changed. For MCP servers making repeated lookups (e.g., an agent asking about the same document multiple times in a conversation), this wastes bandwidth and latency.

### Desired Behavior (With Cache Headers)

```
GET /api/documents?path=security-overview.adoc&version=3.27
→ 200 OK
  Cache-Control: public, max-age=3600
  ETag: "a1b2c3d4e5f6"
  (15KB body)

# 30 seconds later, same request with ETag:
GET /api/documents?path=security-overview.adoc&version=3.27
If-None-Match: "a1b2c3d4e5f6"
→ 304 Not Modified
  (0 bytes body — use cached version)
```

### Cache Duration Strategy

| Content type | `max-age` | Rationale |
|-------------|-----------|-----------|
| Versioned content (`version=3.20`, `version=3.27`) | `3600` (1 hour) | Tagged versions are immutable snapshots; content only changes on cache refresh (every 6h) |
| `version=main` content | `900` (15 minutes) | `main` tracks HEAD of the docs repo; more volatile |
| Catalog (`/api/catalog`) | `1800` (30 minutes) | Catalog changes only when indexes are rebuilt |
| Status (`/api/status`, Feature 83) | `0` (no-cache) | Status is real-time; must never be cached |
| Meta (`/api/meta`) | Skipped by filter | `MetaResource` already sets its own `Cache-Control: public, max-age=3600` header |
| Error responses (4xx, 5xx) | No cache headers | Errors should not be cached |

### Bandwidth Savings Estimate

| Scenario | Without caching | With caching (304) | Savings |
|----------|----------------|-------------------|---------|
| 20 repeated document lookups (15KB each) | 300KB | 0KB (20× 304) | 100% |
| 50 search requests (same query, 5KB each) | 250KB | 5KB (1× 200 + 49× 304) | 98% |
| Agent conversation with 10 doc lookups | ~150KB | ~15KB | 90% |

---

## Scope / Requirements

### R0 (Prerequisite): Add `@Priority` to Existing `FieldSelectionFilter`

**Modified file:** `src/main/java/com/fvd/common/filters/FieldSelectionFilter.java`

The existing `FieldSelectionFilter` has **no `@Priority` annotation**. Without explicit ordering, JAX-RS does not guarantee filter execution order, so the `CacheHeaderFilter` could run before `FieldSelectionFilter`. This would cause ETags to be computed on the **pre-filtered entity** — ETags would then be identical regardless of the `fields` parameter, and conditional GET would return stale/wrong content when different `fields` values are used.

**Required change:** Add `@Priority(Priorities.ENTITY_CODER)` to `FieldSelectionFilter` so it runs at a well-defined priority **before** `CacheHeaderFilter`:

```java
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;

@Slf4j
@Provider
@Priority(Priorities.ENTITY_CODER) // = 4000. Runs BEFORE CacheHeaderFilter (4100)
@RegisterForReflection
public class FieldSelectionFilter implements ContainerResponseFilter {
    // ... existing code unchanged ...
}
```

> **Note on JAX-RS response filter ordering:** For `ContainerResponseFilter`, **lower `@Priority` values run first**. `FieldSelectionFilter` at `Priorities.ENTITY_CODER` (4000) runs before `CacheHeaderFilter` at `Priorities.ENTITY_CODER + 100` (4100). This ensures the entity is field-filtered before the ETag is computed.

### R1: Create `CacheHeaderFilter` — JAX-RS `ContainerResponseFilter`

**New file:** `src/main/java/com/fvd/common/filters/CacheHeaderFilter.java`

**Package:** `com.fvd.common.filters`

A `@Provider` `ContainerResponseFilter` that adds `Cache-Control` and `ETag` headers to successful API GET responses:

```java
package com.fvd.common.filters;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.security.MessageDigest;
import java.util.HexFormat;

@Provider
@Priority(Priorities.ENTITY_CODER + 100) // = 4100. Runs AFTER FieldSelectionFilter (4000)
public class CacheHeaderFilter implements ContainerResponseFilter {

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "app.cache.http.max-age.versioned", defaultValue = "3600")
    int maxAgeVersioned;

    @ConfigProperty(name = "app.cache.http.max-age.main", defaultValue = "900")
    int maxAgeMain;

    @ConfigProperty(name = "app.cache.http.max-age.catalog", defaultValue = "1800")
    int maxAgeCatalog;

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        // Only apply to GET requests — skip POST (batch endpoint), PUT, DELETE, etc.
        if (!"GET".equals(request.getMethod())) return;

        // Only apply to successful responses (2xx)
        if (response.getStatus() < 200 || response.getStatus() >= 300) return;

        // Only apply to API paths — skip health (/q/health), OpenAPI (/q/openapi),
        // Swagger UI (/q/swagger-ui), dev UI (/q/dev-ui), and any other non-API paths
        String path = request.getUriInfo().getPath();
        if (!path.startsWith("api/")) return;

        // Skip status endpoint — must never be cached (real-time warmup state)
        if (path.startsWith("api/status")) return;

        // Skip meta endpoint — MetaResource already sets its own Cache-Control header
        if (path.startsWith("api/meta")) return;

        // Determine max-age based on path and version parameter
        String version = request.getUriInfo().getQueryParameters().getFirst("version");
        int maxAge = resolveMaxAge(path, version);

        response.getHeaders().putSingle("Cache-Control", "public, max-age=" + maxAge);

        // Compute ETag from response entity
        Object entity = response.getEntity();
        if (entity != null) {
            String etag = computeETag(entity);
            response.getHeaders().putSingle("ETag", "\"" + etag + "\"");

            // Conditional GET: check If-None-Match
            String ifNoneMatch = request.getHeaderString("If-None-Match");
            if (ifNoneMatch != null && ifNoneMatch.equals("\"" + etag + "\"")) {
                response.setStatus(304);
                response.setEntity(null);
                response.getHeaders().remove("Content-Type");
                return;
            }
        }
    }

    private int resolveMaxAge(String path, String version) {
        if (path.startsWith("api/catalog")) return maxAgeCatalog;
        if ("main".equals(version) || version == null) return maxAgeMain;
        return maxAgeVersioned;
    }

    private String computeETag(Object entity) {
        try {
            byte[] content;
            if (entity instanceof byte[] bytes) {
                // FieldSelectionFilter produces byte[] via objectMapper.writeValueAsBytes()
                content = bytes;
            } else {
                // No field selection — serialize entity to bytes for hashing
                content = objectMapper.writeValueAsBytes(entity);
            }
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(content);
            return HexFormat.of().formatHex(hash, 0, 8); // 16 hex chars
        } catch (Exception e) {
            return Integer.toHexString(entity.hashCode());
        }
    }
}
```

**Key design decisions:**

1. **`byte[]` entity handling (Critical Issue 1):** The `FieldSelectionFilter` calls `objectMapper.writer(filterProvider).writeValueAsBytes(entity)` and sets the entity to `byte[]` (see `FieldSelectionFilter.java` line 59). The `computeETag` method checks for `byte[]` first and hashes it directly — no re-serialization needed.

2. **`@Priority` ordering (Critical Issue 2):** `CacheHeaderFilter` uses `@Priority(Priorities.ENTITY_CODER + 100)` = 4100, which runs after `FieldSelectionFilter` at `@Priority(Priorities.ENTITY_CODER)` = 4000. For response filters, lower priority values execute first.

3. **API path prefix guard (Critical Issue 3):** The filter checks `path.startsWith("api/")` and returns early for non-API paths. This prevents cache headers from being added to Quarkus internal paths like `/q/health`, `/q/openapi`, `/q/swagger-ui`, `/q/dev-ui`, etc.

4. **POST requests skipped (Issue 4):** The `!"GET".equals(request.getMethod())` guard at the top explicitly skips POST requests (used by the batch endpoint at `POST /api/documents/batch`) and all other non-GET methods.

5. **ObjectMapper injection (Issue 5):** `ObjectMapper` is injected via `@Inject` for serializing entities that haven't been pre-serialized by `FieldSelectionFilter`.

### R2: Implement Conditional GET Support (`If-None-Match` → 304)

Conditional GET is implemented inline within the `CacheHeaderFilter` (see R1 code above). After computing the ETag, the filter checks the `If-None-Match` request header. If the values match, the filter:

1. Sets status to `304 Not Modified`
2. Nulls out the entity (no response body)
3. Removes `Content-Type` header (not needed for 304)

The `Cache-Control` and `ETag` headers remain on the 304 response so the client can continue caching.

**Important:** The `If-None-Match` check happens **after** the entity is available and the ETag is computed. This is correct because `ContainerResponseFilter` runs after the resource method returns the entity.

### R3: ETag Computation Strategy

The ETag is computed as a SHA-256 hash (truncated to 16 hex chars) of the serialized response body bytes. The `computeETag` method handles three entity type scenarios:

| Entity type | When it occurs | Handling |
|-------------|---------------|----------|
| `byte[]` | `FieldSelectionFilter` ran (request had `fields` param) | Hash the `byte[]` directly — no serialization needed |
| Java object (DTO) | No `fields` param — entity is the raw DTO | Serialize via `objectMapper.writeValueAsBytes(entity)`, then hash |
| `null` | No entity (e.g., 204) | Skip ETag entirely (guarded by `entity != null` check) |

**ETag properties:**
- **Deterministic:** Same content always produces the same ETag
- **Content-sensitive:** Different `fields` values produce different byte[] → different ETags
- **Compact:** 16 hex chars (8 bytes of hash)
- **Collision-resistant:** SHA-256 with 64 bits of entropy; collision probability ~1 in 2^32 per pair

### R4: Filter Ordering with `FieldSelectionFilter`

**Execution order for response filters (lower `@Priority` runs first):**

```
Request → Resource Method → Entity produced (Java object)
    │
    ├── FieldSelectionFilter (@Priority(4000) = Priorities.ENTITY_CODER)
    │   └── If 'fields' param present: serializes entity to byte[] with field filtering
    │   └── If no 'fields' param: does nothing, entity remains as Java object
    │
    └── CacheHeaderFilter (@Priority(4100) = Priorities.ENTITY_CODER + 100)
        ├── Guard: skip non-GET, skip non-2xx, skip non-api/ paths, skip status/meta
        ├── Compute ETag from entity:
        │   ├── If byte[] (from FieldSelectionFilter): hash directly
        │   └── If Java object: serialize via ObjectMapper then hash
        ├── Set Cache-Control header
        ├── Set ETag header
        └── Check If-None-Match → 304 if match
```

**Why this ordering matters:** If `CacheHeaderFilter` ran before `FieldSelectionFilter`, the ETag would be computed on the full unfiltered entity. Then two requests — one with `fields=title` and one with `fields=title,path` — would produce the **same** ETag despite returning different response bodies. A client caching the first response and sending `If-None-Match` on the second would incorrectly receive `304`, getting the wrong cached content.

### R5: Make Cache Durations Configurable

**File:** `src/main/resources/application.properties`

Add configurable cache durations:

```properties
# HTTP cache durations (seconds)
app.cache.http.max-age.versioned=3600
app.cache.http.max-age.main=900
app.cache.http.max-age.catalog=1800
```

These are injected via `@ConfigProperty` with defaults in the filter (see R1).

### R6: Skip Cache Headers on Error Responses and Non-API Paths

The filter has multiple guard clauses that skip cache header injection:

| Guard | Condition | Rationale |
|-------|-----------|-----------|
| Non-GET method | `!"GET".equals(request.getMethod())` | POST (batch), PUT, DELETE should not be cached |
| Non-2xx status | `status < 200 \|\| status >= 300` | Errors (4xx, 5xx) should not be cached |
| Non-API path | `!path.startsWith("api/")` | Quarkus internal paths (`/q/health`, `/q/openapi`, `/q/swagger-ui`) must not get cache headers |
| Status endpoint | `path.startsWith("api/status")` | Real-time warmup status must never be cached |
| Meta endpoint | `path.startsWith("api/meta")` | `MetaResource` already sets its own `Cache-Control` header |

### R7: MetaResource Overlap

`MetaResource` already manually sets `Cache-Control: public, max-age=3600` on its response (line 44 of `MetaResource.java`). To avoid conflicting or duplicate headers, the `CacheHeaderFilter` skips paths starting with `api/meta`. This keeps the meta endpoint's caching behavior self-contained and avoids a double `Cache-Control` header scenario.

---

## Technical Design

### Filter Architecture

```
Request → Resource Method → Entity produced
    │
    ├── FieldSelectionFilter (@Priority(4000) = Priorities.ENTITY_CODER)
    │   └── Serializes entity to byte[] with field filtering (if fields param present)
    │
    └── CacheHeaderFilter (@Priority(4100) = Priorities.ENTITY_CODER + 100)
        ├── Guard: GET only, 2xx only, api/ prefix only, skip status + meta
        ├── Compute ETag from entity (byte[] or Object → ObjectMapper → byte[])
        ├── Check If-None-Match → 304 if match
        ├── Set Cache-Control header
        └── Set ETag header
```

### Entity Type Flow

```
Scenario A: Request with ?fields=title,path
  Resource → DTO → FieldSelectionFilter → byte[] → CacheHeaderFilter (hashes byte[])

Scenario B: Request without fields param
  Resource → DTO → FieldSelectionFilter (no-op) → CacheHeaderFilter (serializes DTO → byte[], hashes)

Scenario C: Conditional GET with If-None-Match
  Resource → DTO → FieldSelectionFilter → byte[] → CacheHeaderFilter → ETag matches → 304
```

### ETag Format

ETags are quoted strings per HTTP spec: `"a1b2c3d4e5f6a7b8"`. The value is the first 16 hex characters of the SHA-256 hash of the serialized response body bytes. This provides:
- **Uniqueness:** SHA-256 has negligible collision probability
- **Determinism:** Same content always produces the same ETag
- **Compactness:** 16 hex chars (8 bytes of hash) is short enough for headers

### `version=null` Handling

When no `version` parameter is provided, the API defaults to `"main"`. The filter reads the raw query parameter — if absent, it defaults to the `main` cache duration (900 seconds). This is correct because `null` version resolves to `main` in all resource methods.

### Performance Impact

- **ETag computation:** SHA-256 hashing adds ~0.1ms per response (negligible). When `FieldSelectionFilter` has already serialized to `byte[]`, the `CacheHeaderFilter` hashes those bytes directly — no double-serialization. When no `fields` param, the filter calls `objectMapper.writeValueAsBytes(entity)` to serialize once.
- **304 responses:** Zero serialization cost — the filter short-circuits after ETag comparison, before the body is written.
- **Memory:** No additional memory — the hash is computed inline.

### Double-Serialization Concern

When `FieldSelectionFilter` hasn't run (no `fields` parameter), `CacheHeaderFilter` serializes the entity to `byte[]` to compute the ETag. JAX-RS then serializes the entity again to write the response body. This is a minor performance cost for a simple operation.

**Potential optimization (future):** After computing the ETag, replace the entity with the serialized `byte[]` (same pattern as `FieldSelectionFilter`). JAX-RS passes through `byte[]` as-is, avoiding double-serialization. However, this optimization adds complexity and risk — if the `CacheHeaderFilter` serialization differs from JAX-RS's default serialization (e.g., missing `@JsonView`, custom serializers), the response would be inconsistent. **Decision: defer this optimization** and accept the minimal double-serialization cost.

### Interaction with Quarkus Response Building

Some resource methods return `Response` objects directly (e.g., `MetaResource`). The `CacheHeaderFilter` works with both entity-returning methods and `Response`-returning methods because `ContainerResponseContext` abstracts both patterns. `MetaResource` is explicitly skipped to avoid header conflicts.

### Thread Safety

The filter is stateless (config values are injected once at startup, `ObjectMapper` is a singleton). No mutable state — thread-safe by design.

### Quarkus Native Image

`MessageDigest.getInstance("SHA-256")` is supported in GraalVM native images. No additional `@RegisterForReflection` needed.

---

## Request/Response Examples

### Example 1: First request — full response with ETag

**Request:**
```
GET /api/documents?path=security-overview.adoc&version=3.27
```

**Response (200):**
```
HTTP/1.1 200 OK
Cache-Control: public, max-age=3600
ETag: "a1b2c3d4e5f6a7b8"
Content-Type: application/json

{
    "title": "Security Overview",
    "path": "security-overview.adoc",
    ...
}
```

### Example 2: Conditional GET — 304 Not Modified

**Request:**
```
GET /api/documents?path=security-overview.adoc&version=3.27
If-None-Match: "a1b2c3d4e5f6a7b8"
```

**Response (304):**
```
HTTP/1.1 304 Not Modified
ETag: "a1b2c3d4e5f6a7b8"
Cache-Control: public, max-age=3600
```

No body.

### Example 3: `version=main` — shorter cache duration

**Request:**
```
GET /api/search?keywords=security&version=main
```

**Response (200):**
```
HTTP/1.1 200 OK
Cache-Control: public, max-age=900
ETag: "b2c3d4e5f6a7b8c9"
Content-Type: application/json

{ ... }
```

### Example 4: Catalog — moderate cache duration

**Request:**
```
GET /api/catalog?version=3.27
```

**Response (200):**
```
HTTP/1.1 200 OK
Cache-Control: public, max-age=1800
ETag: "c3d4e5f6a7b8c9d0"
Content-Type: application/json

{ ... }
```

### Example 5: Status endpoint — no caching (Feature 83 dependency)

**Request:**
```
GET /api/status
```

**Response (200):**
```
HTTP/1.1 200 OK
Content-Type: application/json

{ "ready": true, ... }
```

No `Cache-Control` or `ETag` headers — skipped by the `api/status` path guard.

### Example 6: Error response — no caching

**Request:**
```
GET /api/documents?path=nonexistent.adoc&version=3.27
```

**Response (404):**
```
HTTP/1.1 404 Not Found
Content-Type: application/json

{ "type": "about:blank", "title": "Not Found", ... }
```

No `Cache-Control` or `ETag` headers — skipped by the `status >= 300` guard.

### Example 7: POST batch request — no caching

**Request:**
```
POST /api/documents/batch
Content-Type: application/json

{ "paths": ["security-overview.adoc", "getting-started.adoc"] }
```

**Response (200):**
```
HTTP/1.1 200 OK
Content-Type: application/json

{ "documents": [...], "errors": [] }
```

No `Cache-Control` or `ETag` headers — skipped by the GET-only guard.

### Example 8: OpenAPI spec — no caching

**Request:**
```
GET /q/openapi
```

**Response:**
```
HTTP/1.1 200 OK
Content-Type: application/yaml

openapi: 3.0.3
...
```

No `Cache-Control` or `ETag` headers — skipped by the `api/` path prefix guard.

### Example 9: Field selection with caching — different fields produce different ETags

**Request A:**
```
GET /api/documents?path=security-overview.adoc&version=3.27&fields=title
```

**Response (200):**
```
HTTP/1.1 200 OK
Cache-Control: public, max-age=3600
ETag: "1111111111111111"
Content-Type: application/json

{ "title": "Security Overview" }
```

**Request B (different fields):**
```
GET /api/documents?path=security-overview.adoc&version=3.27&fields=title,path
If-None-Match: "1111111111111111"
```

**Response (200) — NOT 304, because the fields are different:**
```
HTTP/1.1 200 OK
Cache-Control: public, max-age=3600
ETag: "2222222222222222"
Content-Type: application/json

{ "title": "Security Overview", "path": "security-overview.adoc" }
```

This works correctly because `FieldSelectionFilter` runs first (priority 4000), producing different `byte[]` for different field sets. `CacheHeaderFilter` (priority 4100) then hashes the already-filtered `byte[]`, producing a different ETag.

---

## Implementation Notes

### Test Profile

In tests, cache headers are irrelevant to business logic. Tests should verify headers are present but not rely on cache behavior. RestAssured assertions can check:
```java
.header("Cache-Control", containsString("max-age="))
.header("ETag", notNullValue())
```

### Conditional GET Integration Test Pattern

To test the 304 flow:
```java
// Step 1: GET the resource, capture the ETag
String etag = given()
    .queryParam("path", "security-overview.adoc")
    .queryParam("version", "3.27")
    .get("/api/documents")
    .then()
    .statusCode(200)
    .extract().header("ETag");

// Step 2: Repeat with If-None-Match
given()
    .queryParam("path", "security-overview.adoc")
    .queryParam("version", "3.27")
    .header("If-None-Match", etag)
    .get("/api/documents")
    .then()
    .statusCode(304)
    .body(emptyOrNullString());
```

---

## Tasks

- [ ] **Add `@Priority(Priorities.ENTITY_CODER)` to `FieldSelectionFilter`** — prerequisite; ensures deterministic filter execution order. Add `import jakarta.annotation.Priority` and `import jakarta.ws.rs.Priorities` to the existing file. This is the ONLY change to `FieldSelectionFilter`.
- [ ] Create `CacheHeaderFilter` in `com.fvd.common.filters` with `@Priority(Priorities.ENTITY_CODER + 100)`:
    - Inject `ObjectMapper` via `@Inject`
    - Inject configurable `max-age` values via `@ConfigProperty`
    - Guard: skip non-GET methods (POST batch, etc.)
    - Guard: skip non-2xx responses
    - Guard: skip non-`api/` paths (health, OpenAPI, Swagger UI, dev UI)
    - Guard: skip `api/status` (real-time, never cached)
    - Guard: skip `api/meta` (already has its own Cache-Control)
- [ ] Implement `resolveMaxAge` method using injected config properties
- [ ] Implement ETag computation using SHA-256 hash of serialized response body bytes:
    - Handle `byte[]` entity (from `FieldSelectionFilter`) — hash directly
    - Handle Java object entity — serialize via `objectMapper.writeValueAsBytes()` then hash
    - Fallback to `entity.hashCode()` on serialization error
- [ ] Implement conditional GET: check `If-None-Match` header, return 304 if ETag matches
- [ ] Set `Cache-Control: public, max-age=3600` for versioned content (explicit non-`main` version)
- [ ] Set `Cache-Control: public, max-age=900` for `version=main` or no version specified
- [ ] Set `Cache-Control: public, max-age=1800` for catalog responses
- [ ] Add configurable cache duration properties to `application.properties`
- [ ] Add unit tests for `CacheHeaderFilter`:
    - GET request with versioned content → `Cache-Control: public, max-age=3600` and ETag present
    - GET request with `version=main` → `max-age=900`
    - GET request with no version → `max-age=900`
    - GET request to catalog → `max-age=1800`
    - POST request → no cache headers
    - Error response (404) → no cache headers
    - Status endpoint → no cache headers
    - Meta endpoint → no cache headers
    - Non-API path (`/q/openapi`) → no cache headers
    - Entity is `byte[]` → ETag computed from raw bytes
    - Entity is Java DTO → ETag computed from `ObjectMapper.writeValueAsBytes()`
    - Entity is `null` → no ETag header
- [ ] Add unit tests for conditional GET:
    - `If-None-Match` matches ETag → 304 with no body
    - `If-None-Match` does not match → 200 with full body
    - No `If-None-Match` header → 200 with full body and ETag
- [ ] Add integration tests across endpoints:
    - `GET /api/search` returns `Cache-Control` and `ETag` headers
    - `GET /api/documents?path=...` returns headers
    - `GET /api/catalog` returns headers with `max-age=1800`
    - `POST /api/documents/batch` does NOT return cache headers
    - Repeated request with `If-None-Match` returns 304
- [ ] Add integration test for `FieldSelectionFilter` interaction:
    - Same endpoint with different `fields` values produces different ETags
    - Same endpoint with same `fields` value produces same ETag
- [ ] Verify `@Priority` ordering — `FieldSelectionFilter` must run before `CacheHeaderFilter`
- [ ] Run `./gradlew test` — all tests pass

---

## Acceptance Criteria

1. `GET /api/documents?path=security-overview.adoc&version=3.27` returns `Cache-Control: public, max-age=3600` and an `ETag` header
2. `GET /api/search?keywords=security&version=main` returns `Cache-Control: public, max-age=900`
3. `GET /api/search?keywords=security` (no version, defaults to main) returns `Cache-Control: public, max-age=900`
4. `GET /api/catalog?version=3.27` returns `Cache-Control: public, max-age=1800`
5. `GET /api/documents?path=security-overview.adoc&version=3.27` with `If-None-Match: "<matching-etag>"` returns `304 Not Modified` with no body
6. `GET /api/documents?path=security-overview.adoc&version=3.27` with `If-None-Match: "<wrong-etag>"` returns `200` with full body
7. `GET /api/status` returns **no** `Cache-Control` or `ETag` headers (when Feature 83 is implemented)
8. `GET /api/meta` returns its existing self-managed `Cache-Control` header, **not** a duplicate from the filter
9. Error responses (400, 404, 500) return **no** `Cache-Control` or `ETag` headers
10. `POST /api/documents/batch` returns **no** `Cache-Control` or `ETag` headers
11. `GET /q/openapi` returns **no** `Cache-Control` or `ETag` headers from the filter
12. `GET /q/health/ready` returns **no** `Cache-Control` or `ETag` headers from the filter
13. Same endpoint with `fields=title` and `fields=title,path` produces **different** ETags
14. Cache durations are configurable via `application.properties`
15. `FieldSelectionFilter` has `@Priority(Priorities.ENTITY_CODER)` annotation
16. `CacheHeaderFilter` has `@Priority(Priorities.ENTITY_CODER + 100)` annotation
17. All existing tests pass unchanged
18. `./gradlew test` passes with zero failures

---

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Double-serialization for requests without `fields` parameter degrades performance | Medium | Low | `objectMapper.writeValueAsBytes()` is fast for small DTOs; acceptable for v1. Can optimize later by replacing entity with `byte[]` |
| ETag hash collisions (SHA-256 truncated to 16 hex chars) | Very Low | Low | 16 hex chars = 64 bits of entropy; collision probability ~1 in 2^32 per pair |
| `If-None-Match` with `*` (wildcard) not handled | Low | Low | Wildcard ETags are rarely used by MCP servers; can be added later if needed |
| Stale cache after index rebuild — client receives 304 for outdated content | Medium | Medium | ETag is content-based; when index rebuilds, content changes, ETag changes, `If-None-Match` fails, client gets fresh 200 |
| `FieldSelectionFilter` ordering conflict — ETag computed on pre-filtered entity | ~~Medium~~ Eliminated | High | **Fixed:** Explicit `@Priority` on both filters guarantees execution order |
| `Cache-Control: public` inappropriate for user-specific data | Very Low | Low | All API data is public documentation — no user-specific content; `public` is correct |
| Filter runs on non-API endpoints (health, OpenAPI, Swagger UI) | ~~Low~~ Eliminated | Low | **Fixed:** `api/` path prefix guard prevents cache headers on non-API paths |
| `MetaResource` gets duplicate `Cache-Control` header | ~~Medium~~ Eliminated | Low | **Fixed:** Filter skips `api/meta` path; `MetaResource` manages its own caching |
| `ObjectMapper` serialization in filter differs from JAX-RS default serialization | Low | Medium | Both use the same Quarkus-configured `ObjectMapper` singleton; difference is unlikely but should be verified in integration tests |
| Feature 83 (`/api/status`) not yet implemented — status path guard is untestable | Low | Low | The guard is a simple string check that works regardless of whether the endpoint exists; test with a mock or defer integration test until Feature 83 ships |

---

## Estimated Effort

| Task | Hours |
|------|-------|
| Add `@Priority` to `FieldSelectionFilter` | 0.25 |
| Create `CacheHeaderFilter` with ETag, Cache-Control, and guards | 2.0 |
| Implement conditional GET (If-None-Match → 304) | 1.0 |
| Add configurable cache duration properties | 0.5 |
| Unit tests for `CacheHeaderFilter` (all guard clauses + entity types) | 2.0 |
| Unit tests for conditional GET | 1.0 |
| Integration tests across all endpoints | 1.5 |
| Integration test for `FieldSelectionFilter` interaction (different fields → different ETags) | 0.5 |
| Run full test suite and fix regressions | 0.5 |
| **Total** | **~9.25 hours** |

---

## Files Modified

### New Production Files (1 file)
- `src/main/java/com/fvd/common/filters/CacheHeaderFilter.java` — `ContainerResponseFilter` adding Cache-Control, ETag, and conditional GET support. Injects `ObjectMapper` and `@ConfigProperty` cache durations.

### Modified Production Files (2 files)
- `src/main/java/com/fvd/common/filters/FieldSelectionFilter.java` — add `@Priority(Priorities.ENTITY_CODER)` annotation and imports. **No other changes.**
- `src/main/resources/application.properties` — add `app.cache.http.max-age.*` configuration properties

### New Test Files (estimated 2 files)
- `src/test/java/com/fvd/common/filters/CacheHeaderFilterTest.java` — unit tests for header generation, guard clauses, entity type handling, and conditional GET
- `src/test/java/com/fvd/common/filters/CacheHeaderIntegrationTest.java` — integration tests verifying headers across endpoints, field selection interaction, and 304 flow

### Unchanged Files
- `src/main/java/com/fvd/api/resources/DocumentResource.java` — no changes; filter is global
- `src/main/java/com/fvd/api/resources/SearchResource.java` — no changes; filter is global
- `src/main/java/com/fvd/api/resources/CatalogResource.java` — no changes; filter is global
- `src/main/java/com/fvd/api/resources/MetaResource.java` — no changes; keeps its own `Cache-Control` header

---

## Dependencies

- **Feature 83 (Readiness & Warmup Status Endpoint):** The `/api/status` path guard is present but the endpoint itself is defined in Feature 83. The guard works regardless (it's just a path prefix check), but integration testing requires Feature 83 to be implemented.
- **Feature 74 (Response Field Selection):** Requires modifying `FieldSelectionFilter` to add `@Priority`. The filter's behavior is unchanged; only the execution order annotation is added.
- No new library dependencies — `MessageDigest` is in `java.security` (JDK standard), `ObjectMapper` is already available via Quarkus Jackson.

---

END OF FILE
