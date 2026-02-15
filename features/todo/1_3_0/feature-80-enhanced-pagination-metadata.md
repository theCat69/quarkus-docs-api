# Feature 80: Enhanced Pagination Metadata

> **Dependencies**: None. This is a self-contained, backward-compatible enhancement to the `PaginatedResponse` base class. All paginated endpoints automatically inherit the new fields.

## Summary

The `PaginatedResponse<T>` base class currently provides `results`, `totalCount`, and `returnedCount`. AI agents consuming the API through MCP must manually compute whether more results exist and track their current offset for paginating through large result sets. This feature adds `offset`, `limit`, and `hasMore` fields to `PaginatedResponse`, enabling AI agents to paginate without manual state tracking. The fields are computed from existing data and are always present on paginated responses.

## User Story

As an **AI agent consuming the API through an MCP server**, I want paginated responses to include `offset`, `limit`, and `hasMore` fields so that I can efficiently paginate through large result sets without tracking state externally or performing arithmetic to determine if more results exist.

## Motivation

### Current Behavior

`GET /api/search?keywords=security&limit=5&offset=0` returns:

```json
{
    "results": [ "... 5 items ..." ],
    "totalCount": 42,
    "returnedCount": 5
}
```

To determine if more results exist, the AI agent must compute: `offset (0) + returnedCount (5) < totalCount (42) → true`. To request the next page, the agent must remember the current offset and limit, then compute `offset + limit = 5` for the next request.

This is error-prone because:
- The agent may not remember the `limit` and `offset` it used (especially across multiple tool calls)
- The computation `offset + returnedCount < totalCount` must be performed correctly
- If the agent uses `returnedCount` instead of `limit` for the next offset calculation, it may get incorrect results when `returnedCount < limit` (partial page)

### Desired Behavior

`GET /api/search?keywords=security&limit=5&offset=0` returns:

```json
{
    "results": [ "... 5 items ..." ],
    "totalCount": 42,
    "returnedCount": 5,
    "offset": 0,
    "limit": 5,
    "hasMore": true
}
```

The agent can now:
1. Check `hasMore` to know if more results exist (no computation needed)
2. Use `offset + limit` for the next page offset (both values are in the response)
3. Stop paginating when `hasMore` is `false`

### Last Page Example

`GET /api/search?keywords=security&limit=5&offset=40` returns:

```json
{
    "results": [ "... 2 items ..." ],
    "totalCount": 42,
    "returnedCount": 2,
    "offset": 40,
    "limit": 5,
    "hasMore": false
}
```

---

## Scope / Requirements

### R1: Add `offset` Field to `PaginatedResponse`

**File:** `src/main/java/com/fvd/api/dto/PaginatedResponse.java`

Add an `offset` field that records the offset used for the current page:

```java
@Schema(description = "Offset used for this page of results (0-indexed)")
protected int offset;
```

### R2: Add `limit` Field to `PaginatedResponse`

**File:** `src/main/java/com/fvd/api/dto/PaginatedResponse.java`

Add a `limit` field that records the limit used for the current page:

```java
@Schema(description = "Maximum number of results requested for this page")
protected int limit;
```

### R3: Add `hasMore` Field to `PaginatedResponse`

**File:** `src/main/java/com/fvd/api/dto/PaginatedResponse.java`

Add a `hasMore` boolean field computed from existing data:

```java
@Schema(description = "True if more results exist beyond this page")
protected boolean hasMore;
```

The value is computed as: `(offset + returnedCount) < totalCount`

### R4: Update Builder and Factory Method

**File:** `src/main/java/com/fvd/api/dto/PaginatedResponse.java`

Update the `of()` static factory method to accept `offset` and `limit`, and compute `hasMore`:

```java
public static <T> PaginatedResponse<T> of(List<T> results, int total, int offset, int limit) {
    return PaginatedResponse.<T>builder()
            .results(results)
            .totalCount(total)
            .returnedCount(results.size())
            .offset(offset)
            .limit(limit)
            .hasMore((offset + results.size()) < total)
            .build();
}
```

Keep the existing `of(List<T> results, int total)` method for backward compatibility, defaulting `offset=0`, `limit=results.size()`, `hasMore=results.size() < total`:

```java
public static <T> PaginatedResponse<T> of(List<T> results, int total) {
    return of(results, total, 0, results.size());
}
```

### R5: Set Pagination Metadata in Service Methods

