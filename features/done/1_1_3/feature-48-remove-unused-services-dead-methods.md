# Feature 48: Remove Unused Services & Dead Methods

> **Dependencies**: Feature 47 must be completed first (clean CDI context and all tests passing).

## Summary

Delete two unused `@ApplicationScoped` / utility classes (`DocService` and `ZipStreamProcessor`) along with their tests, and remove 4 dead private methods from `KeywordIndexer` that were superseded by source-tracking equivalents.

## User Story

As a **developer**, I want to remove unused services and dead methods so that:
- The codebase contains only actively-used code
- New contributors are not confused by services that appear functional but are never called
- Dead private methods in `KeywordIndexer` don't obscure the active implementation
- Test maintenance burden is reduced by removing tests for unused code

## Motivation

After the v1.1.1 API simplification and v1.1.2 refactoring, several classes became orphaned:

### 1. `DocService` — Never Injected

`DocService` is an `@ApplicationScoped` bean with a `getOrFetchDoc()` method. However:
- **Zero production code** injects or uses `DocService` — the active code path uses `DocStore` directly
- The only consumer is `DocServiceTest`, which tests the class in isolation with mocks
- Grep confirms: the string `DocService` appears only in `DocService.java` and `DocServiceTest.java`

### 2. `ZipStreamProcessor` — Never Called

`ZipStreamProcessor` was created in v1.1.2 (Feature 46) as a utility for standardized zip processing. However:
- **Zero production code** calls `ZipStreamProcessor.processEntries()` — the `QuarkiverseZipExtractor` does its own inline zip processing
- The only consumer is `ZipStreamProcessorTest`
- Grep confirms: `ZipStreamProcessor` appears only in `ZipStreamProcessor.java` and `ZipStreamProcessorTest.java`

### 3. Dead Private Methods in `KeywordIndexer`

Four private methods in `KeywordIndexer` were superseded by source-tracking equivalents during the v1.1.1 keyword scoring hierarchy feature:

| Dead Method | Superseded By | Evidence |
|------------|---------------|----------|
| `applyFilenameBoost(String, Map<String, Integer>)` | `applyFilenameBoostWithSource(String, Map<String, KeywordWithSource>)` | Different signature; no call within the class |
| `applyTitleBoost(String, Map<String, Integer>)` | `applyHeadingBoostsWithSource(String, Map<String, KeywordWithSource>)` | Different signature; no call within the class |
| `toSortedScores(Map<String, Integer>)` | `toSortedScoresWithSource(Map<String, KeywordWithSource>)` | Different signature; no call within the class |
| `filterFileEntries(Map<String, Integer>)` | `filterKeywordScores(Map<String, KeywordWithSource>)` | Different signature; no call within the class |

All 4 methods are `private` — they cannot be called from outside the class. The active code paths use the `*WithSource` variants exclusively.

> **Note:** `CodeSampleIndexer` has its own `applyFilenameBoost` and `toSortedScores` methods that are ACTIVE — those are different methods in a different class. Do NOT delete those.

---

## Requirements

### 1. Delete `DocService` and Its Test

**Delete production file:**
- `src/main/java/com/fvd/docs/services/DocService.java`

**Delete test file:**
- `src/test/java/com/fvd/docs/services/DocServiceTest.java`

**Verify:** No other file references `DocService` (already confirmed by grep).

### 2. Delete `ZipStreamProcessor` and Its Test

**Delete production file:**
- `src/main/java/com/fvd/common/utils/ZipStreamProcessor.java`

**Delete test file:**
- `src/test/java/com/fvd/common/utils/ZipStreamProcessorTest.java`

**Verify:** No other file references `ZipStreamProcessor` (already confirmed by grep).

### 3. Remove Dead Private Methods from `KeywordIndexer`

Remove 4 private methods from `src/main/java/com/fvd/indexs/indexers/KeywordIndexer.java`:

**Method 1 — `filterFileEntries`:**
```java
// DELETE — superseded by filterKeywordScores()
private Map<String, Integer> filterFileEntries(Map<String, Integer> originalFileKeywords) {
    int minScore = searchConfig.index().minKeywordScore();
    Map<String, Integer> filteredFileKeywords = new HashMap<>();
    for (Map.Entry<String, Integer> fileEntry : originalFileKeywords.entrySet()) {
        if (fileEntry.getValue() >= minScore) {
            filteredFileKeywords.put(fileEntry.getKey(), fileEntry.getValue());
        }
    }
    return filteredFileKeywords;
}
```

**Method 2 — `applyFilenameBoost`:**
```java
// DELETE — superseded by applyFilenameBoostWithSource()
private void applyFilenameBoost(String filePath, Map<String, Integer> keywords) {
    String filename = filePath;
    int lastSlash = filePath.lastIndexOf('/');
    if (lastSlash >= 0) {
        filename = filePath.substring(lastSlash + 1);
    }
    if (filename.endsWith(parser.fileSuffix())) {
        filename = filename.substring(0, filename.length() - parser.fileSuffix().length());
    }
    int boost = searchConfig.boost().filenameBoost();
    List<String> filenameTokens = parser.tokenize(filename.replace("-", " ").replace("_", " "));
    for (String token : filenameTokens) {
        keywords.merge(Stemmer.stem(token), boost, Integer::sum);
    }
}
```

**Method 3 — `applyTitleBoost`:**
```java
// DELETE — superseded by applyHeadingBoostsWithSource()
private void applyTitleBoost(String title, Map<String, Integer> keywords) {
    if (title == null || title.isBlank()) {
        return;
    }
    int boost = searchConfig.boost().titleBoost();
    List<String> titleTokens = parser.tokenize(title);
    for (String token : titleTokens) {
        keywords.merge(Stemmer.stem(token), boost, Integer::sum);
    }
}
```

