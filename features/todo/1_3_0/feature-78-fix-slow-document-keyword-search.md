# Feature 78: Fix Slow Document Keyword Search

> **Dependencies**: None. This is a self-contained performance bugfix. Interacts with existing `brief` parameter (Feature 67) and `fields` parameter (Feature 74) but does not require changes to either.

## Summary

The `GET /api/documents?keywords=...` endpoint times out (>30s) when `brief` is not explicitly set to `true`, because the `searchDocuments` method loads and parses full document content (sections + codeBlocks) for **all** matching documents **before** pagination is applied. A search for `keywords=rest` matches 50+ documents, each requiring file I/O, AsciiDoc parsing, section extraction, and code block extraction. This feature fixes the performance bug by (1) defaulting `brief` to `true` for keyword searches, (2) applying pagination before loading full content when `brief=false` is explicitly requested, and (3) capping `brief=false` requests at a lower maximum limit with a warning in the response when the result set is large.

## User Story

As an **AI agent consuming the API through an MCP server**, I want keyword searches on `/api/documents` to return results within a reasonable time (<5s) so that my MCP tool calls do not time out and I can discover relevant documents efficiently, even when many documents match my keywords.

## Motivation

### Current Behavior (Timeout)

`GET /api/documents?keywords=rest` (no `brief` parameter):

- The `SearchService.searchFiles()` returns a paginated list of `FileSearchResult` (fast — index lookup only)
- `DocumentService.searchDocuments()` iterates over **all** paginated results and calls `getOrParseDocument()` for each, which reads the file, parses sections, and extracts code blocks
- For broad keywords like `rest`, 20 matching documents are returned, each requiring full file parsing
- Each document parse involves: `docStore.read()` (file I/O), `docParser.parseSections()`, `docParser.parseCodeBlocks()`, and string manipulation
- Total time: **>30 seconds**, causing HTTP timeout

```
GET /api/documents?keywords=rest
→ 504 Gateway Timeout (or 30s+ wait)
```

### Current Behavior (Brief works)

`GET /api/documents?keywords=rest&brief=true`:

```json
{
    "results": [
        {
            "title": "Writing REST Services",
            "description": "How to write REST services with Quarkus...",
            "path": "rest.adoc",
            "subject": "rest-apis",
            "extension": "quarkus-resteasy-reactive",
            "sections": null,
            "codeBlocks": null,
            "matchedKeywords": ["rest"],
            "score": 15.2
        }
    ],
    "totalCount": 52,
    "returnedCount": 20
}
```

This completes in <15s because `brief=true` skips section and code block parsing — it only extracts title and description from the raw content.

### Desired Behavior

1. `GET /api/documents?keywords=rest` (no `brief`) defaults to `brief=true`, completes in <15s
2. `GET /api/documents?keywords=rest&brief=false` is explicitly allowed but capped at `limit=5` (max) and loads full content only for the paginated page
3. When `brief=false` returns a large result set, a `warning` field is included in the response

```
GET /api/documents?keywords=rest
→ 200 OK in <5s (brief=true by default, lightweight metadata)
```

```
GET /api/documents?keywords=rest&brief=false&limit=3
→ 200 OK in <10s (full content for 3 documents only)
```

---

## Scope / Requirements

### R1: Default `brief` to `true` for Keyword Searches

**File:** `src/main/java/com/fvd/api/resources/DocumentResource.java`

In the `getDocuments()` method, change the brief resolution logic for keyword search mode. Currently (line 170):

```java
return documentService.searchDocuments(params.version(), params.keywords(), params.subject(),
        params.extension(), params.limit(), params.offset(), Boolean.TRUE.equals(brief));
```

The expression `Boolean.TRUE.equals(brief)` means `brief` defaults to `false` when omitted. Change this so `brief` defaults to `true` when not explicitly provided:

```java
// brief defaults to true for keyword searches (performance)
boolean briefMode = (brief == null) ? true : brief;
return documentService.searchDocuments(params.version(), params.keywords(), params.subject(),
        params.extension(), params.limit(), params.offset(), briefMode);
```

