# Feature 57: Fix totalCount Mismatch in Search Endpoints

> **Dependencies**: None. This is a bug fix affecting 3 API service classes and 1 search service class.

## Summary

The `totalCount` field returned by all three search endpoints (`/api/search`, `/api/documents`, `/api/code-samples`) is incorrect when a `subject` or `language` filter is applied. The root cause: subject and language filters are applied **after** `SearchService` returns paginated results, but `totalCount` is taken from the pre-filter result count. The fix pushes subject and language filtering down into `SearchService.searchFiles()` and `SearchService.searchCodeSamples()`, so `totalCount` reflects the count after **all** filters are applied.

## User Story

As an **AI agent consuming paginated search results**, I want the `totalCount` field to accurately reflect the number of results matching **all** active filters (keywords, extension, subject, language) so that I can correctly calculate total pages and know when I have retrieved all matching results.

## Motivation

### Current Behavior (Broken)

When calling `GET /api/search?keywords=security&subject=security`:

1. `QuickSearchService.search()` calls `searchService.searchFiles(version, keywords, extension, limit + offset, 0)` (line 50-51)
2. `SearchService.searchFiles()` returns `PaginatedResult` with `total = 150` (all keyword matches, across all subjects)
3. `QuickSearchService` loops over results, skipping entries where `derivedSubject != "security"` (lines 58-62)
4. The response sets `totalCount = searchResult.total()` (line 97) which is **150** (the unfiltered count)
5. But only **25** results actually match the `security` subject filter

The client sees `totalCount: 150` but can never retrieve more than 25 results, making pagination incorrect.

### Same Issue in DocumentService and CodeSampleService

**`DocumentService.searchDocuments()`** (lines 82-118):
- Calls `searchService.searchFiles()` at line 82-83
- Filters by subject at lines 88-91 (post-search)
- Returns `searchResult.total()` at line 116 (pre-filter count)

**`CodeSampleService.searchCodeSamples()`** (lines 51-107):
- Calls `searchService.searchCodeSamples()` at line 51-52, passing `null` for language and subject
- Filters by language at lines 59-61 (post-search)
- Filters by subject at lines 64-67 (post-search)
- Returns `searchResult.total()` at line 105 (pre-filter count)

### Additional Problem: Over-Fetching

`QuickSearchService` requests `limit + offset` results from `SearchService` (line 51) and then manually implements pagination with a `skipped` counter (lines 65-68). This means:
- If subject filtering removes many results, the returned page may have fewer than `limit` items even though more matching results exist
- The manual pagination loop in lines 57-93 is fragile and reimplements what `SearchService.paginate()` already does

---

## Requirements

### R1: Add `subject` Parameter to `SearchService.searchFiles()`

**File:** `src/main/java/com/fvd/search/services/SearchService.java`

**Current signature** (line 53-54):
```java
public PaginatedResult<FileSearchResult> searchFiles(String version, List<String> keywords,
                                                      String extension, int limit, int offset)
```

**New signature:**
```java
public PaginatedResult<FileSearchResult> searchFiles(String version, List<String> keywords,
                                                      String extension, String subject,
                                                      int limit, int offset)
```

**Implementation changes:**
1. Add `SubjectDeriver` as a constructor dependency to `SearchService`
2. In `getFileResults()` (or in the main method after scoring), filter results where `subjectDeriver.deriveSubject(file.path)` does not match the `subject` filter
3. Apply subject filter **before** sorting and pagination, so `total` in `PaginatedResult` reflects the filtered count
4. Use `FilterUtils.matchesFilter(subject, derivedSubject)` for consistency

### R2: Add `subject` and `language` Parameters to `SearchService.searchCodeSamples()`

**File:** `src/main/java/com/fvd/search/services/SearchService.java`

**Current signature** (lines 259-261):
```java
public PaginatedResult<CodeSampleSearchResult> searchCodeSamples(String version, List<String> keywords,
                                                       String filePath, String sectionTitle,
                                                       String extension, int limit, int offset)
```

**New signature:**
```java
public PaginatedResult<CodeSampleSearchResult> searchCodeSamples(String version, List<String> keywords,
                                                       String filePath, String sectionTitle,
                                                       String extension, String subject, String language,
                                                       int limit, int offset)
```

**Implementation changes:**
1. In the loop over `index.samples` (lines 291-313), add subject filter: derive subject from `sample.filePath` and skip if it does not match
2. Add language filter in the same loop: skip if `language` is specified and does not match `sample.language` (case-insensitive)
3. Both filters applied **before** results are added to the list, so `total` is correct after pagination

