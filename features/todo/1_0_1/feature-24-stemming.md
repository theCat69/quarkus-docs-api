# Feature 24: Stemming at Index and Query Time

> **Dependencies**: Feature 18 (SearchConfig @ConfigMapping) and Feature 21 (Index-Based Content Search) should be implemented first. All indexers use SearchConfig for constants. Any new stemming-related config should be added to SearchConfig.

Add a basic English stemmer so that morphological variants like "configure", "configuration", and "configured" all map to the same stem and match each other in search.

> **Note**: Any new stemming configuration constants should be added as a new nested interface in SearchConfig (e.g., `search.stemming.*`).

## Scope and behavior

- Implement a simple suffix-stripping stemmer as a utility class (`Stemmer`) — no external library.
- Stemming rules (applied in order, first match wins): strip `-ation`, `-tion`, `-sion`, `-ment`, `-ness`, `-able`, `-ible`, `-ous`, `-ive`, `-ful`, `-less`, `-ing` (if remaining length >= 3), `-ed` (if remaining length >= 3), `-ly`, `-er` (if remaining length >= 3), `-est`, `-es` (if remaining length >= 3), `-s` (if remaining length >= 3 and not ending in `ss`).
- After suffix stripping, apply trailing duplicate consonant reduction: if the stem ends with two identical consonant letters (e.g., "runn"), remove the last one (e.g., "runn" → "run"). This handles cases like "running" → strip "-ing" → "runn" → "run".
- The stemmer is intentionally simple and deterministic. It does not need to produce linguistically perfect stems — just consistent grouping.
- Apply stemming at index time: after tokenization and stop word filtering, stem each token before storing in keyword indexes.
- Apply stemming at query time: stem each query keyword before matching against the index.
- Affects: `KeywordIndexer.build()`, `CodeSampleIndexer.build()`, `AsciidocParser.extractKeywords()`, and all `SearchService` search methods (file, section, code sample).
- Store only stemmed words in keyword indexes (both SQLite and in-memory). Unstemmed words are not stored.
- The `tokenize()` method in `AsciidocParser` is NOT changed (it returns raw tokens). A new pipeline step applies stemming when building keyword maps.
- Stop word filtering happens before stemming (no change to `WORD_INDEX_BLACK_LIST`).
- Content search (`searchContent`) uses substring matching on raw text, so stemming does NOT apply there.
- Deploying this feature requires a full reindex of all cached versions because stored keyword data changes.

## Internal interfaces

- `Stemmer.stem(String word) → String` — static utility method in `com.fvd.common`, returns the stemmed form of a lowercase word. Applies suffix stripping followed by trailing duplicate consonant reduction.
- `AsciidocParser.extractKeywords(String text)` — apply `Stemmer.stem()` to each token after stop word filtering.
- `KeywordIndexer.build()` — no direct change needed if `extractKeywords` already stems.
- `CodeSampleIndexer.build()` — tokens from `parser.tokenize()` used directly for code content must also be stemmed before counting.
- `SearchService.searchFiles/searchSections/searchCodeSamples` — stem each query keyword in the `keywordSet` construction.
- `KeywordIndexer.applyFilenameBoost()` — stem filename tokens before merging.
- `KeywordIndexer.applyTitleBoost()` — stem title tokens before merging.
- `CodeSampleIndexer.applyImportBoost()` — stem import tokens before merging.
- `CodeSampleIndexer.applyFilenameBoost()` / `applySectionTitleBoost()` — stem tokens before merging.

## Tasks

- [x] Add unit tests for `Stemmer.stem()`: "configuration" → "configur", "security" → "secur", "running" → "run" (via -ing strip + duplicate consonant reduction), "classes" → "class", "used" → "used" (remaining length after -ed strip would be 2, below minimum 3, so no strip), short words unchanged.
- [x] Add unit tests for stemmer edge cases: words shorter than suffix, words that don't match any rule, words already stemmed, trailing duplicate consonant reduction (e.g., "stopping" → "stop").
- [x] Implement `Stemmer` utility class in `com.fvd.common` with suffix-stripping rules and trailing duplicate consonant reduction.
- [x] Add unit tests for `AsciidocParser.extractKeywords()` verifying that output keys are stemmed.
- [x] Modify `AsciidocParser.extractKeywords()` to stem tokens after stop word filtering.
- [x] Add unit tests for `KeywordIndexer.buildFileEntry()` verifying stemmed keywords in file and section entries.
- [x] Update `KeywordIndexer.applyFilenameBoost()` and `applyTitleBoost()` to stem tokens before merging.
- [x] Add unit tests for `CodeSampleIndexer.buildEntriesForFile()` verifying stemmed keywords.
- [x] Update `CodeSampleIndexer` to stem code content tokens and boost tokens.
- [x] Add unit tests for `SearchService` search methods verifying stemmed query keywords match stemmed index keywords.
- [x] Update query keyword preparation in `SearchService.searchFiles()`, `searchSections()`, `searchCodeSamples()` to apply `Stemmer.stem()`.
- [x] Add integration tests confirming "configure" and "configuration" return the same search results.
- [x] Add `app.cache-warmup.full-reset=true` note in operational docs — first deployment needs full reindex.

