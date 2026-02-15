# Feature 79: Add Brief Parameter to Batch Endpoint

> **Dependencies**: None. This is a self-contained enhancement. The `brief` field already exists in `BatchDocumentRequest` and is already handled by `DocumentService.getDocumentsBatch()`. This feature ensures the `fields` query parameter (Feature 74) also works on the batch endpoint, and documents the existing `brief` capability in OpenAPI.

## Summary

The `POST /api/documents/batch` endpoint already supports a `brief` boolean in the request body (`BatchDocumentRequest.brief`), and `DocumentService.getDocumentsBatch()` already uses it to skip section/code block parsing. However, the `fields` query parameter from Feature 74 is already wired via the `@QueryParam("fields")` on the endpoint. This feature focuses on ensuring the existing `brief` + `fields` combination works correctly on the batch endpoint, adds integration tests to verify the token savings (~318KB → ~1KB for 2 docs), and improves the OpenAPI documentation to make the brief option more discoverable.

**Investigation result:** Reading the source code reveals that `BatchDocumentRequest` already has a `brief` field (line 32) and `DocumentResource.getDocumentsBatch()` already passes it to the service (line 215). The `fields` query parameter is also already present (line 206). The main work is:
1. Verify brief mode works correctly on batch (write tests)
2. Ensure `fields` works on batch responses (already wired via `FieldSelectionFilter`)
3. Improve OpenAPI documentation
4. Add response size validation tests

## User Story

As an **AI agent consuming the API through an MCP server**, I want to request multiple documents in brief mode via the batch endpoint so that I can retrieve metadata for several documents in a single request without the ~318KB payload overhead of full sections and codeBlocks, keeping my context window efficient.

## Motivation

### Current Behavior

`POST /api/documents/batch` with full content (brief omitted or false):

```json
// Request
{
    "paths": ["security-overview.adoc", "rest.adoc"],
    "version": "main"
}

// Response: ~318 KB
{
    "documents": [
        {
            "title": "Security Overview",
            "description": "...",
            "path": "security-overview.adoc",
            "subject": "security",
            "extension": "quarkus-core",
            "sections": [ /* 20+ sections, ~150KB */ ],
            "codeBlocks": [ /* 15+ code blocks, ~50KB */ ],
            "matchedKeywords": [],
            "score": null
        },
        {
            "title": "Writing REST Services",
            "description": "...",
            "path": "rest.adoc",
            "subject": "rest-apis",
            "extension": "quarkus-resteasy-reactive",
            "sections": [ /* 25+ sections, ~100KB */ ],
            "codeBlocks": [ /* 10+ code blocks, ~30KB */ ],
            "matchedKeywords": [],
            "score": null
        }
    ],
    "errors": [],
    "requestedCount": 2,
    "retrievedCount": 2,
    "errorCount": 0
}
```

### Desired Behavior

`POST /api/documents/batch` with `brief=true` in the request body:

```json
// Request
{
    "paths": ["security-overview.adoc", "rest.adoc"],
    "version": "main",
    "brief": true
}

// Response: ~1 KB
{
    "documents": [
        {
            "title": "Security Overview",
            "description": "Quarkus provides comprehensive security features...",
            "path": "security-overview.adoc",
            "subject": "security",
            "extension": "quarkus-core",
            "sections": null,
            "codeBlocks": null,
            "matchedKeywords": [],
            "score": null
        },
        {
            "title": "Writing REST Services",
            "description": "How to write REST services with Quarkus...",
            "path": "rest.adoc",
            "subject": "rest-apis",
            "extension": "quarkus-resteasy-reactive",
            "sections": null,
            "codeBlocks": null,
            "matchedKeywords": [],
            "score": null
        }
    ],
    "errors": [],
    "requestedCount": 2,
    "retrievedCount": 2,
    "errorCount": 0
}
```

Additionally, combining `brief=true` with `fields`:

```
POST /api/documents/batch?fields=documents,retrievedCount
```

Returns only the `documents` and `retrievedCount` fields on the `BatchDocumentResponse` wrapper. The `documents` array items are `DocumentResponse` objects, which are filtered by the `@JsonFilter("fieldSelector")` already on `DocumentResponse`. However, nested field selection is out of scope — `fields` on the batch endpoint applies to `BatchDocumentResponse` top-level fields, not to fields within each `DocumentResponse` item.

### Token Savings

