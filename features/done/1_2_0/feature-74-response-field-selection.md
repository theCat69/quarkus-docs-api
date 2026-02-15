# Feature 74: Response Field Selection

> **Dependencies**: None. This is a self-contained cross-cutting enhancement. Compatible with Feature 67 (Lightweight Document Search) — `brief=true` becomes a shorthand that can coexist with `fields`. When both are provided, `fields` takes precedence.

## Summary

AI agents consuming the API through an MCP server often need only a subset of response fields (e.g., just `title` and `path` from a document search, or just `content` from code samples). Currently, the API returns all fields on every response, wasting bandwidth and — critically — MCP context window tokens. This feature adds an optional `fields` query parameter to all endpoints, allowing callers to specify exactly which top-level fields to include in the JSON response. Unspecified fields are omitted entirely from the serialized output (not nulled).

## User Story

As an **AI agent consuming the API through an MCP server**, I want to request only the fields I need (e.g., `fields=title,path,score`) so that API responses consume fewer tokens in my limited context window and I can process results more efficiently.

## Motivation

### Current Behavior (Full Response)

`GET /api/search?keywords=security` returns:

```json
{
    "results": [
        {
            "path": "security-overview.adoc",
            "title": "Security Overview",
            "subject": "security",
            "extension": "quarkus-core",
            "score": 15.2,
            "matchedKeywords": ["secur"],
            "snippet": "Quarkus provides comprehensive security features including..."
        }
    ],
    "totalCount": 42,
    "returnedCount": 20
}
```

An AI agent that only needs `path` and `title` to build a follow-up request still receives `subject`, `extension`, `score`, `matchedKeywords`, and `snippet` — consuming unnecessary tokens.

### Desired Behavior (Field Selection)

`GET /api/search?keywords=security&fields=title,path` returns:

```json
{
    "results": [
        {
            "path": "security-overview.adoc",
            "title": "Security Overview"
        }
    ],
    "totalCount": 42,
    "returnedCount": 20
}
```

Only the requested fields appear on each result item. Envelope fields (`results`, `totalCount`, `returnedCount`) are always included — `fields` applies to the **items inside `results`**, not to the wrapper.

### Token Savings Example

| Scenario | Fields | Approx. tokens per result |
|----------|--------|--------------------------|
| Full `SearchResultRef` | all 7 fields | ~80 tokens |
| `fields=title,path` | 2 fields | ~25 tokens |
| Full `CodeSampleResult` | all 11 fields | ~200 tokens |
| `fields=content,language` | 2 fields | ~100 tokens |
| Full `DocumentResponse` (brief) | 7 fields | ~60 tokens |
| `fields=title,path,score` | 3 fields | ~30 tokens |

For 20 results, selecting only `title,path` on search saves ~1,100 tokens per request.

---

## Scope

### Endpoints Affected

| Endpoint | Response DTO | Item DTO | Selectable Fields |
|----------|-------------|----------|-------------------|
| `GET /api/search` | `QuickSearchResponse` (paginated) | `SearchResultRef` | path, title, subject, extension, score, matchedKeywords, snippet |
| `GET /api/documents?keywords=...` | `DocumentSearchResponse` (paginated) | `DocumentResponse` | title, description, path, subject, extension, sections, codeBlocks, matchedKeywords, score |
| `GET /api/documents?path=...` | `DocumentResponse` (single) | `DocumentResponse` | title, description, path, subject, extension, sections, codeBlocks, matchedKeywords, score |
| `GET /api/code-samples` | `CodeSampleSearchResponse` (paginated) | `CodeSampleResult` | language, content, context, documentPath, documentTitle, subject, extension, matchedKeywords, score, startLine, endLine |
| `GET /api/catalog` | `CatalogResponse` (flat) | N/A (top-level) | subjects, extensions, versions |

### Field Selection Semantics