Update the `@Parameter` OpenAPI annotation on `brief` to document the new default:

```java
@Parameter(
        description = "When true (default for keyword search), returns only metadata (title, description, " +
                "path, subject, extension, matchedKeywords, score) without full sections and codeBlocks. " +
                "Set to false to include full content (limited to 5 results max for performance). " +
                "Only applies to search mode (ignored in path mode).",
        required = false,
        example = "true",
        schema = @Schema(defaultValue = "true")
)
```

### R2: Paginate Before Loading Full Content When `brief=false`

**File:** `src/main/java/com/fvd/api/services/DocumentService.java`

The current `searchDocuments()` method (line 153-197) iterates over all `FileSearchResult` items from `searchService.searchFiles()` and loads full content for each when `brief=false`. The search service already paginates, but the document parsing happens for every result in the page.

The root cause is that `getOrParseDocument()` is called inside the loop for all paginated items, even when there are many. The search service pagination already limits to `limit` items, but the cost is per-item parsing.

The fix ensures that when `brief=false`, we enforce a stricter limit at the service level:

```java
public DocumentSearchResponse searchDocuments(String version, List<String> keywords,
                                              String subject, String extension,
                                              int limit, int offset, boolean brief) {
    // Enforce lower limit for non-brief mode to prevent timeout
    int effectiveLimit = brief ? limit : Math.min(limit, FULL_CONTENT_MAX_LIMIT);

    PaginatedResult<FileSearchResult> searchResult = searchService.searchFiles(
            version, keywords, extension, subject, effectiveLimit, offset);
    // ... rest of method
}
```

### R3: Add `FULL_CONTENT_MAX_LIMIT` Constant

**File:** `src/main/java/com/fvd/api/services/DocumentService.java`

Add a constant for the maximum number of full-content documents:

```java
/**
 * Maximum number of documents returned with full content (brief=false).
 * Loading full content (sections + codeBlocks) is expensive — each document
 * requires file I/O and AsciiDoc parsing.
 */
private static final int FULL_CONTENT_MAX_LIMIT = 5;
```

### R4: Add Warning When `brief=false` Has Large Result Set

**File:** `src/main/java/com/fvd/api/dto/DocumentSearchResponse.java`

Add an optional `warning` field to `DocumentSearchResponse`:

```java
@SuperBuilder
@NoArgsConstructor
@RegisterForReflection
public class DocumentSearchResponse extends PaginatedResponse<DocumentResponse> {

    @Schema(description = "Warning message when results are limited due to performance constraints")
    public String warning;
}
```

**File:** `src/main/java/com/fvd/api/services/DocumentService.java`

Set the warning when the total exceeds the effective limit and `brief=false`:

```java
DocumentSearchResponse.DocumentSearchResponseBuilder<?, ?> builder = DocumentSearchResponse.builder()
        .results(results)
        .totalCount(searchResult.total())
        .returnedCount(results.size());

if (!brief && searchResult.total() > FULL_CONTENT_MAX_LIMIT) {
    builder.warning("Full content mode (brief=false) is limited to " + FULL_CONTENT_MAX_LIMIT +
            " results for performance. Use brief=true (default) for larger result sets, " +
            "then fetch individual documents by path.");
}

return builder.build();
```

### R5: Update OpenAPI Documentation

**File:** `src/main/java/com/fvd/api/resources/DocumentResource.java`

Update the `@Operation` description to mention the brief default change and full-content limit:

```java
@Operation(
        summary = "Get document by path or search by keywords (at least one required)",
        description = "...\n\n" +
                "Mode 2 — Keyword search: If 'keywords' is provided, searches documents and returns " +
                "matching results with scores. Brief mode (metadata only) is the default for performance. " +
                "Set brief=false to include full sections and codeBlocks (limited to 5 results max). ..."
)
```

---

## Technical Design

### Root Cause Analysis

The performance bottleneck is in `DocumentService.searchDocuments()` (lines 153-197). The call chain:

1. `searchService.searchFiles()` — **fast** (in-memory index lookup, ~1ms)
2. Loop over `searchResult.items()` — **slow when brief=false**:
   - `docStore.read(version, path)` — file I/O (~5-50ms per document)
   - `getOrParseDocument()` → `parseSections()` + `parseCodeBlocks()` — AsciiDoc parsing (~50-200ms per document)
3. For 20 documents: 20 * ~150ms = ~3s (best case, cached) to ~20 * ~250ms = ~5s (uncached)

However, the observed >30s timeout suggests the document cache (`documentCache`) is cold on first request, and some documents are very large (>1000 lines), making parsing expensive. The `parseSections()` method splits content line by line, iterates all lines to find headers, and builds section objects. The `parseCodeBlocks()` method similarly processes the entire content.

### Fix Strategy

The fix is two-pronged:

1. **Default brief=true** — Most AI agents only need metadata to decide which documents to fetch in full. The brief default change makes the common case fast.

2. **Cap full-content requests** — When an AI agent explicitly needs full content via `brief=false`, cap at 5 documents. This is sufficient for targeted retrieval (after an initial brief search to identify relevant documents).

### Why Not Lazy-Load After Pagination?

The search service already paginates (`searchFiles` returns a `PaginatedResult` with only `limit` items). The issue is that even for a page of 20 items, loading full content for all 20 is slow. Reducing the limit to 5 for `brief=false` is simpler and more predictable than implementing lazy-loading, and aligns with the expected usage pattern: brief search → identify targets → fetch by path.

### Backward Compatibility

This is a **behavior change** for callers who relied on the implicit `brief=false` default. However:

- The timeout made `brief=false` effectively unusable anyway (>30s)
- AI agents that explicitly pass `brief=false` will still get full content (up to 5 results)
- The `warning` field is additive — existing clients that don't read it are unaffected
- Agents can use `GET /api/documents?path=...` for full single-document retrieval (unaffected)

---

## Request/Response Examples

### Example 1: Default keyword search (brief=true by default)

**Request:**
```
GET /api/documents?keywords=rest
```

**Response (200, <5s):**
```json
{
    "results": [
        {
            "title": "Writing REST Services",
            "description": "How to write REST services with Quarkus...",
            "path": "rest.adoc",
            "subject": "rest-apis",
            "extension": "quarkus-resteasy-reactive",
            "sections": null,
            "codeBlocks": null,
            "matchedKeywords": ["rest"],
            "score": 15.2
        },
        {
            "title": "REST Client",
            "description": "Using the Quarkus REST client...",
            "path": "rest-client.adoc",
            "subject": "rest-apis",
            "extension": "quarkus-rest-client",
            "sections": null,
            "codeBlocks": null,
            "matchedKeywords": ["rest"],
            "score": 12.8
        }
    ],
    "totalCount": 52,
    "returnedCount": 20
}
```

### Example 2: Explicit brief=false with warning

**Request:**
```
GET /api/documents?keywords=rest&brief=false
```

**Response (200, <10s):**
```json
{
    "results": [
        {
            "title": "Writing REST Services",
            "description": "How to write REST services with Quarkus...",
            "path": "rest.adoc",
            "subject": "rest-apis",
            "extension": "quarkus-resteasy-reactive",
            "sections": [
                {
                    "title": "Creating a REST endpoint",
                    "level": 2,
                    "content": "...",
                    "startLine": 15,
                    "endLine": 120
                }
            ],
            "codeBlocks": [
                {
                    "language": "java",
                    "content": "@Path(\"/hello\")...",
                    "context": "Creating a REST endpoint",
                    "startLine": 25,
                    "endLine": 35
                }
            ],
            "matchedKeywords": ["rest"],
            "score": 15.2
        }
    ],
    "totalCount": 52,
    "returnedCount": 5,
    "warning": "Full content mode (brief=false) is limited to 5 results for performance. Use brief=true (default) for larger result sets, then fetch individual documents by path."
}
```

### Example 3: Explicit brief=false with small result set (no warning)

**Request:**
```
GET /api/documents?keywords=oidc+bearer&brief=false
```