| Scenario | Payload Size | Approx. Tokens |
|----------|-------------|----------------|
| 2 docs, `brief=false` (full content) | ~318 KB | ~80,000 tokens |
| 2 docs, `brief=true` | ~1 KB | ~250 tokens |
| 2 docs, `brief=true` + `fields=documents` | ~0.8 KB | ~200 tokens |
| 10 docs, `brief=false` | ~1.5 MB | ~400,000 tokens |
| 10 docs, `brief=true` | ~5 KB | ~1,250 tokens |

---

## Scope / Requirements

### R1: Verify Existing `brief` Handling in Batch

**Files:** `src/main/java/com/fvd/api/dto/BatchDocumentRequest.java`, `src/main/java/com/fvd/api/resources/DocumentResource.java`, `src/main/java/com/fvd/api/services/DocumentService.java`

The `brief` field already exists and is wired:

- `BatchDocumentRequest.brief` (line 32) — `Boolean` field with `@Schema(defaultValue = "false")`
- `DocumentResource.getDocumentsBatch()` (line 215) — `boolean brief = Boolean.TRUE.equals(request.brief);`
- `DocumentService.getDocumentsBatch()` (line 98) — accepts `boolean brief`, calls `getDocumentByPathBrief()` when true

**Action:** No code changes needed. Write integration tests to verify the end-to-end behavior.

### R2: Verify `fields` Works on Batch Response

**File:** `src/main/java/com/fvd/api/dto/BatchDocumentResponse.java`

`BatchDocumentResponse` already has `@JsonFilter("fieldSelector")` (line 14). The `FieldSelectionFilter` (Feature 74) will apply field selection to the top-level `BatchDocumentResponse` fields when `fields` query parameter is provided.

**Available fields for selection:** `documents`, `errors`, `requestedCount`, `retrievedCount`, `errorCount`.

**Action:** Write integration tests to verify `fields` works correctly on the batch endpoint.

### R3: Improve OpenAPI Documentation

**File:** `src/main/java/com/fvd/api/resources/DocumentResource.java`

Update the `@Operation` description on `getDocumentsBatch()` to explicitly mention the `brief` option and its token savings:

```java
@Operation(
        summary = "Retrieve multiple documents by path in a single request",
        description = "Accepts a JSON body with a list of document paths and returns each document's " +
                "full structured content (or brief metadata if brief=true in the request body). " +
                "Setting brief=true reduces response size dramatically (e.g., from ~318KB to ~1KB for 2 docs) " +
                "by omitting sections and codeBlocks. Partial failures are " +
                "reported per-path in the 'errors' array — the request succeeds (200) as long as " +
                "at least one document is found. Returns 400 if the request body is invalid " +
                "(empty paths, too many paths, or malformed input). Returns 404 only if ALL " +
                "requested documents are not found.\n\n" +
                "Combine with the 'fields' query parameter to further reduce response size by " +
                "selecting only specific top-level fields (e.g., fields=documents,retrievedCount)."
)
```

### R4: Improve `BatchDocumentRequest.brief` Schema Documentation

**File:** `src/main/java/com/fvd/api/dto/BatchDocumentRequest.java`

Enhance the `@Schema` description on the `brief` field:

```java
@Schema(description = "When true, returns only metadata (title, description, path, subject, " +
        "extension) without full sections and codeBlocks. Reduces response size from ~150KB " +
        "per document to ~500 bytes per document. Recommended for discovery workflows " +
        "before fetching full documents by path.",
        defaultValue = "false")
public Boolean brief;
```

---

## Technical Design

### Existing Implementation Flow

The batch endpoint already handles `brief` correctly through the following call chain:

1. `DocumentResource.getDocumentsBatch(request, fields)` — resolves `brief` from `request.brief`
2. `DocumentService.getDocumentsBatch(version, paths, brief)` — iterates paths
3. For each path:
   - If `brief=true`: calls `getDocumentByPathBrief()` — reads file, extracts title/description only
   - If `brief=false`: calls `getDocumentByPath()` — reads file, parses sections and code blocks
4. Returns `BatchDocumentResponse` with documents and errors

The `fields` query parameter is handled separately by `FieldSelectionFilter` (ContainerResponseFilter), which applies to the serialized `BatchDocumentResponse`. Since `BatchDocumentResponse` has `@JsonFilter("fieldSelector")`, the filter applies to its top-level fields.

### No Code Changes Required

After review, the implementation is already complete. The feature work is primarily:
1. **Documentation** — improve OpenAPI descriptions to make `brief` more discoverable
2. **Testing** — verify end-to-end behavior with integration tests
3. **Validation** — confirm `fields` works on the batch endpoint response

### Edge Cases

