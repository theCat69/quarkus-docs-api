# Feature 67: Add Lightweight Document Search Mode

> **Dependencies**: None. This is a self-contained enhancement to the `/api/documents` endpoint. Compatible with Feature 57 (Fix totalCount Mismatch) and Feature 65 (Validate Version and Subject Parameters).

## Summary

A keyword search on `/api/documents` returns the **entire** document content (full section text + all code blocks) for every matching result. For searches returning 20 results, response sizes exceed 1.3MB. This is extremely expensive for AI/MCP consumers who often only need titles and paths to decide which document to read in full. This feature adds an optional `brief=true` query parameter that returns only metadata (path, title, description, subject, extension, matchedKeywords, score) without the `sections` and `codeBlocks` arrays.

## User Story

As an **AI agent performing document discovery**, I want to search documents by keywords and get a lightweight list of matches (title, path, subject, score) **without** downloading full document content so that I can identify the relevant document first, then fetch its full content by path in a second request.

## Motivation

### Current Behavior (Heavy)

`GET /api/documents?keywords=security` returns:

```json
{
    "results": [
        {
            "title": "Security Overview",
            "description": "Quarkus provides comprehensive security...",
            "path": "security-overview.adoc",
            "subject": "security",
            "extension": "quarkus-core",
            "sections": [
                {
                    "title": "Authentication",
                    "level": 2,
                    "content": "... (500+ lines of AsciiDoc) ...",
                    "startLine": 15,
                    "endLine": 120
                },
                // ... 10+ more sections
            ],
            "codeBlocks": [
                {
                    "language": "java",
                    "content": "... (full code block) ...",
                    "context": "Authentication",
                    "startLine": 45,
                    "endLine": 72
                },
                // ... 5+ more code blocks
            ],
            "matchedKeywords": ["secur"],
            "score": 15.2
        },
        // ... 19 more full documents
    ],
    "totalCount": 42,
    "returnedCount": 20
}
```

Each document result includes full `sections` and `codeBlocks` arrays with the complete AsciiDoc content. For 20 results, the response can exceed **1.3MB**.

### Desired Behavior (Lightweight)

`GET /api/documents?keywords=security&brief=true` returns:

```json
{
    "results": [
        {
            "title": "Security Overview",
            "description": "Quarkus provides comprehensive security...",
            "path": "security-overview.adoc",
            "subject": "security",
            "extension": "quarkus-core",
            "sections": null,
            "codeBlocks": null,
            "matchedKeywords": ["security"],
            "score": 15.2
        },
        // ... 19 more lightweight results
    ],
    "totalCount": 42,
    "returnedCount": 20
}
```

Response size drops from ~1.3MB to ~5KB — a **260x reduction**. The AI agent can then fetch the specific document it needs with `GET /api/documents?path=security-overview.adoc`.

### Two-Step Discovery Pattern for AI Agents

1. **Discovery**: `GET /api/documents?keywords=security&brief=true` → lightweight list of matches
2. **Retrieval**: `GET /api/documents?path=security-overview.adoc` → full document with sections and code blocks

This mirrors how `/api/search` (quick search) already works as a lightweight discovery endpoint, but `/api/documents` is the only endpoint that provides subject, description, and score in one call.

---

## Requirements

### R1: Add `brief` Query Parameter to `DocumentResource`

**File:** `src/main/java/com/fvd/api/resources/DocumentResource.java`

Add a new `brief` query parameter to the `getDocuments()` method (after `offset`):

```java
@Parameter(
        description = "When true, returns only metadata (title, description, path, subject, " +
                "extension, matchedKeywords, score) without full sections and codeBlocks. " +
                "Useful for lightweight discovery before fetching full documents by path.",
        required = false,
        example = "true",
        schema = @Schema(defaultValue = "false")
)
@QueryParam("brief") Boolean brief
```

Pass `brief` to `documentService.searchDocuments()`:

```java
return documentService.searchDocuments(params.version(), params.keywords(), params.subject(),
        params.extension(), params.limit(), params.offset(), Boolean.TRUE.equals(brief));
```

**Note:** The `brief` parameter only applies to **search mode** (when `keywords` is provided). In **path mode** (when `path` is provided), the full document is always returned — the user explicitly requested a specific document.

### R2: Add `brief` Parameter to `DocumentService.searchDocuments()`

**File:** `src/main/java/com/fvd/api/services/DocumentService.java`

**Current signature** (lines 78-80):

```java
public DocumentSearchResponse searchDocuments(String version, List<String> keywords,
                                              String subject, String extension,
                                              int limit, int offset)
```

**New signature:**

```java
public DocumentSearchResponse searchDocuments(String version, List<String> keywords,
                                              String subject, String extension,
                                              int limit, int offset, boolean brief)
```

### R3: Skip Section and Code Block Parsing When `brief=true`

**File:** `src/main/java/com/fvd/api/services/DocumentService.java`

