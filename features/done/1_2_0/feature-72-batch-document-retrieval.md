# Feature 72: Batch Document Retrieval

> **Dependencies**: None required, but Feature 69 (Cache Document Parsing Results) dramatically improves batch performance. Without caching, each document in the batch is parsed from scratch (3-7s per doc). With Feature 69's in-memory cache, cached documents return in <50ms. Recommended implementation order: Feature 69 first, then Feature 72. Compatible with Feature 67 (Lightweight Document Search / `brief` mode).

## Summary

AI agents consuming this API through an MCP server frequently discover 3–5 relevant documents via keyword search and then need to retrieve them all. Today this requires N sequential `GET /api/documents?path=...` requests, each taking 3–7 seconds due to file I/O and AsciiDoc parsing. A batch endpoint allows fetching multiple documents in a single HTTP request, reducing round-trips from N to 1 and enabling the service to process documents concurrently.

## User Story

As an **AI agent that has just performed a keyword search**, I want to retrieve multiple documents by path in a single request so that I can reduce latency from N sequential round-trips to a single call, obtaining all the context I need in one step.

## Motivation

### Current Workflow (Slow)

```
1. GET /api/documents?keywords=security+oidc&brief=true     → 200ms, returns 5 paths
2. GET /api/documents?path=security-overview.adoc            → 4s
3. GET /api/documents?path=security-oidc-code-flow.adoc      → 5s
4. GET /api/documents?path=security-jwt.adoc                 → 3s
5. GET /api/documents?path=security-keycloak.adoc             → 6s
Total: ~18 seconds sequential
```

### Desired Workflow (Fast)

```
1. GET /api/documents?keywords=security+oidc&brief=true      → 200ms, returns 5 paths
2. POST /api/documents/batch                                  → 7s (parallel), returns all 4 docs
Total: ~7 seconds
```

### Why POST Instead of GET?

| Option | Pros | Cons |
|--------|------|------|
| `GET` with repeated `?paths=a&paths=b` | Cacheable, idempotent semantics clear | URL length limits (~2000 chars); 10 AsciiDoc paths can exceed 500 chars; no room for future request options |
| `POST` with JSON body | No URL length limits; clean request structure; extensible for future fields (e.g., per-doc `brief` flags) | Not cacheable by HTTP intermediaries; uncommon for read operations |

**Decision: `POST /api/documents/batch`** — The request body carries a list of paths and query parameters. URL length limits are a real constraint for document paths like `_guides/security-oidc-code-flow-authentication.adoc`. POST also aligns with "batch operation" semantics used by Google, GitHub, and Microsoft Graph APIs.

---

## Requirements

### R1: New Endpoint — `POST /api/documents/batch`

**File:** `src/main/java/com/fvd/api/resources/DocumentResource.java`

Add a new `POST` method to the existing `DocumentResource`:

```java
@POST
@Path("/batch")
@Consumes(MediaType.APPLICATION_JSON)
@Operation(
        summary = "Retrieve multiple documents by path in a single request",
        description = "Accepts a JSON body with a list of document paths and returns each document's " +
                "full structured content (or brief metadata if brief=true). Partial failures are " +
                "reported per-path in the 'errors' array — the request succeeds (200) as long as " +
                "at least one document is found. Returns 400 if the request body is invalid " +
                "(empty paths, too many paths, or malformed input). Returns 404 only if ALL " +
                "requested documents are not found."
)
@APIResponse(responseCode = "200", description = "Batch results returned (may include partial errors)",
        content = @Content(schema = @Schema(implementation = BatchDocumentResponse.class)))
@APIResponse(responseCode = "400", description = "Invalid request (empty paths, exceeds max batch size, invalid path format)",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
@APIResponse(responseCode = "404", description = "All requested documents not found",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
public BatchDocumentResponse getDocumentsBatch(BatchDocumentRequest request) {
    // ... implementation
}
```