1. **`fields` applies to item-level DTOs** in paginated responses. Envelope fields (`results`, `totalCount`, `returnedCount`) are always present.
2. **For single-object responses** (`GET /api/documents?path=...` and `GET /api/catalog`), `fields` applies to the top-level object.
3. **Nested field selection is out of scope** for v1. Requesting `fields=sections` returns the entire `sections` array with all sub-fields. Dot-notation like `sections.title` is deferred to a future enhancement.
4. **When `fields` is omitted**, all fields are returned (backward compatible).
5. **When `fields` is empty string**, treat as omitted (return all fields).

### Interaction with `brief=true`

- `brief=true` already nulls out `sections` and `codeBlocks` on `/api/documents`.
- When both `brief=true` and `fields` are provided, `fields` takes precedence. If `fields=sections`, the `sections` field will be included and `brief` will be overridden for that field.
- Recommendation for callers: use `fields` instead of `brief` — it is strictly more powerful. `brief=true` remains for backward compatibility.

---

## Technical Design

### Approach: Jackson `@JsonFilter` with `SimpleBeanPropertyFilter`

Jackson's `@JsonFilter` mechanism allows dynamic field filtering at serialization time without modifying DTOs or creating projection classes. This is the standard Jackson approach for this problem.

#### How It Works

1. **Annotate DTOs** with `@JsonFilter("fieldSelector")` — this tells Jackson to consult a named filter before serializing each property.
2. **Create a `ContainerResponseFilter`** (JAX-RS) that reads the `fields` query parameter and installs a `FilterProvider` with a `SimpleBeanPropertyFilter.filterOutAllExcept(fields)` into the Jackson `ObjectMapper` for the current request.
3. **When `fields` is absent**, use `SimpleBeanPropertyFilter.serializeAll()` — no filtering, full backward compatibility.

#### Why Not Other Approaches?

| Approach | Pros | Cons |
|----------|------|------|
| **`@JsonFilter` (chosen)** | No DTO changes beyond annotation; works with public fields; dynamic per-request; Jackson-native | Requires `ObjectMapper` customization per request |
| **`@JsonView`** | Built into Jackson/JAX-RS | Requires predefined view classes; not dynamic — can't handle arbitrary field combinations |
| **Manual Map projection** | Simple, no framework magic | Duplicates field names; error-prone; loses type safety; requires changes in every service method |
| **GraphQL** | Powerful field selection | Requires entirely new API layer; overkill for this use case |
| **`@JsonInclude(NON_NULL)` + nulling** | Minimal code change | Fields are absent when null — can't distinguish "not requested" from "value is null"; nulling sections is what `brief` already does |

### Implementation Components

#### Component 1: `FieldSelectionFilter` — JAX-RS `ContainerResponseFilter`

**Package:** `com.fvd.common.filters`

**Purpose:** Intercepts outgoing responses, reads the `fields` query parameter from the request URI, and configures Jackson's `FilterProvider` for the current response.

```java
package com.fvd.common.filters;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import java.util.Set;

@Provider
public class FieldSelectionFilter implements ContainerResponseFilter {

    public static final String FILTER_NAME = "fieldSelector";

    @Inject
    ObjectMapper objectMapper;

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        String fieldsParam = request.getUriInfo().getQueryParameters().getFirst("fields");
        if (fieldsParam == null || fieldsParam.isBlank()) {
            return; // No filtering — default behavior
        }
        // Field validation and filtering handled via ObjectMapper
        // See detailed design in R3 below
    }
}
```

**Key concern:** The `ObjectMapper` is a singleton in Quarkus CDI. Mutating it per-request would cause thread-safety issues. Instead, use **one of these two thread-safe approaches**:

- **Option A (Recommended): Manual serialization in the filter.** Read the response entity, serialize it to a JSON string using a request-scoped `ObjectWriter` with the filter applied, and replace the response entity with the pre-serialized string. Set `Content-Type` to `application/json`.
- **Option B: Request-scoped `ObjectMapper` copy.** Use `objectMapper.copy()` to create a per-request copy with the filter applied. This is heavier but cleaner.

