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

- [ ] Create `src/main/java/com/fvd/common/StopWords.java` with `@UtilityClass` and `DEFAULT` set containing the 35 stop words.
- [ ] Add unit test `StopWordsTest.java` verifying `DEFAULT` contains all 35 expected words, is not empty, and is immutable.
- [ ] Update `KeywordIndexer` — either remove `WORD_INDEX_BLACK_LIST` entirely or delegate it to `StopWords.DEFAULT`.
- [ ] Update `CodeSampleIndexer.buildEntriesForFile()` — replace `KeywordIndexer.WORD_INDEX_BLACK_LIST` with `StopWords.DEFAULT`.
- [ ] Update `CodeSampleIndexer.applyImportBoost()` — replace `KeywordIndexer.WORD_INDEX_BLACK_LIST` with `StopWords.DEFAULT`.
- [ ] Update `ContentIndexer.tokenizeAndIndex()` — replace `KeywordIndexer.WORD_INDEX_BLACK_LIST` with `StopWords.DEFAULT`.
- [ ] Run all tests (`./gradlew test`) — all must pass with zero changes to test files.

## Acceptance Criteria

1. `StopWords.DEFAULT` exists and contains exactly the same 35 words as the original `WORD_INDEX_BLACK_LIST`.
2. No reference to `KeywordIndexer.WORD_INDEX_BLACK_LIST` remains in `CodeSampleIndexer` or `ContentIndexer`.
3. All existing tests pass without modification.
4. `StopWords.DEFAULT` is an immutable `Set<String>` (created via `Set.of()`).

## Operational notes

- Zero runtime impact. Pure refactor.
- If `KeywordIndexer.WORD_INDEX_BLACK_LIST` is kept as a delegate (`public static final Set<String> WORD_INDEX_BLACK_LIST = StopWords.DEFAULT`), any external code referencing it will continue to work.

---
