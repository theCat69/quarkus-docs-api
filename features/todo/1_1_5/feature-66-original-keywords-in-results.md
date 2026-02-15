# Feature 66: Return Original Keywords Alongside Stemmed matchedKeywords

> **Dependencies**: None. This is a self-contained enhancement to the search result DTOs and scoring pipeline.

## Summary

When searching for `security authentication`, the `matchedKeywords` field in search results returns stemmed forms `["secur", "authentic"]` instead of the original terms. This is confusing for API consumers, especially AI agents that need to understand what matched. This feature preserves the original→stemmed mapping through the search pipeline and returns original keywords in the response, with optional access to the stemmed forms.

## User Story

As an **AI agent consuming search results**, I want `matchedKeywords` to contain the **original** search terms (e.g., `"security"`, `"authentication"`) instead of stemmed forms (e.g., `"secur"`, `"authentic"`) so that I can present human-readable matched keywords and correlate them with the original query.

## Motivation

### Current Flow (Stemmed Keywords Only)

1. User sends `keywords=security authentication`
2. `InputValidator.parseKeywords()` splits into `["security", "authentication"]` (original, lowercase)
3. `SearchKeywords.prepare()` (line 16-20 of `SearchKeywords.java`) stems each keyword:
   - `Stemmer.stem("security")` → `"secur"` (suffix rule: `ity` → `""`)
   - `Stemmer.stem("authentication")` → `"authentic"` (suffix rule: `ation` → `""`)
   - Returns `Set<String>{"secur", "authentic"}`
4. `SqliteSearchScorer.computeScore()` (line 20-59 of `SqliteSearchScorer.java`) matches stemmed query keywords against indexed keywords (also stemmed)
5. `MatchedKeyword` record stores `bestQueryKeyword` (line 53) — which is the **stemmed** form
6. API services extract keywords via `MatchedKeyword::keyword` (e.g., `QuickSearchService.java` line 80-82):
   ```java
   List<String> matchedKws = fileResult.matchedKeywords.stream()
           .map(MatchedKeyword::keyword)
           .toList();
   ```
7. Response: `"matchedKeywords": ["secur", "authentic"]`

### Desired Flow (Original Keywords)

The response should return: `"matchedKeywords": ["security", "authentication"]`

The mapping from original to stemmed is established in `SearchKeywords.prepare()` but discarded — only the stemmed set is returned. The fix maintains a `Map<String, String>` (stemmed → original) and threads it through to `MatchedKeyword`.

---

## Requirements

### R1: Maintain Original→Stemmed Mapping in `SearchKeywords`

**File:** `src/main/java/com/fvd/search/services/SearchKeywords.java`

Change `prepare()` to return both the stemmed set and the mapping:

```java
public record PreparedKeywords(Set<String> stemmed, Map<String, String> stemmedToOriginal) {}

public static PreparedKeywords prepare(List<String> keywords) {
    Map<String, String> stemmedToOriginal = new LinkedHashMap<>();
    for (String keyword : keywords) {
        String lower = keyword.toLowerCase();
        String stem = Stemmer.stem(lower);
        // If multiple originals stem to the same value, keep the first
        stemmedToOriginal.putIfAbsent(stem, lower);
    }
    return new PreparedKeywords(stemmedToOriginal.keySet(), stemmedToOriginal);
}
```

The existing `prepare()` signature returns `Set<String>`. To maintain backward compatibility during migration, the old method can be deprecated or callers can be updated to use `PreparedKeywords`.

**Alternative (simpler, no new record):** Return `Map<String, String>` directly, where keys are stemmed and values are original. Callers that need just the stemmed set use `map.keySet()`.

```java
public static Map<String, String> prepareWithOriginals(List<String> keywords) {
    Map<String, String> stemmedToOriginal = new LinkedHashMap<>();
    for (String keyword : keywords) {
        String lower = keyword.toLowerCase();
        String stem = Stemmer.stem(lower);
        stemmedToOriginal.putIfAbsent(stem, lower);
    }
    return stemmedToOriginal;
}
```

### R2: Add `originalKeyword` Field to `MatchedKeyword` Record

**File:** `src/main/java/com/fvd/search/services/MatchedKeyword.java`