- **`brief=null`** (omitted from request body): Treated as `false` via `Boolean.TRUE.equals(request.brief)`. This is correct for backward compatibility.
- **`brief=true` with `fields`**: Both apply. `brief=true` omits sections/codeBlocks from the `DocumentResponse` objects. `fields` on the query param filters top-level `BatchDocumentResponse` fields. These are independent mechanisms.
- **`brief=true` with one missing document**: Brief mode still correctly handles partial failures — the error is added to `errors` array, and the found document is returned in brief format.

---

## Request/Response Examples

### Example 1: Batch with brief=true

**Request:**
```
POST /api/documents/batch
Content-Type: application/json

{
    "paths": ["security-overview.adoc", "rest.adoc"],
    "version": "main",
    "brief": true
}
```

**Response (200):**
```json
{
    "documents": [
        {
            "title": "Security Overview",
            "description": "Quarkus provides comprehensive security features...",
            "path": "security-overview.adoc",
            "subject": "security",
            "extension": "quarkus-core",
            "sections": null,
            "codeBlocks": null,
            "matchedKeywords": [],
            "score": null
        },
        {
            "title": "Writing REST Services",
            "description": "How to write REST services with Quarkus...",
            "path": "rest.adoc",
            "subject": "rest-apis",
            "extension": "quarkus-resteasy-reactive",
            "sections": null,
            "codeBlocks": null,
            "matchedKeywords": [],
            "score": null
        }
    ],
    "errors": [],
    "requestedCount": 2,
    "retrievedCount": 2,
    "errorCount": 0
}
```

### Example 2: Batch with brief=true and fields

**Request:**
```
POST /api/documents/batch?fields=documents,retrievedCount
Content-Type: application/json

{
    "paths": ["security-overview.adoc"],
    "version": "main",
    "brief": true
}
```

**Response (200):**
```json
{
    "documents": [
        {
            "title": "Security Overview",
            "description": "Quarkus provides comprehensive security features...",
            "path": "security-overview.adoc",
            "subject": "security",
            "extension": "quarkus-core",
            "sections": null,
            "codeBlocks": null,
            "matchedKeywords": [],
            "score": null
        }
    ],
    "retrievedCount": 1
}
```

### Example 3: Batch with brief=false (default, full content)

**Request:**
```
POST /api/documents/batch
Content-Type: application/json

{
    "paths": ["security-overview.adoc"],
    "version": "main"
}
```

**Response (200):** Full document with sections and codeBlocks — same as current behavior.

### Example 4: Batch with brief=true and partial failure

**Request:**
```
POST /api/documents/batch
Content-Type: application/json

{
    "paths": ["security-overview.adoc", "nonexistent.adoc"],
    "version": "main",
    "brief": true
}
```

**Response (200):**
```json
{
    "documents": [
        {
            "title": "Security Overview",
            "description": "...",
            "path": "security-overview.adoc",
            "subject": "security",
            "extension": "quarkus-core",
            "sections": null,
            "codeBlocks": null,
            "matchedKeywords": [],
            "score": null
        }
    ],
    "errors": [
        {
            "path": "nonexistent.adoc",
            "error": "Document not found"
        }
    ],
    "requestedCount": 2,
    "retrievedCount": 1,
    "errorCount": 1
}
```

---

## Implementation Notes

### Token Savings Justification

The ~318KB → ~1KB reduction for 2 documents is based on actual testing. Quarkus documentation files are large — `security-overview.adoc` alone can generate 20+ sections and 15+ code blocks. When serialized to JSON with full content, a single document can be 100-200KB. In brief mode, each document is reduced to ~500 bytes (title, description, path, subject, extension).

For AI agents consuming via MCP, the context window is the critical constraint. A 318KB response consumes ~80,000 tokens, which may exceed the agent's entire context window. Brief mode makes batch retrieval practical.

### `fields` Applies to `BatchDocumentResponse`, Not `DocumentResponse`

The `fields` query parameter on the batch endpoint filters fields on the `BatchDocumentResponse` wrapper (which has `@JsonFilter("fieldSelector")`). The `DocumentResponse` items within the `documents` array also have `@JsonFilter("fieldSelector")`, but the `FieldSelectionFilter` only applies to the root entity. Jackson will serialize nested objects with their own filter if the filter name matches — this means `fields=documents` will include the full `documents` array, but the individual `DocumentResponse` items will include all their fields.

To filter fields within each `DocumentResponse` item, the agent would need to use the single-document endpoint (`GET /api/documents?path=...&fields=title,path`) or combine `brief=true` to omit sections/codeBlocks.