### R3: Remove Manual Subject Filtering from `QuickSearchService.search()`

**File:** `src/main/java/com/fvd/api/services/QuickSearchService.java`

**Changes:**
1. Pass `subject` to `searchService.searchFiles()` instead of filtering in the loop (lines 58-62)
2. Remove the `skipped` counter and manual pagination (lines 54, 65-68) -- pass `limit` and `offset` directly to `searchService.searchFiles()` instead of `limit + offset, 0`
3. Use `searchResult.total()` as `totalCount` -- it is now correct because `SearchService` filters before pagination
4. Remove `subjectDeriver` dependency from `QuickSearchService` (no longer needed here)

### R4: Remove Manual Subject Filtering from `DocumentService.searchDocuments()`

**File:** `src/main/java/com/fvd/api/services/DocumentService.java`

**Changes:**
1. Pass `subject` to `searchService.searchFiles()` at line 82-83
2. Remove the subject filter check at lines 88-91
3. `searchResult.total()` at line 116 is now correct

**Note:** `DocumentService.getDocumentByPath()` still uses `subjectDeriver` directly (line 62), so the `subjectDeriver` dependency must remain.

### R5: Remove Manual Subject and Language Filtering from `CodeSampleService.searchCodeSamples()`

**File:** `src/main/java/com/fvd/api/services/CodeSampleService.java`

**Changes:**
1. Pass `subject` and `language` to `searchService.searchCodeSamples()` at line 51-52 (currently passes `null, null` for these positions)
2. Remove the language filter at lines 59-61
3. Remove the subject filter at lines 64-67
4. Remove the `skipped` counter and manual pagination (lines 55, 70-73) -- pass `limit` and `offset` directly to `searchService.searchCodeSamples()` instead of `limit + offset, 0`
5. Remove `subjectDeriver` dependency from `CodeSampleService` (no longer needed)

### R6: Inject `SubjectDeriver` into `SearchService`

**File:** `src/main/java/com/fvd/search/services/SearchService.java`

**Changes:**
1. Add `private final SubjectDeriver subjectDeriver;` field
2. `@RequiredArgsConstructor` will inject it via constructor
3. Add import for `com.fvd.subject.services.SubjectDeriver`

---

## Implementation Notes

### Filter Ordering Within SearchService

The subject filter should be applied in the same loop where extension filtering and scoring happen. For `searchFiles()`, inside `getFileResults()`:

```java
// Existing extension filter
if (!FilterUtils.matchesFilter(extension, file.extension)) {
    continue;
}
// NEW: subject filter
String derivedSubject = subjectDeriver.deriveSubject(file.path);
if (!FilterUtils.matchesFilter(subject, derivedSubject)) {
    continue;
}
```

For `searchCodeSamples()`, inside the loop at line 291:

```java
// Existing filters
if (filePath != null && !filePath.isBlank() && !sample.filePath.equals(filePath)) continue;
if (matchedTitle != null && !sample.sectionTitle.equals(matchedTitle)) continue;
if (!FilterUtils.matchesFilter(extension, sample.extension)) continue;
// NEW: subject filter
String derivedSubject = subjectDeriver.deriveSubject(sample.filePath);
if (!FilterUtils.matchesFilter(subject, derivedSubject)) continue;
// NEW: language filter
if (language != null && !language.isBlank() && !language.equalsIgnoreCase(sample.language)) continue;
```

### Backward Compatibility

The old signature of `searchFiles()` and `searchCodeSamples()` can be kept as overloads that pass `null` for subject/language, ensuring no other callers break. Alternatively, since the only callers are the 3 API service classes and existing internal callers within `SearchService`, update all callers.

### No Changes to `SubjectDeriver`

`SubjectDeriver` is used as-is. No modifications needed.

---

## Tasks