### R2: Request DTO — `BatchDocumentRequest`

**New file:** `src/main/java/com/fvd/api/dto/BatchDocumentRequest.java`

```java
package com.fvd.api.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

/**
 * Request body for batch document retrieval.
 */
@RegisterForReflection
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request body for batch document retrieval")
public class BatchDocumentRequest {

    @Schema(description = "List of document paths to retrieve (max 10)",
            required = true,
            example = "[\"security-overview.adoc\", \"security-oidc-code-flow.adoc\"]")
    public List<String> paths;

    @Schema(description = "Quarkus version branch or tag. Defaults to 'main' if omitted.",
            defaultValue = "main",
            example = "main")
    public String version;

    @Schema(description = "When true, returns only metadata (title, description, path, subject, " +
            "extension) without full sections and codeBlocks.",
            defaultValue = "false")
    public Boolean brief;
}
```

### R3: Response DTO — `BatchDocumentResponse`

**New file:** `src/main/java/com/fvd/api/dto/BatchDocumentResponse.java`

```java
package com.fvd.api.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

/**
 * Response for batch document retrieval with partial failure support.
 */
@RegisterForReflection
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Batch document retrieval response")
public class BatchDocumentResponse {

    @Schema(description = "Successfully retrieved documents")
    public List<DocumentResponse> documents;

    @Schema(description = "Errors for paths that could not be retrieved")
    public List<BatchDocumentError> errors;

    @Schema(description = "Total number of paths requested")
    public int requestedCount;

    @Schema(description = "Number of documents successfully retrieved")
    public int retrievedCount;

    @Schema(description = "Number of paths that failed")
    public int errorCount;
}
```

### R4: Error DTO — `BatchDocumentError`

**New file:** `src/main/java/com/fvd/api/dto/BatchDocumentError.java`

```java
package com.fvd.api.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Error detail for a single document in a batch request.
 */
@RegisterForReflection
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Error detail for a single path in a batch request")
public class BatchDocumentError {

    @Schema(description = "The document path that failed", example = "nonexistent.adoc")
    public String path;

    @Schema(description = "Error reason", example = "Document not found")
    public String reason;
}
```

### R5: Service Method — `DocumentService.getDocumentsBatch()`

**File:** `src/main/java/com/fvd/api/services/DocumentService.java`

Add a new method that iterates over paths, calls `getDocumentByPath()` for each, and collects successes and failures:

```java
/**
 * Retrieves multiple documents by path. Handles partial failures gracefully.
 *
 * @param version the documentation version
 * @param paths list of document paths to retrieve
 * @param brief when true, returns only title and description without sections/code blocks
 * @return batch response with documents and errors
 */
public BatchDocumentResponse getDocumentsBatch(String version, List<String> paths, boolean brief) {
    List<DocumentResponse> documents = new ArrayList<>();
    List<BatchDocumentError> errors = new ArrayList<>();

    for (String path : paths) {
        try {
            DocumentResponse doc = brief
                    ? getDocumentByPathBrief(version, path)
                    : getDocumentByPath(version, path);
            if (doc != null) {
                documents.add(doc);
            } else {
                errors.add(new BatchDocumentError(path, "Document not found"));
            }
        } catch (Exception e) {
            log.warn("Error retrieving document '{}': {}", path, e.getMessage());
            errors.add(new BatchDocumentError(path, "Error reading document: " + e.getMessage()));
        }
    }

    return new BatchDocumentResponse(documents, errors, paths.size(), documents.size(), errors.size());
}
```

### R6: Brief Mode Support for Single Document Path Lookup

**File:** `src/main/java/com/fvd/api/services/DocumentService.java`