Add an `originalKeyword` field to the record:

```java
@RegisterForReflection
public record MatchedKeyword(
        String keyword,           // stemmed keyword (existing, for backward compat in scoring)
        String originalKeyword,   // original user-provided keyword (new)
        String source,
        double weight
) {
    /**
     * Backward-compatible constructor without originalKeyword.
     */
    public MatchedKeyword(String keyword, String source, double weight) {
        this(keyword, keyword, source, weight); // originalKeyword defaults to keyword
    }

    /**
     * Backward-compatible constructor for integer weight without originalKeyword.
     */
    public MatchedKeyword(String keyword, String source, int weight) {
        this(keyword, keyword, source, (double) weight);
    }
}
```

The backward-compatible constructors ensure existing callers (tests, `SqliteSearchScorer`) that don't have the original keyword continue to work by defaulting `originalKeyword` to `keyword`.

### R3: Populate `originalKeyword` in `SqliteSearchScorer`

**File:** `src/main/java/com/fvd/search/services/SqliteSearchScorer.java`

The `computeScore()` method (line 20) currently receives `Set<String> queryKeywords` — the stemmed set. To populate `originalKeyword`, it needs the stemmed→original mapping.

**Option A (Recommended): Change the method signature:**

```java
@Override
public MatchResult computeScore(List<KeywordScore> indexedKeywords,
                                 Set<String> queryKeywords,
                                 Map<String, String> stemmedToOriginal) {
```

Then at line 53, when creating a `MatchedKeyword`:

```java
String original = stemmedToOriginal != null
        ? stemmedToOriginal.getOrDefault(bestQueryKeyword, bestQueryKeyword)
        : bestQueryKeyword;
matchedByQuery.put(bestQueryKeyword,
        new MatchedKeyword(bestQueryKeyword, original, source, bestScore));
```

**Option B: Change `SearchScorer` interface to accept `Map<String, String>` instead of `Set<String>`:**

This requires updating the `SearchScorer` interface signature:

```java
MatchResult computeScore(List<KeywordScore> indexedKeywords, Map<String, String> stemmedToOriginal);
```

Where callers pass the map and the scorer uses `stemmedToOriginal.keySet()` for matching.

Option B is cleaner (fewer parameters), but changes the interface. Since there's only one implementation (`SqliteSearchScorer`), this is safe.

### R4: Thread the Mapping Through `SearchService`

**File:** `src/main/java/com/fvd/search/services/SearchService.java`

There are **3 distinct call sites** in `SearchService` where `SearchKeywords.prepare()` is called and the stemmed set is passed to `computeScore()`. All 3 must be updated:

1. **`searchFiles()`** (line 60): `Set<String> keywordSet = SearchKeywords.prepare(keywords);` → passes to `getFileResults()` → `searchScorer.computeScore(file.keywords, keywordSet)`
2. **`searchSections()`** (line 97): `Set<String> keywordSet = SearchKeywords.prepare(keywords);` → passes to `searchScorer.computeScore(section.keywords, keywordSet)` in the loop at line 113
3. **`searchCodeSamples()`** (line 267): `Set<String> keywordSet = SearchKeywords.prepare(keywords);` → passes to `searchScorer.computeScore(sample.keywords, keywordSet)` at line 302

Each call site changes from:

```java
// Before:
Set<String> keywordSet = SearchKeywords.prepare(keywords);
// ... passed to computeScore(..., keywordSet)

// After:
Map<String, String> stemmedToOriginal = SearchKeywords.prepareWithOriginals(keywords);
// ... passed to computeScore(..., stemmedToOriginal)
```

> **Note on section search:** The `searchSections()` call site (item 2 above) ensures that section search results also carry the original keyword through `MatchedKeyword`. Section search results flow through `SectionSearchResult` which uses `MatchedKeyword` directly — no additional API-layer changes are needed for sections beyond this R4 update. The API-layer changes in R5 only apply to services that manually extract keywords from `MatchedKeyword` via `::keyword` (i.e., `QuickSearchService`, `DocumentService`, `CodeSampleService`).

### R5: Use `originalKeyword` in API Response DTOs