**Decision:** Use **Option A** — serialize in the filter. This avoids creating `ObjectMapper` copies and keeps the approach lightweight.

```java
@Override
public void filter(ContainerRequestContext request, ContainerResponseContext response) {
    String fieldsParam = request.getUriInfo().getQueryParameters().getFirst("fields");
    if (fieldsParam == null || fieldsParam.isBlank()) {
        return;
    }

    Object entity = response.getEntity();
    if (entity == null) {
        return;
    }

    Set<String> requestedFields = FieldSelectionValidator.parseAndValidate(
            fieldsParam, entity);

    SimpleFilterProvider filterProvider = new SimpleFilterProvider()
            .addFilter(FILTER_NAME,
                    SimpleBeanPropertyFilter.filterOutAllExcept(requestedFields));

    try {
        String json = objectMapper.writer(filterProvider).writeValueAsString(entity);
        response.setEntity(json);
        response.getHeaders().putSingle("Content-Type", "application/json");
    } catch (Exception e) {
        // Log and let the default serialization proceed
    }
}
```

#### Component 2: `FieldSelectionValidator` — Validation Utility

**Package:** `com.fvd.common.validators`

**Purpose:** Parses the `fields` parameter, validates field names against the response DTO, and returns 400 with available fields if any are invalid.

```java
package com.fvd.common.validators;

import com.fvd.common.exceptions.InvalidInputException;
import lombok.experimental.UtilityClass;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@UtilityClass
public class FieldSelectionValidator {

    public static Set<String> parseAndValidate(String fieldsParam, Object entity) {
        Set<String> requested = Arrays.stream(fieldsParam.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());

        if (requested.isEmpty()) {
            return Set.of(); // Empty after trimming — treat as "all fields"
        }

        // Determine the item class (unwrap paginated responses)
        Class<?> itemClass = resolveItemClass(entity);
        Set<String> available = getFieldNames(itemClass);

        Set<String> invalid = requested.stream()
                .filter(f -> !available.contains(f))
                .collect(Collectors.toSet());

        if (!invalid.isEmpty()) {
            throw new InvalidInputException(
                    "Unknown field(s): " + String.join(", ", invalid) +
                    ". Available fields: " + String.join(", ", available.stream().sorted().toList()));
        }

        return requested;
    }

    private static Set<String> getFieldNames(Class<?> clazz) {
        return Arrays.stream(clazz.getFields())
                .map(Field::getName)
                .collect(Collectors.toSet());
    }

    private static Class<?> resolveItemClass(Object entity) {
        // For PaginatedResponse subtypes, resolve the item type
        // For direct DTOs (DocumentResponse, CatalogResponse), use the entity class
        // Implementation uses reflection on the results list or a type registry
        // See detailed design in R4 below
    }
}
```

#### Component 3: DTO Annotations

**Files:** All DTOs that support field selection.

Add `@JsonFilter("fieldSelector")` to each item-level DTO:

```java
@JsonFilter("fieldSelector")
@RegisterForReflection
@NoArgsConstructor
@AllArgsConstructor
public class SearchResultRef { ... }
```

**DTOs to annotate:**
- `SearchResultRef`
- `DocumentResponse`
- `CodeSampleResult`
- `CatalogResponse`

**Important:** When `@JsonFilter` is on a class but no filter is registered with the `ObjectMapper`, Jackson throws an exception. To prevent this, register a **default filter** that serializes all properties:

```java
// In an ObjectMapperCustomizer or startup bean:
objectMapper.setFilterProvider(
    new SimpleFilterProvider()
        .addFilter("fieldSelector", SimpleBeanPropertyFilter.serializeAll())
        .setFailOnUnknownId(false)
);
```

#### Component 4: `ObjectMapper` Default Configuration

**Package:** `com.fvd.common.config` (new class)

**Purpose:** Register the default `fieldSelector` filter so DTOs annotated with `@JsonFilter("fieldSelector")` serialize normally when no `fields` parameter is provided.