All service methods that build paginated responses must set `offset`, `limit`, and `hasMore`. The following files return paginated responses:

**File:** `src/main/java/com/fvd/api/services/DocumentService.java`

In `searchDocuments()` (line 192-197), update the builder:

```java
return DocumentSearchResponse.builder()
        .results(results)
        .totalCount(searchResult.total())
        .returnedCount(results.size())
        .offset(offset)
        .limit(limit)
        .hasMore((offset + results.size()) < searchResult.total())
        .build();
```

**Other service files** that build paginated responses must be updated similarly. The search service methods (`searchFiles`, `searchSections`, `searchCodeSamples`) return `PaginatedResult` records, which are converted to response DTOs in the resource/service layer. Each conversion point must set the pagination metadata.

### R6: Add OpenAPI `@Schema` Annotations

**File:** `src/main/java/com/fvd/api/dto/PaginatedResponse.java`

Add `@Schema` annotations to the new fields for OpenAPI documentation:

```java
@Schema(description = "Offset used for this page of results (0-indexed)", example = "0")
protected int offset;

@Schema(description = "Maximum number of results requested for this page", example = "20")
protected int limit;

@Schema(description = "True if more results exist beyond this page. " +
        "When true, use offset + limit as the offset for the next page request.",
        example = "true")
protected boolean hasMore;
```

---

## Technical Design

### Approach: Add Fields to Base Class

Since all paginated responses extend `PaginatedResponse<T>`, adding the fields to the base class ensures they appear on all paginated endpoints automatically:

- `GET /api/search` → `QuickSearchResponse extends PaginatedResponse<SearchResultRef>`
- `GET /api/documents?keywords=...` → `DocumentSearchResponse extends PaginatedResponse<DocumentResponse>`
- `GET /api/code-samples` → `CodeSampleSearchResponse extends PaginatedResponse<CodeSampleResult>`
- `GET /api/documents/related` → `RelatedDocumentResponse extends PaginatedResponse<RelatedDocumentRef>`

No changes are needed to the subclasses — they inherit the new fields via `@SuperBuilder` and `@Data`.

### `hasMore` Computation

The `hasMore` field is computed as:

```
hasMore = (offset + returnedCount) < totalCount
```

This is correct because:
- `offset` is the 0-indexed starting position
- `returnedCount` is the number of items actually returned (may be less than `limit` on the last page)
- `totalCount` is the total number of items matching the query
- If `offset + returnedCount >= totalCount`, all items have been returned

Edge cases:
- Empty results: `offset=0, returnedCount=0, totalCount=0` → `hasMore = false` (correct)
- Last page: `offset=40, returnedCount=2, totalCount=42` → `hasMore = false` (correct)
- Full page: `offset=0, returnedCount=20, totalCount=42` → `hasMore = true` (correct)
- Exact boundary: `offset=20, returnedCount=20, totalCount=40` → `hasMore = false` (correct)
- Offset beyond total: `offset=50, returnedCount=0, totalCount=42` → `hasMore = false` (correct)

### Service Layer Changes

The pagination metadata must be set wherever paginated responses are built. Based on code analysis:

1. **`DocumentService.searchDocuments()`** (line 192-197) — builds `DocumentSearchResponse` with builder. Must add `.offset(offset).limit(limit).hasMore(...)`.

2. **Other endpoints** that use `PaginatedResponse.of()` — the updated factory method handles the computation.

3. **Services that use `SearchService` pagination** — the `SearchService.paginate()` method returns `PaginatedResult(items, total)`. The `offset` and `limit` are passed through from the resource layer. Each service method that receives the `PaginatedResult` and builds a response DTO must include the pagination metadata.

### Identifying All Builder Callsites

The following code patterns need updating:

```java
// Pattern 1: Direct builder calls
DocumentSearchResponse.builder()
        .results(results)
        .totalCount(total)
        .returnedCount(count)
        .build();

// Pattern 2: PaginatedResponse.of() calls
PaginatedResponse.of(results, total);
```

All instances must include `offset`, `limit`, and `hasMore`.

### Backward Compatibility

The new fields are **additive**. Existing clients that do not read `offset`, `limit`, or `hasMore` are unaffected. The fields always have valid values (integers and boolean), so they never break JSON parsing.

---

## Request/Response Examples

### Example 1: First page with more results