**Response (200):**
```json
{
    "results": [
        {
            "title": "OIDC Bearer Token Authentication",
            "path": "security-oidc-bearer-token-authentication.adoc",
            "sections": [ "..." ],
            "codeBlocks": [ "..." ],
            "matchedKeywords": ["oidc", "bearer"],
            "score": 22.5
        }
    ],
    "totalCount": 3,
    "returnedCount": 3
}
```

No `warning` field because `totalCount` (3) <= `FULL_CONTENT_MAX_LIMIT` (5).

### Example 4: Explicit brief=true (unchanged behavior)

**Request:**
```
GET /api/documents?keywords=rest&brief=true
```

**Response (200):** Same as current behavior — metadata only, no sections/codeBlocks.

---

## Implementation Notes

### Document Cache Mitigates But Does Not Solve

The `documentCache` (`ConcurrentHashMap`) in `DocumentService` caches `ParsedDocument` objects keyed by `version::path`. On subsequent requests for the same documents, parsing is skipped. However:

- First request for any document is always slow (cold cache)
- Cache is invalidated on version refresh (`invalidateDocumentCache()`)
- Cache does not help when different keyword searches match different document sets
- Even with cache hits, returning 20 large documents in a single response consumes significant bandwidth

### Path Mode Unaffected

`GET /api/documents?path=security-overview.adoc` always returns full content for a single document. This is intentional and unaffected by this change — single-document retrieval is fast because it parses only one file.

### Interaction with `fields` Parameter

When `fields` is provided alongside `brief=false`, the field selection filter (Feature 74) applies after serialization. The documents are still fully parsed (the performance cost is in parsing, not serialization). Therefore, `fields` does not mitigate the performance issue — only `brief=true` or the `FULL_CONTENT_MAX_LIMIT` cap helps.

### Configuration Option (Deferred)

Making `FULL_CONTENT_MAX_LIMIT` configurable via `application.properties` (e.g., `app.document-search.full-content-max-limit=5`) is desirable but deferred to keep this fix focused. A hardcoded constant is sufficient for now.

---

## Tasks

- [ ] Add `FULL_CONTENT_MAX_LIMIT = 5` constant to `DocumentService`
- [ ] Change `brief` default to `true` in `DocumentResource.getDocuments()` for keyword search mode
- [ ] Update `@Parameter` OpenAPI annotation on `brief` to document new default
- [ ] Update `@Operation` description to mention brief default and full-content limit
- [ ] Enforce `FULL_CONTENT_MAX_LIMIT` in `DocumentService.searchDocuments()` when `brief=false`
- [ ] Add `warning` field to `DocumentSearchResponse`
- [ ] Set warning message in `searchDocuments()` when `brief=false` and `totalCount > FULL_CONTENT_MAX_LIMIT`
- [ ] Add integration test: `GET /api/documents?keywords=rest` (no brief) returns results with null sections/codeBlocks (brief=true default)
- [ ] Add integration test: `GET /api/documents?keywords=rest&brief=false` returns at most 5 results with full content
- [ ] Add integration test: `GET /api/documents?keywords=rest&brief=false` includes `warning` when totalCount > 5
- [ ] Add integration test: `GET /api/documents?keywords=rest&brief=false&limit=2` returns 2 results with full content (respects explicit limit below cap)
- [ ] Add integration test: `GET /api/documents?keywords=narrowterm&brief=false` with small result set returns no warning
- [ ] Add integration test: `GET /api/documents?keywords=rest&brief=true` still works as before (explicit brief=true)
- [ ] Add unit test: `DocumentService.searchDocuments()` with brief=false caps limit at 5
- [ ] Add unit test: `DocumentService.searchDocuments()` with brief=true uses original limit
- [ ] Verify existing tests pass — brief default change may require updating test expectations
- [ ] Run `./gradlew test` — all tests pass

---

## Acceptance Criteria