```java
package com.fvd.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import io.quarkus.jackson.ObjectMapperCustomizer;
import jakarta.inject.Singleton;

@Singleton
public class FieldSelectionObjectMapperCustomizer implements ObjectMapperCustomizer {

    @Override
    public void customize(ObjectMapper objectMapper) {
        SimpleFilterProvider filterProvider = new SimpleFilterProvider()
                .addFilter(FieldSelectionFilter.FILTER_NAME,
                        SimpleBeanPropertyFilter.serializeAll())
                .setFailOnUnknownId(false);
        objectMapper.setFilterProvider(filterProvider);
    }
}
```

### Handling Paginated vs. Single Responses

The `fields` filter must apply to the **item DTOs inside `results`**, not to the paginated wrapper. Jackson handles this naturally because `@JsonFilter` is on the item DTO class, not on `PaginatedResponse`. When Jackson serializes `PaginatedResponse.results` (a `List<T>`), it serializes each `T` element using `T`'s filter.

For the `GET /api/documents?path=...` endpoint (returns a single `DocumentResponse`), the filter applies directly to the top-level object.

For `GET /api/catalog` (returns `CatalogResponse`), the filter applies to the top-level `CatalogResponse` fields: `subjects`, `extensions`, `versions`.

### Field Validation — Resolving the Item Class

The `FieldSelectionValidator` needs to know which fields are available on the response DTO to validate the `fields` parameter. The challenge is that paginated responses wrap item DTOs in a generic `List<T>`.

**Approach: Type registry map.**

```java
private static final Map<Class<?>, Class<?>> ITEM_TYPE_REGISTRY = Map.of(
    QuickSearchResponse.class, SearchResultRef.class,
    DocumentSearchResponse.class, DocumentResponse.class,
    CodeSampleSearchResponse.class, CodeSampleResult.class,
    CatalogResponse.class, CatalogResponse.class,
    DocumentResponse.class, DocumentResponse.class
);
```

When the filter intercepts a response, it looks up `entity.getClass()` in the registry to determine which class to validate field names against.

**Edge case:** `DocumentResource.getDocuments()` returns `Object` (either `DocumentResponse` or `DocumentSearchResponse`). The filter must check `entity.getClass()` at runtime.

---

## Request/Response Examples

### Example 1: Search with field selection

**Request:**
```
GET /api/search?keywords=security&fields=title,path,score
```

**Response (200):**
```json
{
    "results": [
        {
            "path": "security-overview.adoc",
            "title": "Security Overview",
            "score": 15.2
        },
        {
            "path": "security-oidc.adoc",
            "title": "OpenID Connect",
            "score": 12.8
        }
    ],
    "totalCount": 42,
    "returnedCount": 20
}
```

### Example 2: Code samples with field selection

**Request:**
```
GET /api/code-samples?keywords=rest+endpoint&fields=content,language,documentPath
```

**Response (200):**
```json
{
    "results": [
        {
            "language": "java",
            "content": "@Path(\"/hello\")\npublic class HelloResource { ... }",
            "documentPath": "rest-getting-started.adoc"
        }
    ],
    "totalCount": 15,
    "returnedCount": 15
}
```

### Example 3: Document by path with field selection

**Request:**
```
GET /api/documents?path=security-overview.adoc&fields=title,sections
```

**Response (200):**
```json
{
    "title": "Security Overview",
    "sections": [
        {
            "title": "Authentication",
            "level": 2,
            "content": "...",
            "startLine": 15,
            "endLine": 120
        }
    ]
}
```

### Example 4: Invalid field name

**Request:**
```
GET /api/search?keywords=security&fields=title,nonexistent,path
```

**Response (400):**
```json
{
    "type": "about:blank",
    "title": "Bad Request",
    "status": 400,
    "detail": "Unknown field(s): nonexistent. Available fields: extension, matchedKeywords, path, score, snippet, subject, title",
    "instance": "/api/search",
    "timestamp": "2026-02-15T10:30:00Z"
}
```

### Example 5: Catalog with field selection

