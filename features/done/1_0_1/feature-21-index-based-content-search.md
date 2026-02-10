# Feature 21: Index-Based Full-Text Content Search

> **Dependencies**: Feature 19 (Unify Search Algorithm) established the correct MULTI_KEYWORD_BOOST behavior. This rewrite must preserve it: apply boost only when matchedCount > 1.

Replace the brute-force file-scanning content search with an inverted word index built at index time, eliminating per-request file I/O and enabling efficient keyword lookups.

## Scope and behavior

- Build an inverted content index during cache warmup and refresh, alongside existing keyword and code sample indexes.
- New SQLite table `content_words` stores word occurrences with file path, character offset, and line number per occurrence.
- New SQLite table `content_word_positions` stores individual positions: `(word_id, offset, line_number)`.
- At index time: for each doc file, tokenize the full text (including code blocks), record each word occurrence position (character offset + line number).
- Respect existing stop word list (`WORD_INDEX_BLACK_LIST`) and `MIN_TOKEN_LENGTH` filtering. **Note**: Use `SearchConfig` for any scoring constants (MULTI_KEYWORD_BOOST, MIN_TOKEN_LENGTH, etc.) instead of hardcoding.
- New in-memory cache: `ConcurrentHashMap<String, ContentIndex>` in `SearchService`, loaded from SQLite on first access.
- `ContentIndex` model: maps `word → List<ContentOccurrence(filePath, offset, lineNumber)>` for fast lookup.
- At search time: look up each query keyword in the inverted index, aggregate file scores by occurrence count, generate snippet from the first match offset using existing `generateSnippet()` logic (still reads the file for snippet text).
- Snippet generation still requires reading the file content from `DocStore` — the index provides match offsets to avoid full-text scanning.
- Maintain the same `ContentSearchResult` response shape: `path`, `snippet`, `matchOffset`, `matchLine`, `score`.
- **Behavioral change**: The current brute-force implementation uses substring matching (`indexOf`), which matches keywords within larger words (e.g., "rest" matches inside "forest", "interest"). The inverted word index uses tokenized word matching, so only whole-word tokens match. This improves precision but reduces recall for substring queries. This is an intentional improvement — word-level matching produces more relevant results.
- Scoring: count of word occurrences per file per keyword, with `MULTI_KEYWORD_BOOST` for multi-keyword queries (apply only when matchedCount > 1, preserving Feature 19 behavior). Note that scores will differ from the current substring-based scoring due to the word-boundary change.
- Index must be invalidated alongside keyword/code-sample caches in `SearchService.invalidateCache(version)`.
- Index built in `CacheWarmupJob.warmupVersion()` and `CacheRefreshJob.refreshVersion()` after keyword and code sample indexes.

## Internal interfaces

- `ContentIndexer.build(String version, List<String> filePaths) → ContentIndex` — builds and persists the inverted index.
- `ContentIndexStore.write(String version, ContentIndex index)` — persists to SQLite.
- `ContentIndexStore.read(String version) → Optional<ContentIndex>` — loads from SQLite.
- `ContentIndexStore.exists(String version) → boolean` — checks existence.
- `SearchService.getOrLoadContentIndex(String version) → ContentIndex` — in-memory cache with SQLite fallback.
- `SearchService.searchContent(String version, List<String> keywords, int limit, int offset)` — refactored to use inverted index.

## Response shape

No change to `ContentSearchResult`:
```json
{
  "results": [
    {
      "path": "security-overview.adoc",
      "snippet": "...configuring security in Quarkus...",
      "matchOffset": 1423,
      "matchLine": 42,
      "score": 8.5
    }
  ],
  "total": 15,
  "limit": 10,
  "offset": 0
}
```

## Tasks