1. `GET /api/documents?keywords=rest` (no `brief` parameter) returns results with `null` sections and codeBlocks — brief mode is the default
2. `GET /api/documents?keywords=rest` completes in under 15 seconds (previously timed out at >30s)
3. `GET /api/documents?keywords=rest&brief=false` returns at most 5 results with full sections and codeBlocks
4. `GET /api/documents?keywords=rest&brief=false` includes a `warning` field when totalCount exceeds 5
5. `GET /api/documents?keywords=rest&brief=false&limit=2` returns exactly 2 results with full content (explicit limit below cap is respected)
6. `GET /api/documents?keywords=narrowterm&brief=false` with totalCount <= 5 returns full content with no `warning` field
7. `GET /api/documents?keywords=rest&brief=true` behaves identically to current behavior (no regression)
8. `GET /api/documents?path=security-overview.adoc` still returns full content (path mode unaffected)
9. OpenAPI documentation reflects the new `brief` default and full-content limit
10. All existing tests pass (with updates for the brief default change)
11. `./gradlew test` passes with zero failures

---

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Changing `brief` default is a breaking change for callers expecting full content by default | Medium | Medium | The old default (`brief=false`) caused timeouts, making it effectively broken. Callers can explicitly set `brief=false` to restore old behavior (with the 5-result cap). Document the change in release notes. |
| `FULL_CONTENT_MAX_LIMIT=5` may be too low for some use cases | Low | Low | AI agents typically identify specific documents via brief search first, then fetch by path. 5 full documents per request is sufficient for most workflows. Can be made configurable later. |
| Warning field may confuse clients that parse responses strictly | Low | Low | The `warning` field is `null` when not applicable, and the `@JsonInclude(NON_NULL)` change (Feature 81) would omit it entirely. Clients that don't read it are unaffected. |
| Existing tests may assert `brief=false` as default behavior | Medium | Low | Update affected test assertions. The fix is intentional and tests should reflect the new correct behavior. |
| `DocumentSearchResponse` with `warning` field requires `@SuperBuilder` update | Low | Low | Lombok `@SuperBuilder` on the subclass with a new public field works without changes to the parent builder. |

---

## Estimated Effort

| Task | Hours |
|------|-------|
| Add `FULL_CONTENT_MAX_LIMIT` constant and enforce in `searchDocuments()` | 0.5 |
| Change `brief` default in `DocumentResource.getDocuments()` | 0.25 |
| Add `warning` field to `DocumentSearchResponse` and set in service | 0.5 |
| Update OpenAPI annotations (`@Parameter`, `@Operation`) | 0.5 |
| Integration tests (6 test methods) | 1.5 |
| Unit tests for service limit capping (2 test methods) | 0.5 |
| Update existing tests for new brief default | 1.0 |
| Run full test suite and fix regressions | 0.5 |
| **Total** | **~5.25 hours** |

---

## Files Modified

### Modified Production Files (3 files)
- `src/main/java/com/fvd/api/resources/DocumentResource.java` — change brief default to `true` for keyword search; update OpenAPI annotations
- `src/main/java/com/fvd/api/services/DocumentService.java` — add `FULL_CONTENT_MAX_LIMIT`; enforce limit cap when `brief=false`; set warning message
- `src/main/java/com/fvd/api/dto/DocumentSearchResponse.java` — add optional `warning` field

### New Test Files (estimated 1 file)
- `src/test/java/com/fvd/api/resources/DocumentResourceSlowSearchTest.java` — integration tests for brief default, limit cap, and warning behavior

### Modified Test Files (estimated 1-2 files)
- `src/test/java/com/fvd/api/resources/DocumentResourceTest.java` — update expectations for keyword search tests that assumed `brief=false` default
- `src/test/java/com/fvd/api/services/DocumentServiceTest.java` — unit tests for limit capping behavior

---

## Dependencies

- **None** — this feature is independent and can be implemented without any other feature.
- The existing `brief` parameter, `SearchService.searchFiles()` pagination, and `DocumentService.searchDocuments()` method provide the foundation.
- Compatible with Feature 74 (`fields` parameter) and Feature 81 (`@JsonInclude(NON_NULL)`).

---

END OF FILE