**Request:**
```
GET /api/catalog?fields=subjects,versions
```

**Response (200):**
```json
{
    "subjects": [
        {
            "name": "security",
            "displayName": "Security",
            "description": "Authentication, authorization, and security features",
            "docCount": 15,
            "keywords": ["security", "oidc", "jwt"]
        }
    ],
    "versions": ["main", "3.27", "3.21"]
}
```

### Example 6: No fields parameter (backward compatible)

**Request:**
```
GET /api/search?keywords=security
```

**Response (200):** Full response with all fields — identical to current behavior.

---

## Requirements

### R1: Add `@JsonFilter("fieldSelector")` to Item DTOs

**Files:**
- `src/main/java/com/fvd/api/dto/SearchResultRef.java`
- `src/main/java/com/fvd/api/dto/DocumentResponse.java`
- `src/main/java/com/fvd/api/dto/CodeSampleResult.java`
- `src/main/java/com/fvd/api/dto/CatalogResponse.java`

Add `@JsonFilter("fieldSelector")` annotation to each class. Import `com.fasterxml.jackson.annotation.JsonFilter`.

### R2: Register Default Filter via `ObjectMapperCustomizer`

**New file:** `src/main/java/com/fvd/common/config/FieldSelectionObjectMapperCustomizer.java`

Register a `SimpleBeanPropertyFilter.serializeAll()` filter with name `"fieldSelector"` as the default. This ensures DTOs annotated with `@JsonFilter` serialize normally when no `fields` parameter is present. Set `setFailOnUnknownId(false)` for safety.

### R3: Create `FieldSelectionFilter` (JAX-RS `ContainerResponseFilter`)

**New file:** `src/main/java/com/fvd/common/filters/FieldSelectionFilter.java`

- Annotate with `@Provider`
- Read `fields` query parameter from `ContainerRequestContext.getUriInfo().getQueryParameters()`
- If `fields` is null or blank, return immediately (no-op)
- Parse fields into a `Set<String>`, validate against the response entity's item class
- Serialize the entity using `objectMapper.writer(filterProvider).writeValueAsString(entity)` with a `SimpleBeanPropertyFilter.filterOutAllExcept(requestedFields)`
- Replace the response entity with the serialized JSON string
- Set `Content-Type: application/json`

### R4: Create `FieldSelectionValidator`

**New file:** `src/main/java/com/fvd/common/validators/FieldSelectionValidator.java`

- `@UtilityClass` following project conventions
- `parseAndValidate(String fieldsParam, Object entity)` method:
  - Split on `,`, trim, remove empty strings
  - Resolve the item class via type registry (paginated wrapper → item DTO)
  - Get available field names via `Class.getFields()` (public fields)
  - If any requested fields are not in available set, throw `InvalidInputException` listing unknown and available fields
  - Return `Set<String>` of validated field names

### R5: Add `fields` Query Parameter to All Resource Endpoints (OpenAPI Only)

The `fields` parameter does not need to be a method parameter on each resource — the `ContainerResponseFilter` reads it from the URI. However, for **OpenAPI documentation**, add `@Parameter` annotations.

**Option A (Recommended):** Since the filter is global and reads from the query string, the `fields` param is handled transparently. For OpenAPI, add a reusable `@Parameter` to each endpoint method signature:

```java
@Parameter(
        description = "Comma-separated list of fields to include in each result item. " +
                "When omitted, all fields are returned. " +
                "Invalid field names return 400 with the list of available fields. " +
                "Example: 'title,path,score'",
        required = false,
        example = "title,path,score"
)
@QueryParam("fields") String fields
```

The `fields` variable does not need to be used in the method body — it is consumed by the `ContainerResponseFilter`. Adding it as a `@QueryParam` ensures it appears in the OpenAPI spec and is properly documented.

**Files to modify:**
- `src/main/java/com/fvd/api/resources/SearchResource.java`
- `src/main/java/com/fvd/api/resources/DocumentResource.java`
- `src/main/java/com/fvd/api/resources/CodeSampleResource.java`
- `src/main/java/com/fvd/api/resources/CatalogResource.java`

