# Feature 30: Extract Stop Words into Shared Constant

> **Dependencies**: None. This is a standalone refactor.

Extract `KeywordIndexer.WORD_INDEX_BLACK_LIST` into a new shared utility class `com.fvd.common.StopWords` so that all indexers and the upcoming query-time stop word removal (Feature 31) can reference a single canonical stop word list.

## Scope and behavior

- Create `com.fvd.common.StopWords` as a `@UtilityClass` (Lombok) with a single public constant `DEFAULT` of type `Set<String>`.
- Move the exact 35-word set from `KeywordIndexer.WORD_INDEX_BLACK_LIST` into `StopWords.DEFAULT`.
- Replace `KeywordIndexer.WORD_INDEX_BLACK_LIST` with a delegation to `StopWords.DEFAULT` (keep the field for backward compat or remove entirely — either is acceptable as long as all references are updated).
- Update `CodeSampleIndexer.buildEntriesForFile()` and `CodeSampleIndexer.applyImportBoost()` to reference `StopWords.DEFAULT` instead of `KeywordIndexer.WORD_INDEX_BLACK_LIST`.
- Update `ContentIndexer.tokenizeAndIndex()` to reference `StopWords.DEFAULT` instead of `KeywordIndexer.WORD_INDEX_BLACK_LIST`.
- No behavioral change — purely a refactor. All existing tests must pass without modification.

## Internal interfaces

- **`com.fvd.common.StopWords`** — new `@UtilityClass`:
  - `public static final Set<String> DEFAULT` — immutable `Set.of(...)` containing the 35 stop words currently in `KeywordIndexer.WORD_INDEX_BLACK_LIST`.

## Tasks

- [x] Create `src/main/java/com/fvd/common/StopWords.java` with `@UtilityClass` and `DEFAULT` set containing the 35 stop words.
- [x] Add unit test `StopWordsTest.java` verifying `DEFAULT` contains all 35 expected words, is not empty, and is immutable.
- [x] Update `KeywordIndexer` — either remove `WORD_INDEX_BLACK_LIST` entirely or delegate it to `StopWords.DEFAULT`.
- [x] Update `CodeSampleIndexer.buildEntriesForFile()` — replace `KeywordIndexer.WORD_INDEX_BLACK_LIST` with `StopWords.DEFAULT`.
- [x] Update `CodeSampleIndexer.applyImportBoost()` — replace `KeywordIndexer.WORD_INDEX_BLACK_LIST` with `StopWords.DEFAULT`.
- [x] Update `ContentIndexer.tokenizeAndIndex()` — replace `KeywordIndexer.WORD_INDEX_BLACK_LIST` with `StopWords.DEFAULT`.
- [x] Run all tests (`./gradlew test`) — all must pass with zero changes to test files.

## Acceptance Criteria

1. `StopWords.DEFAULT` exists and contains exactly the same 35 words as the original `WORD_INDEX_BLACK_LIST`.
2. No reference to `KeywordIndexer.WORD_INDEX_BLACK_LIST` remains in `CodeSampleIndexer` or `ContentIndexer`.
3. All existing tests pass without modification.
4. `StopWords.DEFAULT` is an immutable `Set<String>` (created via `Set.of()`).

## Operational notes

- Zero runtime impact. Pure refactor.
- If `KeywordIndexer.WORD_INDEX_BLACK_LIST` is kept as a delegate (`public static final Set<String> WORD_INDEX_BLACK_LIST = StopWords.DEFAULT`), any external code referencing it will continue to work.

---

## Implementation notes

- **`StopWords.java`**: Created `@UtilityClass` in `com.fvd.common` with `DEFAULT` constant containing the exact 35 stop words as an immutable `Set.of(...)`.
- **`StopWordsTest.java`**: Unit test verifying size==35, known word membership, and immutability (add throws UnsupportedOperationException).
- **`KeywordIndexer.java`**: Replaced inline `Set.of(...)` with delegation `WORD_INDEX_BLACK_LIST = StopWords.DEFAULT`. Field kept for backward compatibility.
- **`CodeSampleIndexer.java`**: Both references in `buildEntriesForFile()` and `applyImportBoost()` changed from `KeywordIndexer.WORD_INDEX_BLACK_LIST` to `StopWords.DEFAULT`.
- **`ContentIndexer.java`**: Reference in `tokenizeAndIndex()` changed from `KeywordIndexer.WORD_INDEX_BLACK_LIST` to `StopWords.DEFAULT`.
- **`AsciidocParser.java`**: Still references `KeywordIndexer.WORD_INDEX_BLACK_LIST` — intentionally left as-is since it was not in the feature scope.
- **All 424 tests pass** with zero modifications to existing test files.