**Request:**
```
GET /api/search?keywords=security&limit=5&offset=0
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
    "returnedCount": 5,
    "offset": 0,
    "limit": 5,
    "hasMore": true
}
```

### Example 2: Last page (partial)

**Request:**
```
GET /api/search?keywords=security&limit=5&offset=40
```

**Response (200):**
```json
{
    "results": [
        {
            "path": "security-misc.adoc",
            "title": "Miscellaneous Security",
            "score": 1.2
        },
        {
            "path": "security-tips.adoc",
            "title": "Security Tips",
            "score": 0.8
        }
    ],
    "totalCount": 42,
    "returnedCount": 2,
    "offset": 40,
    "limit": 5,
    "hasMore": false
}
```

### Example 3: Empty results

**Request:**
```
GET /api/search?keywords=zzzznonexistent&limit=20&offset=0
```

**Response (200):**
```json
{
    "results": [],
    "totalCount": 0,
    "returnedCount": 0,
    "offset": 0,
    "limit": 20,
    "hasMore": false
}
```

### Example 4: Document keyword search

**Request:**
```
GET /api/documents?keywords=security&limit=10&offset=10
```

**Response (200):**
```json
{
    "results": [ "... 10 document items ..." ],
    "totalCount": 25,
    "returnedCount": 10,
    "offset": 10,
    "limit": 10,
    "hasMore": true
}
```

### Example 5: Code sample search

**Request:**
```
GET /api/code-samples?keywords=rest&limit=5&offset=0
```

**Response (200):**
```json
{
    "results": [ "... 5 code sample items ..." ],
    "totalCount": 15,
    "returnedCount": 5,
    "offset": 0,
    "limit": 5,
    "hasMore": true
}
```

---

## Implementation Notes

### Lombok `@Data` and `@SuperBuilder`

`PaginatedResponse` uses `@Data` (which generates getters, setters, `toString`, `equals`, `hashCode`) and `@SuperBuilder`. Adding new fields to the class will automatically include them in:
- The builder (`.offset(0).limit(20).hasMore(true)`)
- Getters/setters
- JSON serialization (Jackson uses public fields or getters)

Since the fields are `protected`, `@Data` generates public getters/setters, which Jackson will use for serialization.

### Default Values

The new fields are primitives (`int offset`, `int limit`, `boolean hasMore`), which default to `0`, `0`, and `false` respectively. This means if a callsite forgets to set them, the response will contain `"offset": 0, "limit": 0, "hasMore": false` — which is incorrect but not a crash. All callsites must be updated.

### Finding All Callsites

Use `grep` to find all places where paginated responses are constructed:

```bash
rg "\.builder\(\)" --include="*.java" | grep -i "response\|paginated"
rg "PaginatedResponse.of" --include="*.java"
```

Each callsite must be updated to include `offset`, `limit`, and `hasMore`.

### `PaginatedResult` Record

The `PaginatedResult<T>` record in `com.fvd.search.services` holds `items` and `total`. It does not hold `offset` or `limit` because those are passed as parameters. The service methods that convert `PaginatedResult` to response DTOs already have `offset` and `limit` in their method signatures — they just need to pass them through to the builder.

---

## Tasks

- [ ] Add `offset`, `limit`, `hasMore` fields to `PaginatedResponse` with `@Schema` annotations
- [ ] Update `PaginatedResponse.of(List, int)` to default `offset=0`, `limit=results.size()`, compute `hasMore`
- [ ] Add overloaded `PaginatedResponse.of(List, int, int, int)` that accepts `offset` and `limit`
- [ ] Update `DocumentService.searchDocuments()` builder to set `offset`, `limit`, `hasMore`
- [ ] Find and update all other paginated response builder callsites (search, code-samples, related-docs)
- [ ] Add integration test: `GET /api/search?keywords=security&limit=5&offset=0` includes `offset`, `limit`, `hasMore` fields
- [ ] Add integration test: `hasMore=true` when more results exist
- [ ] Add integration test: `hasMore=false` on last page
- [ ] Add integration test: `hasMore=false` when no results
- [ ] Add integration test: `offset` and `limit` values match request parameters
- [ ] Add integration test: document search includes pagination metadata
- [ ] Add integration test: code sample search includes pagination metadata
- [ ] Add unit test: `PaginatedResponse.of()` correctly computes `hasMore` for various edge cases
- [ ] Verify existing paginated response tests pass with additional fields
- [ ] Run `./gradlew test` — all tests pass