In `searchDocuments()` (lines 85-118), when `brief=true`, the content is still needed for title and description extraction but section and code block parsing should be skipped.

**Current flow** in the loop (lines 86-112):

```java
for (FileSearchResult fileResult : searchResult.items()) {
    // ... subject filter ...
    Optional<String> contentOpt = docStore.read(version, fileResult.path);
    // ... matchedKeywords ...
    DocumentResponse doc = buildDocumentResponse(
            fileResult.path, contentOpt.get(), fileResult.extension,
            derivedSubject, matchedKws, fileResult.score);
    results.add(doc);
}
```

**Modified flow:**

```java
for (FileSearchResult fileResult : searchResult.items()) {
    // ... subject filter ...

    List<String> matchedKws = fileResult.matchedKeywords.stream()
            .map(MatchedKeyword::keyword)
            .toList();

    if (brief) {
        // Lightweight: read content only for title/description, skip section/code parsing
        Optional<String> contentOpt = docStore.read(version, fileResult.path);
        String title = contentOpt.map(DocumentTitleExtractor::extractTitle).orElse("");
        String description = contentOpt.map(this::extractDescription).orElse("");

        results.add(new DocumentResponse(
                title, description, fileResult.path, derivedSubject,
                fileResult.extension, null, null, matchedKws, fileResult.score));
    } else {
        // Full: existing behavior
        Optional<String> contentOpt = docStore.read(version, fileResult.path);
        if (contentOpt.isEmpty()) continue;
        DocumentResponse doc = buildDocumentResponse(
                fileResult.path, contentOpt.get(), fileResult.extension,
                derivedSubject, matchedKws, fileResult.score);
        results.add(doc);
    }
}
```

**Performance benefit:** In brief mode, `parseSections()` (line 126, calls `docParser.parseSections()`) and `parseCodeBlocks()` (line 127, calls `docParser.parseCodeBlocks()`) are skipped entirely. These are the most expensive operations — they parse the full AsciiDoc content line-by-line.

### R4: `DocumentResponse` — `sections` and `codeBlocks` May Be `null`

**File:** `src/main/java/com/fvd/api/dto/DocumentResponse.java`

No structural changes needed. The fields are already nullable reference types (`List<SectionInfo>` and `List<CodeBlockInfo>`). When `brief=true`, they are set to `null` and serialized as `null` in JSON.

If Jackson is configured to skip null fields (`NON_NULL` serialization), the fields would be **omitted** from the response entirely, which is even better for response size. Check if `application.properties` has `quarkus.jackson.serialization-inclusion=NON_NULL`. If not, `null` values are fine and clearly indicate "not fetched."

### R5: Update OpenAPI Documentation

**File:** `src/main/java/com/fvd/api/resources/DocumentResource.java`

Update the `@Operation` description to mention the `brief` mode:

```java
@Operation(
        summary = "Get document by path or search by keywords",
        description = "If 'path' is provided, returns a single document with full structured content " +
                "including sections and code blocks. If 'keywords' is provided, searches documents " +
                "and returns matching results with scores. Path takes precedence if both are provided. " +
                "Returns 400 if neither path nor keywords is provided. " +
                "When 'brief=true' in search mode, returns only metadata without sections and codeBlocks."
)
```

---

## Implementation Notes

### Why Not Use `/api/search` Instead?

The existing `/api/search` endpoint (`SearchResource`) returns `SearchResultRef` objects which are already lightweight (path, title, subject, extension, score, matchedKeywords, snippet). However:

1. `/api/search` does **not** return `description` (the `:description:` attribute or first paragraph)
2. `/api/search` returns a `snippet` (keyword-context excerpt) rather than a structured description
3. `/api/documents?brief=true` provides a consistent interface for both discovery and full retrieval on the same endpoint
4. AI agents using the MCP server may already be using `/api/documents` — adding `brief` mode is non-breaking

### `brief` Only Affects Search Mode

When `path` is provided, the user explicitly asked for a specific document — returning full content is always the correct behavior. The `brief` parameter is ignored in path mode.

### Jackson Null Serialization

If Jackson serializes `null` lists as `null` in JSON, the response includes `"sections": null, "codeBlocks": null`. If configured with `NON_NULL`, the fields are omitted entirely. Both are acceptable — the client should not rely on these fields being present when `brief=true`.

### Content Still Read for Title/Description

Even in brief mode, `docStore.read()` is called to extract the title and description. This I/O cannot be avoided unless the title and description are cached in the keyword index. However:
- Reading the file is fast (cached in OS page cache after indexing)
- `extractTitle()` and `extractDescription()` only scan the first ~20 lines of the file
- The heavy operations — `parseSections()` and `parseCodeBlocks()` — which scan the entire file line-by-line, are skipped

### Response Size Comparison

| Mode | Results | Typical Response Size | Parsing Cost |
|------|---------|----------------------|-------------|
| Full (default) | 20 | ~1.3MB | 20× full AsciiDoc parse |
| Brief | 20 | ~5KB | 20× first-20-lines scan |