- [x] Design `content_words` and `content_word_positions` SQLite tables; add to `SqliteSchemaInitializer`.
- [x] Create `ContentOccurrence` model: `(filePath, offset, lineNumber)`.
- [x] Create `ContentIndex` model: `Map<String, List<ContentOccurrence>>` wrapper with lookup methods.
- [x] Implement `ContentIndexer.build()` — tokenize full file content (whitespace split, strip, lowercase, filter), record character offsets and line numbers.
- [x] Implement `ContentIndexStore` with `write()`, `read()`, `exists()`, `deleteVersion()` following `KeywordIndexStore` patterns.
- [x] Add `contentIndexCache` (`ConcurrentHashMap`) to `SearchService`; add `getOrLoadContentIndex()`.
- [x] Refactor `SearchService.searchContent()` to use inverted index for keyword lookup, then read files only for snippet generation.
- [x] Update `SearchService.invalidateCache()` to also clear `contentIndexCache`.
- [x] Integrate `ContentIndexer.build()` into `CacheWarmupJob.warmupVersion()` after code sample indexer.
- [x] Integrate `ContentIndexer.build()` into `CacheRefreshJob.refreshVersion()` after code sample indexer.
- [x] Add unit tests for `ContentIndexer.build()` — correct word positions, stop word filtering, multi-file indexing.
- [x] Add unit tests for `ContentIndexStore` read/write round-trip.
- [x] Add unit tests for refactored `searchContent()` — verify word-level matching (not substring) and relevance ordering.
- [x] Add integration test via `/api/search/content` endpoint confirming correct results.
- [x] Add test verifying word-boundary behavior: "rest" matches "REST" token but not inside "forest" or "interest".
- [x] Performance validation: verify no per-request file reads except for snippet generation on matched files.

## Implementation notes

### Architecture
- Two-table SQLite design: `content_words` (version, word, file_path) + `content_word_positions` (word_id, char_offset, line_number) with FK and ON DELETE CASCADE.
- Tokenization regex: `[a-zA-Z0-9-]+` — matches word characters including hyphens.
- Full text tokenization: Unlike `KeywordIndexer` which strips code blocks, `ContentIndexer` tokenizes the FULL text including code blocks.
- Word-level matching replaces substring matching — intentional precision improvement.

### Key files
- `ContentOccurrence.java` — Model record: `(filePath, charOffset, lineNumber)`.
- `ContentIndex.java` — Model: `Map<String, List<ContentOccurrence>> wordOccurrences`.
- `ContentIndexStore.java` — SQLite persistence with `exists()`, `read()`, `write()`, `deleteVersion()`.
- `ContentIndexer.java` — `@ApplicationScoped` bean, builds inverted word index from doc files.
- `SearchService.java` — Added `contentIndexStore`, `contentIndexer`, `contentIndexCache`; refactored `searchContent()` with `searchContentBruteForce()` fallback.
- `SqliteSchemaInitializer.java` — Added `content_words` and `content_word_positions` tables; added `resetSchema()` method for test cleanup.

### Test fix: SQLite connection pool stale data
Integration tests that call `FileUtils.cleanDirectory(build/test-cache)` delete the SQLite DB file, but the Agroal connection pool holds open file descriptors to the old (unlinked) file. `initSchema()` with `CREATE TABLE IF NOT EXISTS` on a pooled connection to the old file leaves stale data intact. Fixed by adding `resetSchema()` which drops all tables before recreating them, ensuring a clean database state across test classes.

## Operational notes

- First deployment after this feature requires a full reindex of all cached versions (cache warmup will handle it).
- The content index may be large (every non-stop word with positions). Consider limiting stored positions per word per file (e.g., first 50 occurrences) to control memory and SQLite size.
- If a version's content index is missing (e.g., pre-existing cache), fall back to the current brute-force scan and log a warning.
- If Feature 24 (stemming) is active when this feature is implemented, the content indexer should also apply `Stemmer.stem()` to tokens before indexing, and query keywords should be stemmed before lookup. If Feature 24 is not yet implemented, index raw (unstemmed) tokens. The content indexer should follow the same stemming convention as `KeywordIndexer` and `CodeSampleIndexer`.