---

## Acceptance Criteria

1. `GET /api/search?keywords=security&limit=5&offset=0` response includes `"offset": 0`, `"limit": 5`, and `"hasMore": true` (when totalCount > 5)
2. `GET /api/search?keywords=security&limit=5&offset=40` response includes `"hasMore": false` when offset + returnedCount >= totalCount
3. `GET /api/search?keywords=nonexistent` response includes `"offset": 0`, `"limit": 20`, `"hasMore": false` with empty results
4. `GET /api/documents?keywords=security&limit=10&offset=10` response includes pagination metadata
5. `GET /api/code-samples?keywords=rest&limit=5&offset=0` response includes pagination metadata
6. `offset` value in response matches the `offset` query parameter used in the request
7. `limit` value in response matches the effective `limit` used (after validation/defaulting)
8. `hasMore` is correctly computed as `(offset + returnedCount) < totalCount` for all edge cases
9. Existing paginated responses still include `results`, `totalCount`, `returnedCount` (backward compatible)
10. All existing tests pass (new fields are additive)
11. `./gradlew test` passes with zero failures

---

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Missing callsites — some paginated response builders may not be updated | Medium | Medium | Use grep to find all `.builder()` calls on paginated response classes; compile-time won't catch missing fields since they have defaults |
| Primitive default values (0, false) may be misleading if a callsite is missed | Medium | Low | Review all callsites carefully; add a unit test that constructs each response type and verifies pagination metadata |
| `@Data` on `PaginatedResponse` generates `equals`/`hashCode` including new fields | Low | Low | This is correct behavior — pagination metadata should be part of equality comparison |
| `@SuperBuilder` in subclasses may need regeneration after parent class change | Low | Low | Lombok handles this automatically; clean build should resolve any issues |
| Serialization order may change (new fields appear at different positions in JSON) | Low | Low | JSON field order is not guaranteed; well-behaved clients parse by field name, not position |

---

## Estimated Effort

| Task | Hours |
|------|-------|
| Add fields to `PaginatedResponse` with annotations | 0.5 |
| Update `of()` factory methods | 0.25 |
| Update `DocumentService.searchDocuments()` builder | 0.25 |
| Find and update all other builder callsites | 1.0 |
| Integration tests (7 test methods) | 1.5 |
| Unit tests for `PaginatedResponse.of()` edge cases | 0.5 |
| Verify existing tests pass with new fields | 0.5 |
| Run full test suite and fix regressions | 0.5 |
| **Total** | **~5.0 hours** |

---

## Files Modified

### Modified Production Files (2-4 files)
- `src/main/java/com/fvd/api/dto/PaginatedResponse.java` — add `offset`, `limit`, `hasMore` fields; update `of()` factory methods
- `src/main/java/com/fvd/api/services/DocumentService.java` — set pagination metadata in `searchDocuments()` builder
- Additional service files that build paginated response DTOs (to be identified via grep for builder callsites)

### Unchanged Production Files
- `src/main/java/com/fvd/api/dto/QuickSearchResponse.java` — inherits from `PaginatedResponse`, no changes needed
- `src/main/java/com/fvd/api/dto/DocumentSearchResponse.java` — inherits from `PaginatedResponse`, no changes needed
- `src/main/java/com/fvd/api/dto/CodeSampleSearchResponse.java` — inherits from `PaginatedResponse`, no changes needed
- `src/main/java/com/fvd/api/dto/RelatedDocumentResponse.java` — inherits from `PaginatedResponse`, no changes needed
- `src/main/java/com/fvd/search/services/PaginatedResult.java` — internal record, not part of API contract

### New Test Files (1 file)
- `src/test/java/com/fvd/api/dto/PaginatedResponseTest.java` — unit tests for `of()` factory method and `hasMore` computation

### Modified Test Files (estimated 1-3 files)
- Existing integration tests for search, document search, and code-sample search — verify new fields are present in responses

---

## Dependencies

- **None** — this feature is independent and can be implemented without any other feature.
- The `PaginatedResponse` base class and its subclasses (`QuickSearchResponse`, `DocumentSearchResponse`, `CodeSampleSearchResponse`, `RelatedDocumentResponse`) are the foundation.
- Compatible with Feature 74 (`fields` parameter) — the new fields can be selected or excluded via `fields`.

---

END OF FILE