### R6: Add `@RegisterForReflection` to New Classes

Quarkus native image compilation requires `@RegisterForReflection` on classes accessed reflectively. Since `FieldSelectionValidator` uses `Class.getFields()`, ensure all DTOs already have `@RegisterForReflection` (they do). The new filter and validator classes do not need it unless they are serialized.

---

## Implementation Notes

### Thread Safety

The `ObjectMapper` injected into `FieldSelectionFilter` is a CDI singleton. The filter **must not** mutate it. Instead, call `objectMapper.writer(filterProvider)` which creates a lightweight, immutable `ObjectWriter` — this is thread-safe and does not modify the underlying `ObjectMapper`.

### Performance

The `ContainerResponseFilter` runs on every response. When `fields` is absent, it returns immediately (single null check). When `fields` is present:
- Field validation uses `Class.getFields()` which is cached by the JVM
- `objectMapper.writer(filterProvider).writeValueAsString()` serializes the entity once — the same work the default serializer would do, but with a filter applied
- No extra deserialization/re-serialization — the entity is serialized directly to a filtered JSON string

### Nested Field Selection (Deferred)

Selecting sub-fields (e.g., `sections.title` to return only section titles without content) is **not included** in this feature. Reasons:
- Jackson's `SimpleBeanPropertyFilter` operates on direct properties of the annotated class, not nested properties
- Supporting nested fields would require recursive filter application or custom serializers
- The `brief=true` precedent shows that field-level toggling (all-or-nothing on `sections`) is sufficient for current use cases
- Can be added as Feature 69+ if needed

### `CatalogResponse` Special Case

`CatalogResponse` is not a paginated response — it has top-level fields (`subjects`, `extensions`, `versions`). The field filter applies directly:
- `fields=subjects` returns only the `subjects` array
- `fields=versions` returns only the `versions` array
- The inner objects (`SubjectInfo`, `ExtensionInfo`) are not filtered — requesting `fields=subjects` returns the full `SubjectInfo` objects

### Interaction with Error Responses

The `ContainerResponseFilter` should check the response status. If status >= 400 (error response), skip field filtering — error responses use the `ProblemDetail` format which should not be filtered.

---

## Tasks

- [ ] Create `FieldSelectionObjectMapperCustomizer` in `com.fvd.common.config` — register default `serializeAll()` filter for `"fieldSelector"`
- [ ] Add `@JsonFilter("fieldSelector")` annotation to `SearchResultRef`
- [ ] Add `@JsonFilter("fieldSelector")` annotation to `DocumentResponse`
- [ ] Add `@JsonFilter("fieldSelector")` annotation to `CodeSampleResult`
- [ ] Add `@JsonFilter("fieldSelector")` annotation to `CatalogResponse`
- [ ] Create `FieldSelectionValidator` in `com.fvd.common.validators` — parse, validate, and return field set
- [ ] Create `FieldSelectionFilter` (`ContainerResponseFilter`) in `com.fvd.common.filters` — read `fields` param, validate, serialize with filter
- [ ] Add `@QueryParam("fields")` with `@Parameter` OpenAPI annotation to `SearchResource.search()`
- [ ] Add `@QueryParam("fields")` with `@Parameter` OpenAPI annotation to `DocumentResource.getDocuments()`
- [ ] Add `@QueryParam("fields")` with `@Parameter` OpenAPI annotation to `CodeSampleResource.searchCodeSamples()`
- [ ] Add `@QueryParam("fields")` with `@Parameter` OpenAPI annotation to `CatalogResource.getCatalog()`
- [ ] Handle error response bypass in `FieldSelectionFilter` (skip when status >= 400)
- [ ] Add unit tests for `FieldSelectionValidator`:
    - Valid field names return correct set
    - Invalid field names throw `InvalidInputException` with available fields
    - Empty/blank `fields` returns empty set (treated as "all")
    - Mixed valid and invalid fields throws exception listing only the invalid ones
    - Validates against correct item class for each paginated response type