**Files:**
- `src/main/java/com/fvd/api/services/QuickSearchService.java` (line 80-82)
- `src/main/java/com/fvd/api/services/CodeSampleService.java` (line 84-86)
- `src/main/java/com/fvd/api/services/DocumentService.java` (line 99-101)

Change from:

```java
List<String> matchedKws = fileResult.matchedKeywords.stream()
        .map(MatchedKeyword::keyword)
        .toList();
```

To:

```java
List<String> matchedKws = fileResult.matchedKeywords.stream()
        .map(MatchedKeyword::originalKeyword)
        .toList();
```

This is the minimal change — the `matchedKeywords` field in the API response becomes original keywords instead of stemmed ones. No structural changes to `DocumentResponse`, `SearchResultRef`, or `CodeSampleResult` DTOs.

---

## Implementation Notes

### Multiple Originals Mapping to Same Stem

If a user searches for `configure configuring`, both stem to `configur`. The `putIfAbsent` in `prepareWithOriginals()` keeps the first original (`configure`). This is acceptable because:
1. Stemming intentionally groups these as equivalent terms
2. The search only matches once (same stem = one match in the index)
3. Showing either original is equally informative

### Backward Compatibility

- The `MatchedKeyword` record gains a new field but retains backward-compatible constructors
- API response field `matchedKeywords` changes from `["secur"]` to `["security"]` — this is a **behavioral change** to the response content, not the response structure
- Clients that only check for the presence of `matchedKeywords` entries are unaffected
- Clients that use the exact stemmed value for further processing (unlikely for external consumers) would need to adjust

### No New Fields in Response DTOs

The simplest approach: `matchedKeywords` now contains original keywords. No new `stemmedKeywords` or `originalKeywords` fields are added. If future need arises, the `MatchedKeyword` record already has both `keyword` (stemmed) and `originalKeyword` (original), and a richer response structure can be introduced.

---

## Tasks

- [ ] Add `prepareWithOriginals(List<String> keywords)` method to `SearchKeywords` returning `Map<String, String>` (stemmed → original)
- [ ] Add `originalKeyword` field to `MatchedKeyword` record with backward-compatible constructors
- [ ] Update `SearchScorer` interface to accept `Map<String, String>` (stemmed → original) instead of `Set<String>`
- [ ] Update `SqliteSearchScorer.computeScore()` to use the map for matching and populate `originalKeyword` in `MatchedKeyword`
- [ ] Update `SearchService` to use `SearchKeywords.prepareWithOriginals()` and pass the map to `computeScore()`
- [ ] Update `QuickSearchService` to use `MatchedKeyword::originalKeyword` instead of `MatchedKeyword::keyword`
- [ ] Update `DocumentService` to use `MatchedKeyword::originalKeyword` instead of `MatchedKeyword::keyword`
- [ ] Update `CodeSampleService` to use `MatchedKeyword::originalKeyword` instead of `MatchedKeyword::keyword`
- [ ] Add unit tests for `SearchKeywords.prepareWithOriginals()`:
    - Single keyword returns correct mapping (`"security"` → `"secur"` → `"security"`)
    - Multiple keywords return correct mappings
    - Duplicate stems (e.g., `"configure"`, `"configuring"`) keep first original
    - Already-stemmed keyword maps to itself
- [ ] Add unit test for `MatchedKeyword` backward-compatible constructors
- [ ] Update `SqliteSearchScorerTest` / `KeywordScorerTest` to verify `originalKeyword` is populated
- [ ] Update `SearchServiceTest` to verify `originalKeyword` propagation
- [ ] Add integration test: `GET /api/search?keywords=security authentication` and verify `matchedKeywords` contains `["security", "authentication"]` not `["secur", "authentic"]`
- [ ] Run `./gradlew test` — all tests pass

---

## Acceptance Criteria