Add a new private method `getDocumentByPathBrief()` that reads the document but skips section and code block parsing (same optimization as Feature 67's search brief mode, applied to path-based retrieval):

```java
/**
 * Retrieves a document by path with only title, description, subject, and extension.
 * Skips section and code block parsing for performance.
 */
private DocumentResponse getDocumentByPathBrief(String version, String path) {
    Optional<String> contentOpt = docStore.read(version, path);
    if (contentOpt.isEmpty()) {
        return null;
    }

    String content = contentOpt.get();
    String title = DocumentTitleExtractor.extractTitle(content);
    String description = extractDescription(content);
    String extension = findExtensionForPath(version, path);
    String subject = subjectDeriver.deriveSubject(path);

    return new DocumentResponse(title, description, path, subject, extension,
            null, null, List.of(), null);
}
```

### R7: Input Validation

**File:** `src/main/java/com/fvd/api/resources/DocumentResource.java` (endpoint level)
**File:** `src/main/java/com/fvd/common/validators/InputValidator.java` (new validation method)

Validation rules applied in the endpoint method, before calling the service:

| Validation | Error | Status |
|-----------|-------|--------|
| `request` is null | `"Request body is required"` | 400 |
| `request.paths` is null or empty | `"paths must not be empty"` | 400 |
| `request.paths` size > `app.batch.max-size` (default 10) | `"paths must not exceed {max} entries"` | 400 |
| Any path contains `..` | `"paths must not contain '..' (path: {path})"` | 400 |
| Any path is blank | `"paths contains an empty entry"` | 400 |
| Duplicate paths | Silently deduplicated (not an error) | — |
| Version invalid | Existing `InvalidInputException` | 400 |

Add a new validation method to `InputValidator`:

```java
public static List<String> validateBatchPaths(List<String> paths, int maxBatchSize) {
    if (paths == null || paths.isEmpty()) {
        throw new InvalidInputException("paths must not be empty");
    }
    if (paths.size() > maxBatchSize) {
        throw new InvalidInputException("paths must not exceed " + maxBatchSize + " entries");
    }
    List<String> deduplicated = paths.stream()
            .distinct()
            .toList();
    for (String path : deduplicated) {
        validatePath(path);
    }
    return deduplicated;
}
```

### R8: Configuration — Max Batch Size

**File:** `src/main/resources/application.properties`

```properties
# Batch document retrieval
app.batch.max-size=10
```

Inject via `@ConfigProperty`:

```java
@ConfigProperty(name = "app.batch.max-size", defaultValue = "10")
int maxBatchSize;
```

### R9: HTTP Status Code Strategy

The endpoint uses a **partial success model**:

| Scenario | HTTP Status | Response Body |
|----------|-------------|---------------|
| All documents found | `200 OK` | `documents=[...], errors=[], retrievedCount=N, errorCount=0` |
| Some found, some not | `200 OK` | `documents=[...], errors=[...], retrievedCount=M, errorCount=K` |
| None found (all paths invalid/missing) | `404 Not Found` | RFC 7807 `ProblemDetail` with `detail="None of the requested documents were found"` |
| Invalid request (empty paths, over limit) | `400 Bad Request` | RFC 7807 `ProblemDetail` |

**Rationale:** Returning 200 with partial errors follows the convention of batch APIs (Google Batch, GitHub GraphQL). The client inspects `errors` to detect failures. Returning 404 only when ALL documents are missing signals a complete failure distinct from partial success.

### R10: Endpoint Implementation

**File:** `src/main/java/com/fvd/api/resources/DocumentResource.java`

```java
@POST
@Path("/batch")
@Consumes(MediaType.APPLICATION_JSON)
public BatchDocumentResponse getDocumentsBatch(BatchDocumentRequest request) {
    if (request == null) {
        throw new InvalidInputException("Request body is required");
    }

    String resolvedVersion = InputValidator.resolveVersion(request.version);
    InputValidator.validateVersionExists(resolvedVersion, cacheService.listCachedVersions());
    List<String> validatedPaths = InputValidator.validateBatchPaths(request.paths, maxBatchSize);

    boolean brief = Boolean.TRUE.equals(request.brief);
    BatchDocumentResponse response = documentService.getDocumentsBatch(resolvedVersion, validatedPaths, brief);

    if (response.retrievedCount == 0 && response.errorCount > 0) {
        throw new DocNotFoundException("None of the requested documents were found");
    }

    return response;
}
```

---

## API Design

### Request

```
POST /api/documents/batch
Content-Type: application/json
```

```json
{
    "paths": [
        "security-overview.adoc",
        "security-oidc-code-flow.adoc",
        "nonexistent.adoc"
    ],
    "version": "3.27",
    "brief": false
}
```

### Response — Partial Success (200)

```json
{
    "documents": [
        {
            "title": "Security Overview",
            "description": "Introduction to Quarkus security features.",
            "path": "security-overview.adoc",
            "subject": "security",
            "extension": "quarkus-core",
            "sections": [ ... ],
            "codeBlocks": [ ... ],
            "matchedKeywords": [],
            "score": null
        },
        {
            "title": "OIDC Code Flow Authentication",
            "description": "How to use OIDC code flow with Quarkus.",
            "path": "security-oidc-code-flow.adoc",
            "subject": "security",
            "extension": "quarkus-oidc",
            "sections": [ ... ],
            "codeBlocks": [ ... ],
            "matchedKeywords": [],
            "score": null
        }
    ],
    "errors": [
        {
            "path": "nonexistent.adoc",
            "reason": "Document not found"
        }
    ],
    "requestedCount": 3,
    "retrievedCount": 2,
    "errorCount": 1
}
```

### Response — All Not Found (404)

```json
{
    "type": "about:blank",
    "title": "Not Found",
    "status": 404,
    "detail": "None of the requested documents were found",
    "instance": "/api/documents/batch",
    "timestamp": "2026-02-15T10:30:00Z"
}
```

### Response — Invalid Request (400)

```json
{
    "type": "about:blank",
    "title": "Bad Request",
    "status": 400,
    "detail": "paths must not exceed 10 entries",
    "instance": "/api/documents/batch",
    "timestamp": "2026-02-15T10:30:00Z"
}
```

---

## Implementation Notes

### Ordering

Documents in the `documents` array are returned in the same order as the requested `paths`. This is predictable for clients mapping results back to their requests.

### Deduplication

If a client sends duplicate paths (e.g., `["security.adoc", "security.adoc"]`), they are silently deduplicated before processing. The `requestedCount` reflects the deduplicated count.

### Brief Mode in Batch

When `brief=true`, each document is returned without `sections` and `codeBlocks` (same as Feature 67's brief mode). This is useful when the agent needs title, description, and subject for multiple documents but does not yet need full content.

### `matchedKeywords` and `score`

Since batch retrieval is path-based (not search-based), `matchedKeywords` is an empty list and `score` is `null` for each document. This is consistent with the existing single-document path mode behavior.

### `@Consumes` Annotation

The batch endpoint adds `@Consumes(MediaType.APPLICATION_JSON)` at the method level. The class-level `@Produces(MediaType.APPLICATION_JSON)` already applies. This is the only `POST` endpoint on `DocumentResource`.

### No `@Tag` Change

The existing `@Tag(name = "Documents")` on the class applies to all methods, including the new batch endpoint. No additional tag is needed.

---

## Tasks

- [ ] Create `BatchDocumentRequest` DTO in `com.fvd.api.dto` with `paths`, `version`, `brief` fields, `@Schema` annotations, and Lombok annotations
- [ ] Create `BatchDocumentError` DTO in `com.fvd.api.dto` with `path` and `reason` fields, `@Schema` annotations, and Lombok annotations
- [ ] Create `BatchDocumentResponse` DTO in `com.fvd.api.dto` with `documents`, `errors`, `requestedCount`, `retrievedCount`, `errorCount` fields, `@Schema` annotations, and Lombok annotations
- [ ] Add `validateBatchPaths(List<String> paths, int maxBatchSize)` method to `InputValidator` that deduplicates, validates each path, and enforces the max size limit
- [ ] Add `app.batch.max-size=10` configuration property to `application.properties`
- [ ] Add `getDocumentByPathBrief()` private method to `DocumentService` that reads a document but skips section/code-block parsing
- [ ] Add `getDocumentsBatch()` method to `DocumentService` that iterates paths, collects successes into `documents` and failures into `errors`, and returns `BatchDocumentResponse`
- [ ] Add `POST /batch` endpoint to `DocumentResource` with `@Consumes(APPLICATION_JSON)`, OpenAPI annotations, request body validation, version validation, and delegation to `DocumentService.getDocumentsBatch()`
- [ ] Inject `@ConfigProperty(name = "app.batch.max-size", defaultValue = "10") int maxBatchSize` in `DocumentResource`
- [ ] Throw `DocNotFoundException` when all documents fail (returns 404 via existing mapper)
- [ ] Add unit tests for `InputValidator.validateBatchPaths()`:
    - Null list throws `InvalidInputException`
    - Empty list throws `InvalidInputException`
    - List exceeding max size throws `InvalidInputException`
    - Path containing `..` throws `InvalidInputException`
    - Blank path throws `InvalidInputException`
    - Duplicate paths are deduplicated
    - Valid paths pass through
- [ ] Add unit tests for `DocumentService.getDocumentsBatch()`:
    - All documents found → all in `documents`, empty `errors`
    - Some documents found, some missing → partial `documents` and `errors`
    - No documents found → empty `documents`, all in `errors`
    - Brief mode returns documents without sections/codeBlocks
    - Full mode returns documents with sections and codeBlocks
- [ ] Add integration tests for `POST /api/documents/batch`:
    - Valid request with all docs found → 200, `retrievedCount` matches
    - Valid request with partial failures → 200, `errors` array populated
    - All docs not found → 404 with ProblemDetail
    - Empty paths list → 400
    - Null request body → 400
    - Paths exceeding max batch size → 400
    - Path with `..` → 400
    - Unknown version → 400
    - Brief mode → 200, documents have null sections/codeBlocks
    - Duplicate paths → 200, deduplicated count
- [ ] Run `./gradlew test` — all tests pass

---

## Acceptance Criteria

1. `POST /api/documents/batch` with a valid JSON body containing 1–10 paths returns 200 with `documents` and `errors` arrays
2. Successfully retrieved documents include `title`, `description`, `path`, `subject`, `extension`, `sections`, `codeBlocks` (or null sections/codeBlocks if `brief=true`)
3. Documents that cannot be found appear in the `errors` array with `path` and `reason` fields
4. `requestedCount`, `retrievedCount`, and `errorCount` are accurate and consistent (`requestedCount = retrievedCount + errorCount`)
5. If ALL requested documents are not found, the endpoint returns 404 with an RFC 7807 `ProblemDetail`
6. If `paths` is empty, null, or exceeds `app.batch.max-size`, the endpoint returns 400 with an RFC 7807 `ProblemDetail`
7. Paths containing `..` or blank entries are rejected with 400
8. Duplicate paths are silently deduplicated before processing
9. `version` defaults to `"main"` when omitted from the request body
10. `brief=true` returns documents without sections and codeBlocks (same as Feature 67 behavior)
11. The `app.batch.max-size` config property controls the maximum number of paths (default 10)
12. OpenAPI annotations fully document the endpoint, request body, and response schemas
13. All existing tests continue to pass (no regressions on `GET /api/documents`)
14. `./gradlew test` passes with zero failures

---

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Large batch requests with full mode cause high memory usage (10 full documents can be ~6MB) | Medium | Medium | Default max batch size of 10 is conservative; recommend `brief=true` for discovery in docs; consider streaming in future |
| POST for a read operation surprises API consumers expecting GET-only | Low | Low | Document clearly in OpenAPI description; POST is standard for batch operations in major APIs |
| Concurrent `docStore.read()` calls could create I/O contention | Low | Low | Sequential processing within a single request is acceptable; files are cached in OS page cache after first access; parallel execution is a future optimization |
| `@Consumes(APPLICATION_JSON)` on a single method while class has only `@Produces` may confuse OpenAPI generators | Low | Low | Method-level `@Consumes` is standard JAX-RS; test that OpenAPI spec renders correctly |
| Adding `@ConfigProperty` to a `@RequiredArgsConstructor` class requires adjusting constructor injection | Medium | Low | Use field injection with `@ConfigProperty` for the single config value (consistent with Quarkus `@ConfigProperty` pattern) or add the config to the constructor |
| Clients send very long paths that consume memory before validation | Low | Low | Jackson deserialization limits apply; `validateBatchPaths()` rejects immediately if count exceeds max |
| Response order not guaranteed if processing is parallelized later | Low | Medium | Document that order matches request order; sequential processing ensures this initially |

---

## Estimated Effort

| Task | Hours |
|------|-------|
| Create DTOs (`BatchDocumentRequest`, `BatchDocumentResponse`, `BatchDocumentError`) | 1.0 |
| Add `validateBatchPaths()` to `InputValidator` | 0.5 |
| Add `app.batch.max-size` configuration | 0.25 |
| Add `getDocumentByPathBrief()` to `DocumentService` | 0.5 |
| Add `getDocumentsBatch()` to `DocumentService` | 1.0 |
| Add `POST /batch` endpoint to `DocumentResource` with OpenAPI annotations | 1.0 |
| Unit tests for `InputValidator.validateBatchPaths()` | 0.75 |
| Unit tests for `DocumentService.getDocumentsBatch()` | 1.5 |
| Integration tests for `POST /api/documents/batch` | 2.0 |
| Run full test suite and fix regressions | 0.5 |
| **Total** | **~9 hours** |

---

## Files Affected

### New Production Files (3 files)
- `src/main/java/com/fvd/api/dto/BatchDocumentRequest.java` — request DTO
- `src/main/java/com/fvd/api/dto/BatchDocumentResponse.java` — response DTO with partial failure support
- `src/main/java/com/fvd/api/dto/BatchDocumentError.java` — per-path error DTO

### Modified Production Files (4 files)
- `src/main/java/com/fvd/api/resources/DocumentResource.java` — add `POST /batch` endpoint with OpenAPI annotations and `maxBatchSize` config injection
- `src/main/java/com/fvd/api/services/DocumentService.java` — add `getDocumentsBatch()` and `getDocumentByPathBrief()` methods
- `src/main/java/com/fvd/common/validators/InputValidator.java` — add `validateBatchPaths()` method
- `src/main/resources/application.properties` — add `app.batch.max-size=10`

### Unchanged Production Files
- `src/main/java/com/fvd/api/dto/DocumentResponse.java` — reused as-is for each document in the batch
- `src/main/java/com/fvd/common/resources/ProblemDetail.java` — reused for error responses
- `src/main/java/com/fvd/common/exceptions/InvalidInputException.java` — reused for validation errors
- `src/main/java/com/fvd/docs/exceptions/DocNotFoundException.java` — reused for all-not-found case

### New Test Files (estimated 2 files)
- `src/test/java/com/fvd/common/validators/InputValidatorBatchTest.java` — unit tests for `validateBatchPaths()`
- `src/test/java/com/fvd/api/services/DocumentServiceBatchTest.java` — unit tests for `getDocumentsBatch()`

### Modified Test Files (1 file)
- `src/test/java/com/fvd/api/resources/DocumentResourceTest.java` — add integration tests for `POST /api/documents/batch`

---

END OF FILE