- [ ] Add unit tests for `FieldSelectionFilter`:
    - No `fields` parameter — entity unchanged
    - Valid `fields` — entity serialized with only requested fields
    - Error response (status 400) — entity unchanged
- [ ] Add integration tests for `SearchResource`:
    - `GET /api/search?keywords=security&fields=title,path` returns only `title` and `path` on each result
    - `GET /api/search?keywords=security&fields=title,path` still includes `totalCount` and `returnedCount`
    - `GET /api/search?keywords=security` (no fields) returns all fields (backward compatible)
    - `GET /api/search?keywords=security&fields=nonexistent` returns 400 with available fields
- [ ] Add integration tests for `DocumentResource`:
    - `GET /api/documents?keywords=security&fields=title,score` returns filtered results
    - `GET /api/documents?path=security-overview.adoc&fields=title,path` returns filtered single document
    - `GET /api/documents?keywords=security&fields=title&brief=true` — fields takes precedence
- [ ] Add integration tests for `CodeSampleResource`:
    - `GET /api/code-samples?keywords=rest&fields=content,language` returns filtered results
- [ ] Add integration tests for `CatalogResource`:
    - `GET /api/catalog?fields=versions` returns only versions
- [ ] Verify all existing tests still pass — `@JsonFilter` with default `serializeAll()` must not change behavior
- [ ] Run `./gradlew test` — all tests pass

---

## Acceptance Criteria

1. `GET /api/search?keywords=security&fields=title,path` returns results containing **only** `title` and `path` fields — no `subject`, `extension`, `score`, `matchedKeywords`, or `snippet`
2. `GET /api/search?keywords=security&fields=title,path` response still includes `results`, `totalCount`, and `returnedCount` envelope fields
3. `GET /api/search?keywords=security` (no `fields`) returns all fields unchanged (full backward compatibility)
4. `GET /api/search?keywords=security&fields=nonexistent` returns 400 with RFC 7807 ProblemDetail listing available field names
5. `GET /api/documents?path=security-overview.adoc&fields=title,sections` returns only `title` and `sections` on the single document
6. `GET /api/documents?keywords=security&fields=title,score` returns paginated results with only `title` and `score` per item
7. `GET /api/code-samples?keywords=rest&fields=content,language` returns code samples with only `content` and `language`
8. `GET /api/catalog?fields=versions` returns only the `versions` field on the catalog response
9. Error responses (400, 404, 502) are never affected by `fields` — ProblemDetail format is always returned in full
10. `fields` parameter appears in OpenAPI spec on all four endpoints with description and example
11. All existing tests pass unchanged — adding `@JsonFilter` with default `serializeAll()` does not alter existing behavior
12. `./gradlew test` passes with zero failures

---

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| `@JsonFilter` on DTOs breaks existing serialization when no filter is registered | High (if missed) | Critical | Register default `serializeAll()` filter via `ObjectMapperCustomizer` at startup; verify in CI |
| Thread-safety issues if `ObjectMapper` singleton is mutated per-request | High (if wrong approach) | Critical | Use `objectMapper.writer(filterProvider)` which creates immutable `ObjectWriter` — never mutate the singleton |
| `ContainerResponseFilter` double-serializes: once in filter, once by JAX-RS | Medium | Medium | Replace entity with `String` and set `Content-Type` header — JAX-RS will pass through the string as-is |
| `Class.getFields()` reflection fails in GraalVM native image | Medium | High | All DTOs already have `@RegisterForReflection`; verify with native build |
| Clients send `fields` with wrong case (e.g., `Title` vs `title`) | Low | Low | Java field names are camelCase and documented; case-sensitive matching is correct; error message lists available fields |
| `DocumentResource.getDocuments()` returns `Object` — runtime type resolution needed | Medium | Low | Use `entity.getClass()` at runtime in the filter; type registry handles both `DocumentResponse` and `DocumentSearchResponse` |
| Performance overhead of re-serialization in the filter | Low | Low | Only runs when `fields` param is present; serialization is the same work the default would do |
| YAML ObjectMapper instances in quarkiverse package inherit the filter | Low | Low | They are separate `ObjectMapper` instances constructed with `new ObjectMapper(YAMLFactory)` — not affected by CDI customizer |