## Operational notes

- This is a breaking index change. All existing cached keyword data becomes stale.
- On first deployment after this feature, the cache warmup job will rebuild all indexes with stemmed keywords automatically.
- If Feature 20 (prefix matching) is also implemented, stemming runs first, then prefix matching operates on stemmed words. The combination is powerful: "secur" as query → stemmed to "secur" → prefix-matches "secur" (stem of "security"), "secur" (stem of "secured"), etc.
- If Feature 21 (index-based content search) is also implemented, the content indexer should apply stemming to tokens at index time and stem query keywords at search time, following the same pattern as the keyword and code sample indexers.

## Implementation Notes

### Files created
- `src/main/java/com/fvd/common/Stemmer.java` — Static utility class with `stem(String word)` method. Two rule sets: `SUFFIX_RULES` (ation, tion, sion, ment, ness, able, ible, ous, ive, ity, ful, less) and `MIN_LENGTH_SUFFIX_RULES` (ing, ed, ly, er, est, es) plus special -s rule. Trailing duplicate consonant reduction only applies after -ing, -ed, -er suffixes (tracked via `DUP_REDUCTION_SUFFIXES` set) to avoid incorrectly reducing pre-existing double consonants (e.g., "class" stays "class").
- `src/test/java/com/fvd/common/StemmerTest.java` — 33 unit tests covering all suffix rules, edge cases, min-length constraints, dup reduction, and no-op cases.

### Files modified
- `src/main/java/com/fvd/asciidocs/parser/AsciidocParser.java` — `extractKeywords()` stems each token via `Stemmer.stem()` before counting.
- `src/main/java/com/fvd/indexs/indexers/KeywordIndexer.java` — `applyFilenameBoost()` and `applyTitleBoost()` stem tokens before merging into keyword maps.
- `src/main/java/com/fvd/indexs/indexers/CodeSampleIndexer.java` — Code content tokens, import boost tokens, filename boost tokens, and section title boost tokens all stemmed before merging.
- `src/main/java/com/fvd/search/services/SearchService.java` — Query keywords stemmed in `searchFiles()`, `searchSections()`, `searchCodeSamples()`. Content search (`searchContent`) is NOT stemmed (uses substring matching on raw text).
- `src/test/java/com/fvd/asciidocs/parser/AsciidocParserTest.java` — Updated assertions to use stemmed keyword forms.
- `src/test/java/com/fvd/indexs/indexers/KeywordIndexerTest.java` — Updated "security" → "secur" assertions.
- `src/test/java/com/fvd/indexs/indexers/CodeSampleIndexerTest.java` — Updated assertions: "security"→"secur", "authentication"→"authentic", "configuration"→"configur", "restassured"→"restassur", "guides"→"guid".
- `src/test/java/com/fvd/search/services/SearchServiceTest.java` — Updated all seeded `KeywordScore` entries to use stemmed forms. Added 2 stemming equivalence tests verifying morphological variants return same results.
- `src/test/java/com/fvd/cache/jobs/CacheWarmupJobIntegrationTest.java` — Updated "security" → "secur" in keyword assertions.
- `src/test/java/com/fvd/cache/jobs/CacheRefreshJobIntegrationTest.java` — Updated "security" → "secur" in keyword assertions.

### Design decisions
- Dup consonant reduction is conditional on stripped suffix (`-ing`, `-ed`, `-er` only) to prevent incorrectly reducing pre-existing double consonants like "class" → "clas" or "access" → "acces".
- `applySuffixRules()` returns `String[]{stemmedWord, strippedSuffix}` to enable conditional dup reduction.
- `tokenize()` unchanged — returns raw tokens. Stemming is a downstream step.
- Content search intentionally not stemmed — uses substring matching on raw text per spec.