### No Need for `brief` Query Parameter on Batch

The batch endpoint uses a POST body for its request. The `brief` parameter is naturally part of the request body alongside `paths` and `version`. Adding a duplicate `brief` query parameter would be redundant and create ambiguity about which takes precedence.

---

## Tasks

- [ ] Improve `@Operation` description on `getDocumentsBatch()` to mention `brief` and token savings
- [ ] Enhance `@Schema` description on `BatchDocumentRequest.brief` with size reduction guidance
- [ ] Add integration test: batch with `brief=true` returns documents without sections/codeBlocks
- [ ] Add integration test: batch with `brief=false` (default) returns documents with sections/codeBlocks
- [ ] Add integration test: batch with `brief=true` and partial failure handles errors correctly
- [ ] Add integration test: batch with `fields=documents,retrievedCount` returns only selected fields
- [ ] Add integration test: batch with `brief=true` response size is significantly smaller than `brief=false`
- [ ] Add integration test: batch with `brief=true` and invalid fields returns 400 with available fields
- [ ] Verify existing batch tests still pass
- [ ] Run `./gradlew test` — all tests pass

---

## Acceptance Criteria

1. `POST /api/documents/batch` with `{"paths": [...], "brief": true}` returns documents with `null` sections and codeBlocks
2. `POST /api/documents/batch` with `{"paths": [...]}` (no `brief`) returns documents with full sections and codeBlocks (backward compatible)
3. `POST /api/documents/batch` with `brief=true` and a missing path returns the found documents in brief mode and the missing path in `errors`
4. `POST /api/documents/batch?fields=documents,retrievedCount` returns only `documents` and `retrievedCount` fields
5. `POST /api/documents/batch?fields=invalid` returns 400 with available field names
6. Brief batch response size is at least 100x smaller than full batch response for typical documents
7. OpenAPI documentation for batch endpoint mentions brief mode and its token savings
8. All existing batch tests pass unchanged
9. `./gradlew test` passes with zero failures

---

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| `fields` on batch endpoint filters `BatchDocumentResponse` but not nested `DocumentResponse` items | Medium (confusion) | Low | Document clearly that `fields` applies to top-level batch fields; nested document field selection requires single-document endpoint |
| `brief=true` combined with Feature 81 (`@JsonInclude(NON_NULL)`) will omit `sections` and `codeBlocks` entirely instead of showing `null` | Medium | Low (desired) | This is actually the desired behavior — Feature 81 complements this feature by reducing payload further |
| OpenAPI schema may not clearly show `brief` as an option in the request body | Low | Low | Enhance `@Schema` annotation on `BatchDocumentRequest.brief` with clear description and example |
| Tests may be flaky if document content varies across cache versions | Low | Medium | Use fixed test data or mock `DocStore` to ensure consistent document content in tests |

---

## Estimated Effort

| Task | Hours |
|------|-------|
| Improve OpenAPI documentation (operation + schema) | 0.5 |
| Integration tests for brief mode (4 test methods) | 1.5 |
| Integration tests for fields on batch (2 test methods) | 0.5 |
| Integration test for response size comparison | 0.5 |
| Run full test suite and verify | 0.5 |
| **Total** | **~3.5 hours** |

---

## Files Modified

### Modified Production Files (2 files)
- `src/main/java/com/fvd/api/resources/DocumentResource.java` — improve `@Operation` description on `getDocumentsBatch()`
- `src/main/java/com/fvd/api/dto/BatchDocumentRequest.java` — enhance `@Schema` description on `brief` field

### New Test Files (1 file)
- `src/test/java/com/fvd/api/resources/BatchDocumentBriefTest.java` — integration tests for brief mode, fields, and response size on batch endpoint

### Unchanged Files
- `src/main/java/com/fvd/api/dto/BatchDocumentResponse.java` — already has `@JsonFilter("fieldSelector")`
- `src/main/java/com/fvd/api/services/DocumentService.java` — already handles `brief` in `getDocumentsBatch()`
- `src/main/java/com/fvd/common/filters/FieldSelectionFilter.java` — already handles batch responses

---

## Dependencies

- **Feature 74 (Response Field Selection)** — already implemented. The `fields` query parameter works on the batch endpoint via `FieldSelectionFilter` and `@JsonFilter("fieldSelector")` on `BatchDocumentResponse`.
- **Feature 81 (Omit Null Fields)** — optional complement. When implemented, `sections: null` and `codeBlocks: null` in brief mode will be omitted entirely from the JSON response.

---

END OF FILE