---

## Estimated Effort

| Task | Hours |
|------|-------|
| Create `FieldSelectionObjectMapperCustomizer` | 0.5 |
| Add `@JsonFilter` annotations to 4 DTOs | 0.5 |
| Create `FieldSelectionValidator` with type registry | 1.5 |
| Create `FieldSelectionFilter` (ContainerResponseFilter) | 2.0 |
| Add `@QueryParam("fields")` with OpenAPI annotations to 4 resources | 1.0 |
| Unit tests for `FieldSelectionValidator` | 1.0 |
| Unit tests for `FieldSelectionFilter` | 1.0 |
| Integration tests across all 4 endpoints | 2.0 |
| Verify existing tests pass with `@JsonFilter` annotations | 0.5 |
| Run full test suite and fix regressions | 1.0 |
| **Total** | **~11.0 hours** |

---

## Files Modified

### New Production Files (3 files)
- `src/main/java/com/fvd/common/config/FieldSelectionObjectMapperCustomizer.java` — register default Jackson filter
- `src/main/java/com/fvd/common/filters/FieldSelectionFilter.java` — JAX-RS ContainerResponseFilter
- `src/main/java/com/fvd/common/validators/FieldSelectionValidator.java` — field name parsing and validation

### Modified Production Files (8 files)
- `src/main/java/com/fvd/api/dto/SearchResultRef.java` — add `@JsonFilter("fieldSelector")`
- `src/main/java/com/fvd/api/dto/DocumentResponse.java` — add `@JsonFilter("fieldSelector")`
- `src/main/java/com/fvd/api/dto/CodeSampleResult.java` — add `@JsonFilter("fieldSelector")`
- `src/main/java/com/fvd/api/dto/CatalogResponse.java` — add `@JsonFilter("fieldSelector")`
- `src/main/java/com/fvd/api/resources/SearchResource.java` — add `fields` query parameter with OpenAPI annotation
- `src/main/java/com/fvd/api/resources/DocumentResource.java` — add `fields` query parameter with OpenAPI annotation
- `src/main/java/com/fvd/api/resources/CodeSampleResource.java` — add `fields` query parameter with OpenAPI annotation
- `src/main/java/com/fvd/api/resources/CatalogResource.java` — add `fields` query parameter with OpenAPI annotation

### New Test Files (estimated 3 files)
- `src/test/java/com/fvd/common/validators/FieldSelectionValidatorTest.java` — unit tests for field validation
- `src/test/java/com/fvd/common/filters/FieldSelectionFilterTest.java` — unit tests for the response filter
- `src/test/java/com/fvd/common/filters/FieldSelectionIntegrationTest.java` — integration tests across all endpoints

### Unchanged Files
- `src/main/java/com/fvd/api/dto/PaginatedResponse.java` — no `@JsonFilter` needed (envelope always serialized)
- `src/main/java/com/fvd/api/dto/SectionInfo.java` — not filtered (nested fields out of scope)
- `src/main/java/com/fvd/api/dto/CodeBlockInfo.java` — not filtered (nested fields out of scope)
- `src/main/java/com/fvd/api/dto/SubjectInfo.java` — not filtered (nested fields out of scope)
- `src/main/java/com/fvd/api/dto/ExtensionInfo.java` — not filtered (nested fields out of scope)
- `src/main/java/com/fvd/common/exceptions/InvalidInputException.java` — reused as-is
- `src/main/java/com/fvd/common/exceptions/InvalidInputExceptionMapper.java` — reused as-is
- `src/main/resources/application.properties` — no configuration changes needed

---

END OF FILE