- [ ] Add `SubjectDeriver` dependency to `SearchService` constructor
- [ ] Add `subject` parameter to `SearchService.searchFiles()` and apply filter before pagination
- [ ] Add `subject` and `language` parameters to `SearchService.searchCodeSamples()` and apply filters before pagination
- [ ] Update `QuickSearchService.search()` to pass `subject` to `SearchService` and remove manual filtering/pagination
- [ ] Update `DocumentService.searchDocuments()` to pass `subject` to `SearchService` and remove manual filtering
- [ ] Update `CodeSampleService.searchCodeSamples()` to pass `subject` and `language` to `SearchService` and remove manual filtering/pagination
- [ ] Remove `subjectDeriver` dependency from `QuickSearchService` and `CodeSampleService` (no longer needed)
- [ ] Update unit tests for `SearchService` to verify subject and language filtering affects `total`
- [ ] Update unit tests for `QuickSearchService`, `DocumentService`, `CodeSampleService`
- [ ] Add integration test: `GET /api/search?keywords=security&subject=security` and verify `totalCount` matches actual filtered count
- [ ] Add integration test: `GET /api/code-samples?keywords=rest&language=java` and verify `totalCount` matches filtered count
- [ ] Run `./gradlew test` -- all tests pass

---

## Acceptance Criteria

1. `SearchService.searchFiles()` accepts a `subject` parameter and filters results before pagination
2. `SearchService.searchCodeSamples()` accepts `subject` and `language` parameters and filters results before pagination
3. `PaginatedResult.total()` returned by `SearchService` reflects the count **after** all filters (extension, subject, language) are applied
4. `QuickSearchService` no longer contains manual subject filtering or manual pagination logic
5. `CodeSampleService` no longer contains manual language or subject filtering or manual pagination logic
6. `DocumentService.searchDocuments()` no longer contains manual subject filtering
7. `GET /api/search?keywords=security&subject=security` returns `totalCount` that equals the actual number of results matching both keyword and subject filters
8. `GET /api/code-samples?keywords=rest&language=java` returns `totalCount` that equals the actual number of results matching both keyword and language filters
9. Pagination works correctly: requesting `offset=0,limit=5` then `offset=5,limit=5` returns non-overlapping results that together cover all matches
10. `./gradlew test` passes with zero failures

---

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Adding `SubjectDeriver` to `SearchService` creates circular dependency | Low | High | `SubjectDeriver` has no dependency on `SearchService`; verify CDI graph at startup |
| Changing `SearchService` method signatures breaks internal callers | Medium | Medium | Search for all callers of `searchFiles()` and `searchCodeSamples()`; add backward-compatible overloads if needed |
| Subject derivation in `SearchService` loop adds latency | Low | Low | `SubjectDeriver.deriveSubject()` is regex-based and fast (~microseconds per call); already called per-result in current code |
| Removing `subjectDeriver` from `QuickSearchService`/`CodeSampleService` breaks other methods | Low | Medium | Verify no other methods in those classes use `subjectDeriver` before removing |
| Tests that mock `SearchService` need updated signatures | Medium | Low | Update mock setup to match new parameter lists |

---

## Estimated Effort

| Task | Hours |
|------|-------|
| Modify `SearchService` (add dependency, update 2 methods) | 1.0 |
| Update `QuickSearchService` (simplify, remove manual logic) | 0.5 |
| Update `DocumentService` (pass subject, remove filter) | 0.25 |
| Update `CodeSampleService` (pass filters, remove manual logic) | 0.5 |
| Update existing unit tests for all 4 services | 1.5 |
| Add integration tests for totalCount correctness | 1.0 |
| Run full test suite and fix regressions | 0.5 |
| **Total** | **~5.25 hours** |

---

## Files Modified

### Production Code (4 files)
- `src/main/java/com/fvd/search/services/SearchService.java` -- add `SubjectDeriver` dependency, add `subject`/`language` params, filter before pagination
- `src/main/java/com/fvd/api/services/QuickSearchService.java` -- remove manual filtering/pagination, remove `subjectDeriver` dependency
- `src/main/java/com/fvd/api/services/DocumentService.java` -- pass `subject` to `SearchService`, remove manual filter
- `src/main/java/com/fvd/api/services/CodeSampleService.java` -- pass `subject`/`language` to `SearchService`, remove manual filtering/pagination, remove `subjectDeriver` dependency

### Test Code (estimated 4-6 files)
- `src/test/java/com/fvd/search/services/SearchServiceTest.java` -- update method signatures, add subject/language filter tests
- `src/test/java/com/fvd/api/services/QuickSearchServiceTest.java` -- update mocks, verify totalCount
- `src/test/java/com/fvd/api/services/DocumentServiceTest.java` -- update mocks, verify totalCount
- `src/test/java/com/fvd/api/services/CodeSampleServiceTest.java` -- update mocks, verify totalCount

### Unchanged Files
- `src/main/java/com/fvd/subject/services/SubjectDeriver.java` -- no changes needed

---

END OF FILE