---

## Tasks

- [ ] Add `brief` query parameter to `DocumentResource.getDocuments()` with `@Parameter` and `@Schema` annotations
- [ ] Pass `brief` to `DocumentService.searchDocuments()` (search mode only; ignored in path mode)
- [ ] Add `boolean brief` parameter to `DocumentService.searchDocuments()` method signature
- [ ] When `brief=true`, build `DocumentResponse` with `null` sections and `null` codeBlocks, extracting only title and description
- [ ] When `brief=false` (or default), preserve existing full-document behavior
- [ ] Update `@Operation` description on `getDocuments()` to mention `brief` mode
- [ ] Add unit tests for `DocumentService.searchDocuments()` with `brief=true`:
    - Results have `null` sections and `null` codeBlocks
    - Results still have title, description, path, subject, extension, matchedKeywords, score
    - Verify `parseSections()` and `parseCodeBlocks()` are not called (mock verification)
- [ ] Add unit tests for `DocumentService.searchDocuments()` with `brief=false`:
    - Results have populated sections and codeBlocks (existing behavior)
- [ ] Add integration tests:
    - `GET /api/documents?keywords=security&brief=true` returns 200 with results where sections and codeBlocks are null/absent
    - `GET /api/documents?keywords=security&brief=true` response size is significantly smaller than without `brief`
    - `GET /api/documents?keywords=security` (no brief) returns full sections and codeBlocks (unchanged behavior)
    - `GET /api/documents?path=security-overview.adoc&brief=true` returns full document (brief ignored in path mode)
- [ ] Run `./gradlew test` — all tests pass

---

## Acceptance Criteria

1. `GET /api/documents?keywords=security&brief=true` returns results with `null` (or absent) `sections` and `codeBlocks` fields
2. `GET /api/documents?keywords=security&brief=true` results still contain `title`, `description`, `path`, `subject`, `extension`, `matchedKeywords`, and `score`
3. `GET /api/documents?keywords=security` (no `brief` parameter) returns full results with sections and codeBlocks (backward compatible)
4. `GET /api/documents?path=security-overview.adoc&brief=true` returns the full document with sections and codeBlocks (`brief` is ignored in path mode)
5. Response size in brief mode is at least 10x smaller than full mode for equivalent queries
6. `brief` parameter is documented in OpenAPI annotations with description and default value
7. `./gradlew test` passes with zero failures

---

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Clients assume `sections` and `codeBlocks` are always non-null | Medium | Medium | Document in OpenAPI that these fields are null when `brief=true`; use `@Schema(nullable = true)` annotation on the fields |
| `docStore.read()` still called in brief mode (I/O cost) | Low | Low | I/O is minimal; title/description extraction reads only first ~20 lines; full parse is skipped |
| Adding a boolean parameter to service method is less extensible than an enum | Low | Low | A boolean is simplest for a single toggle; if more modes are needed later (e.g., `mode=sections-only`), refactor to an enum |
| Jackson `NON_NULL` config may unexpectedly omit fields, confusing clients | Low | Medium | Check `application.properties` for serialization config; document behavior in OpenAPI description |
| Tests that assert on response structure may fail if null fields are omitted vs present-as-null | Low | Low | Use RestAssured's `body("results[0].sections", nullValue())` or `body("results[0]", not(hasKey("sections")))` based on Jackson config |

---

## Estimated Effort

| Task | Hours |
|------|-------|
| Add `brief` parameter to `DocumentResource` with OpenAPI annotations | 0.5 |
| Add `brief` parameter to `DocumentService.searchDocuments()` and implement brief logic | 1.0 |
| Update OpenAPI operation description | 0.25 |
| Unit tests for brief mode in `DocumentServiceTest` | 1.0 |
| Integration tests for brief mode | 0.75 |
| Run full test suite and fix regressions | 0.5 |
| **Total** | **~4.0 hours** |

---

## Files Modified

### Production Code (2 files)
- `src/main/java/com/fvd/api/resources/DocumentResource.java` — add `brief` query parameter, pass to service, update OpenAPI annotations
- `src/main/java/com/fvd/api/services/DocumentService.java` — add `boolean brief` parameter, skip section/code-block parsing when true

### Unchanged Production Files
- `src/main/java/com/fvd/api/dto/DocumentResponse.java` — no changes needed; `sections` and `codeBlocks` are already nullable
- `src/main/java/com/fvd/api/dto/DocumentSearchResponse.java` — no changes needed
- `src/main/java/com/fvd/api/dto/SectionInfo.java` — no changes needed
- `src/main/java/com/fvd/api/dto/CodeBlockInfo.java` — no changes needed

### Test Code (estimated 2 files)
- `src/test/java/com/fvd/api/services/DocumentServiceTest.java` — add unit tests for brief mode
- `src/test/java/com/fvd/api/resources/DocumentResourceTest.java` — add integration tests for brief parameter

---

END OF FILE