1. `GET /api/search?keywords=security authentication` returns `matchedKeywords: ["security", "authentication"]` instead of `["secur", "authentic"]`
2. `GET /api/documents?keywords=security` returns `matchedKeywords: ["security"]` instead of `["secur"]`
3. `GET /api/code-samples?keywords=rest` returns `matchedKeywords: ["rest"]` instead of `["rest"]` (unchanged for short words that don't get stemmed)
4. `MatchedKeyword` record contains both `keyword` (stemmed) and `originalKeyword` (original)
5. Backward-compatible `MatchedKeyword` constructors work for callers that don't have the original keyword
6. `SearchKeywords.prepareWithOriginals()` correctly maps stemmed → original for all keyword combinations
7. When multiple original keywords stem to the same value, the first original is used
8. `./gradlew test` passes with zero failures

---

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Changing `SearchScorer` interface breaks implementors | Low | Medium | Only one implementation (`SqliteSearchScorer`); update both interface and implementation together |
| Clients relying on stemmed `matchedKeywords` values break | Low | Medium | Unlikely any client uses stemmed values for logic; this is a cosmetic improvement. Document in release notes |
| Multiple keywords stemming to same value causes confusion in results | Low | Low | `putIfAbsent` keeps the first original; result still shows one match (correct behavior) |
| `MatchedKeyword` record growing too large with backward-compatible constructors | Low | Low | Record is still simple (4 fields); constructors are straightforward |
| Tests that assert on stemmed keyword values (`"secur"`) need updating | Medium | Low | Search-and-replace in test files; all such assertions are explicit strings |
| `SearchService` callers that use old `prepare()` return type break | Medium | Medium | Update all callers; add deprecation annotation to old `prepare()` |

---

## Estimated Effort

| Task | Hours |
|------|-------|
| Add `prepareWithOriginals()` to `SearchKeywords` | 0.25 |
| Update `MatchedKeyword` record | 0.25 |
| Update `SearchScorer` interface + `SqliteSearchScorer` | 0.5 |
| Thread mapping through `SearchService` | 0.5 |
| Update 3 API service classes (`::originalKeyword`) | 0.25 |
| Unit tests for `SearchKeywords` and `MatchedKeyword` | 0.75 |
| Update existing scorer/service tests for new signatures | 1.0 |
| Integration tests for original keywords in response | 0.5 |
| Run full test suite and fix regressions | 0.5 |
| **Total** | **~4.5 hours** |

---

## Files Modified

### Production Code (8 files)
- `src/main/java/com/fvd/search/services/SearchKeywords.java` — add `prepareWithOriginals()` method
- `src/main/java/com/fvd/search/services/MatchedKeyword.java` — add `originalKeyword` field, update constructors
- `src/main/java/com/fvd/search/services/SearchScorer.java` — update interface signature to accept `Map<String, String>`
- `src/main/java/com/fvd/search/services/SqliteSearchScorer.java` — populate `originalKeyword`, use map for matching
- `src/main/java/com/fvd/search/services/SearchService.java` — use `prepareWithOriginals()`, pass map to scorer
- `src/main/java/com/fvd/api/services/QuickSearchService.java` — change `MatchedKeyword::keyword` to `MatchedKeyword::originalKeyword`
- `src/main/java/com/fvd/api/services/DocumentService.java` — change `MatchedKeyword::keyword` to `MatchedKeyword::originalKeyword`
- `src/main/java/com/fvd/api/services/CodeSampleService.java` — change `MatchedKeyword::keyword` to `MatchedKeyword::originalKeyword`

### Test Code (estimated 4-5 files)
- `src/test/java/com/fvd/search/services/SearchKeywordsTest.java` — add tests for `prepareWithOriginals()`
- `src/test/java/com/fvd/search/services/SqliteSearchScorerTest.java` — update signature, verify `originalKeyword`
- `src/test/java/com/fvd/search/services/SearchServiceTest.java` — update for new method signatures
- `src/test/java/com/fvd/api/resources/SearchResourceTest.java` — integration test for original keywords in response
- `src/test/java/com/fvd/api/resources/DocumentResourceTest.java` — integration test for original keywords

### Unchanged Files
- `src/main/java/com/fvd/common/Stemmer.java` — no changes needed
- `src/main/java/com/fvd/api/dto/DocumentResponse.java` — `matchedKeywords` is `List<String>`, no structural change
- `src/main/java/com/fvd/api/dto/SearchResultRef.java` — `matchedKeywords` is `List<String>`, no structural change

---

END OF FILE
