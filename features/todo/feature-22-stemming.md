# Feature 22: Stemming at Index and Query Time

Add a basic English stemmer so that morphological variants like "configure", "configuration", and "configured" all map to the same stem and match each other in search.

## Scope and behavior

- Implement a simple suffix-stripping stemmer as a utility class (`Stemmer`) — no external library.
- Stemming rules (applied in order, first match wins): strip `-ation`, `-tion`, `-sion`, `-ment`, `-ness`, `-able`, `-ible`, `-ous`, `-ive`, `-ful`, `-less`, `-ing` (if remaining length >= 3), `-ed` (if remaining length >= 3), `-ly`, `-er` (if remaining length >= 3), `-est`, `-es` (if remaining length >= 3), `-s` (if remaining length >= 3 and not ending in `ss`).
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

- `Stemmer.stem(String word) → String` — static utility method, returns the stemmed form of a lowercase word.
- `AsciidocParser.extractKeywords(String text)` — apply `Stemmer.stem()` to each token after stop word filtering.
- `KeywordIndexer.build()` — no direct change needed if `extractKeywords` already stems.
- `CodeSampleIndexer.build()` — tokens from `parser.tokenize()` used directly for code content must also be stemmed before counting.
- `SearchService.searchFiles/searchSections/searchCodeSamples` — stem each query keyword in the `keywordSet` construction.
- `KeywordIndexer.applyFilenameBoost()` — stem filename tokens before merging.
- `KeywordIndexer.applyTitleBoost()` — stem title tokens before merging.
- `CodeSampleIndexer.applyImportBoost()` — stem import tokens before merging.
- `CodeSampleIndexer.applyFilenameBoost()` / `applySectionTitleBoost()` — stem tokens before merging.

## Tasks

- [ ] Add unit tests for `Stemmer.stem()`: "configuration" → "configur", "security" → "secur", "running" → "run", "classes" → "class", "used" → "us" or similar consistent stem, short words unchanged.
- [ ] Add unit tests for stemmer edge cases: words shorter than suffix, words that don't match any rule, words already stemmed.
- [ ] Implement `Stemmer` utility class in `com.fvd.asciidocs.parser` (or `com.fvd.common`) with suffix-stripping rules.
- [ ] Add unit tests for `AsciidocParser.extractKeywords()` verifying that output keys are stemmed.
- [ ] Modify `AsciidocParser.extractKeywords()` to stem tokens after stop word filtering.
- [ ] Add unit tests for `KeywordIndexer.buildFileEntry()` verifying stemmed keywords in file and section entries.
- [ ] Update `KeywordIndexer.applyFilenameBoost()` and `applyTitleBoost()` to stem tokens before merging.
- [ ] Add unit tests for `CodeSampleIndexer.buildEntriesForFile()` verifying stemmed keywords.
- [ ] Update `CodeSampleIndexer` to stem code content tokens and boost tokens.
- [ ] Add unit tests for `SearchService` search methods verifying stemmed query keywords match stemmed index keywords.
- [ ] Update query keyword preparation in `SearchService.searchFiles()`, `searchSections()`, `searchCodeSamples()` to apply `Stemmer.stem()`.
- [ ] Add integration tests confirming "configure" and "configuration" return the same search results.
- [ ] Add `app.cache-warmup.full-reset=true` note in operational docs — first deployment needs full reindex.

## Operational notes

- This is a breaking index change. All existing cached keyword data becomes stale.
- On first deployment after this feature, the cache warmup job will rebuild all indexes with stemmed keywords automatically.
- If Feature 18 (prefix matching) is also implemented, stemming runs first, then prefix matching operates on stemmed words. The combination is powerful: "secur" as query → stemmed to "secur" → prefix-matches "secur" (stem of "security"), "secur" (stem of "secured"), etc.