**Method 4 — `toSortedScores`:**
```java
// DELETE — superseded by toSortedScoresWithSource()
private List<KeywordScore> toSortedScores(Map<String, Integer> keywords) {
    return KeywordScoreUtils.toSortedScores(keywords);
}
```

**Also remove any "Legacy methods" comment block.**

**After deletion, verify that:**
- `KeywordScoreUtils.toSortedScores` is still referenced by `CodeSampleIndexer.toSortedScores()` — that is a SEPARATE active method
- No compilation errors in `KeywordIndexer`
- The `Stemmer` import may become unused if only the dead methods used it — check and clean up if needed

### 4. Clean Up Unused Imports in `KeywordIndexer`

After removing the 4 dead methods, check if any imports in `KeywordIndexer.java` become unused. Potential candidates:

| Import | Still Used? | Action |
|--------|-------------|--------|
| `com.fvd.common.Stemmer` | Yes — used in `extractKeywordsWithSource`, `extractSectionKeywordsWithSource`, `applyFilenameBoostWithSource`, `applyHeadingBoostsWithSource` | Keep |
| `com.fvd.search.KeywordScoringConfig` | Check | Remove if unused |
| All others | Likely still used | Keep |

> **Note:** The implementer must verify each import after deletion. Do not blindly remove imports.

---

## Implementation Notes

### `DocService` Is a CDI Bean

`DocService` is `@ApplicationScoped` with `@RequiredArgsConstructor`. Deleting it removes one CDI bean from the context. Since no code injects it, no `UnsatisfiedResolutionException` will occur.

### `ZipStreamProcessor` Is a `@UtilityClass`

`ZipStreamProcessor` uses `@UtilityClass` (Lombok) — it is not a CDI bean. Deleting it has no CDI impact.

### `KeywordIndexer` Dead Methods Are All Private

All 4 methods are `private` — the Java compiler would catch any internal call within the class at compile time. Since the class currently compiles without error and these methods are not called, they are guaranteed dead.

### `CodeSampleIndexer` Has Same-Named Methods — Do NOT Delete

`CodeSampleIndexer` has its own `applyFilenameBoost` and `toSortedScores` methods that ARE actively used (called from `CodeSampleIndexer.buildFileEntry()`). These are completely separate from the dead methods in `KeywordIndexer`.

---

## Tasks

- [x] Grep for `DocService` across entire codebase — confirm only 2 files reference it
- [x] Delete `src/main/java/com/fvd/docs/services/DocService.java`
- [x] Delete `src/test/java/com/fvd/docs/services/DocServiceTest.java`
- [x] Grep for `ZipStreamProcessor` across entire codebase — confirm only 2 files reference it
- [x] Delete `src/main/java/com/fvd/common/utils/ZipStreamProcessor.java`
- [x] Delete `src/test/java/com/fvd/common/utils/ZipStreamProcessorTest.java`
- [x] Remove `filterFileEntries` method from `KeywordIndexer.java`
- [x] Remove `applyFilenameBoost` method from `KeywordIndexer.java`
- [x] Remove `applyTitleBoost` method from `KeywordIndexer.java`
- [x] Remove `toSortedScores` method from `KeywordIndexer.java`
- [x] Remove "Legacy methods" comment block
- [x] Check and remove any unused imports in `KeywordIndexer.java`
- [x] Run `./gradlew test` — all tests must pass
- [x] Verify application starts cleanly (no CDI errors)

---

## Acceptance Criteria

1. `DocService.java` and `DocServiceTest.java` are deleted
2. `ZipStreamProcessor.java` and `ZipStreamProcessorTest.java` are deleted
3. `KeywordIndexer.java` no longer contains `filterFileEntries`, `applyFilenameBoost(String, Map<String, Integer>)`, `applyTitleBoost`, or `toSortedScores(Map<String, Integer>)` methods
4. `KeywordIndexer.java` still contains `filterKeywordScores`, `applyFilenameBoostWithSource`, `applyHeadingBoostsWithSource`, and `toSortedScoresWithSource` methods (active code preserved)
5. `CodeSampleIndexer.java` is unchanged (its `applyFilenameBoost` and `toSortedScores` are ACTIVE)
6. No file in the codebase contains `import com.fvd.docs.services.DocService` (verified by grep)
7. No file in the codebase contains `import com.fvd.common.utils.ZipStreamProcessor` (verified by grep)
8. `./gradlew test` passes with zero failures
9. Application starts without CDI errors
10. No unused imports remain in `KeywordIndexer.java`

---

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| `DocService` has hidden injection point missed by grep | Very Low | Medium | Grep for `DocService` in all `.java` files; only 2 files found |
| `ZipStreamProcessor` is used via reflection | Very Low | Low | No `@RegisterForReflection`; utility class with no CDI; grep confirms no usage |
| Wrong `applyFilenameBoost` deleted (CodeSampleIndexer's instead of KeywordIndexer's) | Low | High | Spec explicitly names `KeywordIndexer` only; `CodeSampleIndexer` methods have different callers |
| Removing private methods causes KeywordIndexer compilation error | Very Low | Low | All 4 are private with no internal callers; compiler would have already flagged this |
| `KeywordScoringConfig` import becomes unused | Low | None | Implementer must check imports after deletion |

---

## Estimated Effort

| Task | Hours |
|------|-------|
| Delete `DocService` + test | 0.25 |
| Delete `ZipStreamProcessor` + test | 0.25 |
| Remove 4 dead methods from `KeywordIndexer` | 0.5 |
| Verify build + tests + startup | 0.5 |
| **Total** | **1-2 hours** |

---

END OF FILE
