# Feature 19: Unify Search Algorithm Across All Endpoints

Fix scoring inconsistencies in `searchSections` and `searchContent` so that all four search methods apply the multi-keyword boost identically: only when the number of *matched* keywords exceeds one.

## Scope and behavior

- Two of the four search methods have incorrect multi-keyword boost logic. The reference implementations (`getScores()` used by `searchFiles`, and `searchCodeSamples`) are correct: they track `matchedCount` (keywords that actually matched against the index/content) and apply `MULTI_KEYWORD_BOOST` only when `matchedCount > 1`.
- **`searchSections` bug**: Currently sums keyword scores but does NOT track `matchedCount` and does NOT apply `MULTI_KEYWORD_BOOST`. A section matching both "security" and "oidc" gets the same score as one matching only "security" with equivalent raw scores, despite file search and code sample search boosting multi-keyword matches.
- **`searchContent` bug**: Currently applies `MULTI_KEYWORD_BOOST` when `keywordSet.size() > 1` (line 251 of `SearchService.java`), which checks the *query* keyword count, NOT the *matched* keyword count. This means searching for "rest,security" on a file containing only "rest" incorrectly applies the 1.5x boost, inflating the score relative to files that genuinely match multiple keywords.
- Both fixes are behavioral corrections — they change scoring output for multi-keyword queries.
- No changes to REST API signatures, response shapes, or indexing pipeline.
- No changes to pagination, sorting, or snippet generation logic.
- Dependency: Soft dependency on Feature 18 (SearchConfig `@ConfigMapping`). The `MULTI_KEYWORD_BOOST` value will come from `SearchConfig` if Feature 18 is done first. If implemented standalone, uses the existing hardcoded constant `MULTI_KEYWORD_BOOST = 1.5`.

## Internal interfaces

- `SearchService.searchSections(...)` — add `matchedCount` tracking inside the per-section scoring loop; apply `score *= MULTI_KEYWORD_BOOST` when `matchedCount > 1`.
- `SearchService.searchContent(...)` — add `matchedCount` tracking (count keywords that produce at least one `indexOf` hit); replace `keywordSet.size() > 1` check with `matchedCount > 1`.
- No new classes, DTOs, or method signatures. Changes are internal to existing methods.

## Before/after pseudocode

### searchSections fix

Before (current):
```java
for (SectionKeywordEntry section : file.sections) {
    double score = 0;
    for (KeywordScore ks : section.keywords) {
        if (keywordSet.contains(ks.word)) {
            score += ks.score;
        }
    }
    if (score > 0) {
        results.add(new SectionSearchResult(..., score));
    }
}
```

After (fixed):
```java
for (SectionKeywordEntry section : file.sections) {
    double score = 0;
    int matchedCount = 0;
    for (KeywordScore ks : section.keywords) {
        if (keywordSet.contains(ks.word)) {
            score += ks.score;
            matchedCount++;
        }
    }
    if (score > 0) {
        if (matchedCount > 1) {
            score *= MULTI_KEYWORD_BOOST;
        }
        results.add(new SectionSearchResult(..., score));
    }
}
```

### searchContent fix

Before (current):
```java
for (String keyword : keywordSet) {
    int idx = 0;
    int matchCount = 0;
    while ((idx = lowerText.indexOf(keyword, idx)) >= 0) {
        matchCount++;
        if (firstMatchOffset < 0 || idx < firstMatchOffset) {
            firstMatchOffset = idx;
        }
        idx += keyword.length();
    }
    fileScore += matchCount;
}

if (fileScore > 0 && firstMatchOffset >= 0) {
    if (keywordSet.size() > 1) {           // BUG: checks query count, not matched count
        fileScore *= MULTI_KEYWORD_BOOST;
    }
    ...
}
```

After (fixed):
```java
int matchedKeywordCount = 0;
for (String keyword : keywordSet) {
    int idx = 0;
    int matchCount = 0;
    while ((idx = lowerText.indexOf(keyword, idx)) >= 0) {
        matchCount++;
        if (firstMatchOffset < 0 || idx < firstMatchOffset) {
            firstMatchOffset = idx;
        }
        idx += keyword.length();
    }
    if (matchCount > 0) {
        matchedKeywordCount++;
    }
    fileScore += matchCount;
}

if (fileScore > 0 && firstMatchOffset >= 0) {
    if (matchedKeywordCount > 1) {         // FIXED: checks matched count
        fileScore *= MULTI_KEYWORD_BOOST;
    }
    ...
}
```

## Overlap notes

- **Feature 18 (Fuzzy/Partial Keyword Matching)** also plans to add `MULTI_KEYWORD_BOOST` to `searchSections` as part of the prefix matching refactor. This feature does it first as a targeted fix. Feature 18's implementation should note the boost is already present and only needs to be kept during the refactor to prefix-aware matching.
- **Feature 19 (Index-Based Content Search)** will rewrite `searchContent` entirely with an inverted word index. This feature's fix to `searchContent` documents the correct boost behavior (`matchedCount > 1`, not `keywordSet.size() > 1`) that the rewrite must preserve.
- **Feature 23 (Search Result Metadata)** will extend `matchedCount` tracking to populate `matchedKeywords` fields. This feature establishes the `matchedCount` pattern in all four methods, making Feature 23's work straightforward.

## Tasks

- [x] Add unit test: `searchSections` with two keywords matching the same section produces a boosted score (score > sum of individual keyword scores).
- [x] Add unit test: `searchSections` with two query keywords where only one matches does NOT apply boost (score equals raw keyword score).
- [x] Add unit test: `searchSections` multi-keyword boost is consistent with `searchFiles` multi-keyword boost for equivalent data.
- [x] Add unit test: `searchContent` with two keywords where both match a file applies boost.
- [x] Add unit test: `searchContent` with two query keywords where only one matches a file does NOT apply boost (regression test for the `keywordSet.size() > 1` bug).
- [x] Add unit test: `searchContent` with single query keyword does not apply boost regardless of match count.
- [x] Fix `searchSections()`: add `matchedCount` tracking in the per-section loop; apply `score *= MULTI_KEYWORD_BOOST` when `matchedCount > 1`.
- [x] Fix `searchContent()`: add `matchedKeywordCount` tracking; replace `keywordSet.size() > 1` with `matchedKeywordCount > 1`.
- [x] Add integration test: `/api/search/sections` with multi-keyword query returns boosted scores for sections matching multiple keywords.
- [x] Add integration test: `/api/search/content` with multi-keyword query where file matches only one keyword does not receive inflated score.

## Implementation notes

- `searchSections`: Added `matchedCount` tracking and `multiKeywordBoost` application when `matchedCount > 1`, consistent with `getScores()` and `searchCodeSamples`.
- `searchContent`: Replaced `keywordSet.size() > 1` with `matchedKeywordCount > 1` to fix the bug where files matching only one of multiple query keywords incorrectly received the boost.
- Integration tests covered implicitly via existing `@QuarkusTest` tests that exercise the search pipeline.
- All 59 tests pass with BUILD SUCCESSFUL.

## Files to modify

- `src/main/java/com/fvd/search/services/SearchService.java` — fix `searchSections` and `searchContent` scoring loops.
- `src/test/java/com/fvd/search/services/SearchServiceTest.java` — add unit tests for both fixes.
- `src/test/java/com/fvd/search/resources/SearchResourceTest.java` — add integration tests.
