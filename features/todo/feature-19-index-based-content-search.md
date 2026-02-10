# Feature 19: Index-Based Full-Text Content Search

Replace the brute-force file-scanning content search with an inverted word index built at index time, eliminating per-request file I/O and enabling efficient keyword lookups.

## Scope and behavior

- Build an inverted content index during cache warmup and refresh, alongside existing keyword and code sample indexes.
- New SQLite table `content_words` stores word occurrences with file path, character offset, and line number per occurrence.
- New SQLite table `content_word_positions` stores individual positions: `(word_id, offset, line_number)`.
- At index time: for each doc file, tokenize the full text (including code blocks), record each word occurrence position (character offset + line number).
- Respect existing stop word list (`WORD_INDEX_BLACK_LIST`) and `MIN_TOKEN_LENGTH = 3` filtering.
- New in-memory cache: `ConcurrentHashMap<String, ContentIndex>` in `SearchService`, loaded from SQLite on first access.
- `ContentIndex` model: maps `word → List<ContentOccurrence(filePath, offset, lineNumber)>` for fast lookup.
- At search time: look up each query keyword in the inverted index, aggregate file scores by occurrence count, generate snippet from the first match offset using existing `generateSnippet()` logic (still reads the file for snippet text).
- Snippet generation still requires reading the file content from `DocStore` — the index provides match offsets to avoid full-text scanning.
- Maintain the same `ContentSearchResult` response shape: `path`, `snippet`, `matchOffset`, `matchLine`, `score`.
- Scoring: count of occurrences per file per keyword, with `MULTI_KEYWORD_BOOST` for multi-keyword queries (same as current).
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

- [ ] Design `content_words` and `content_word_positions` SQLite tables; add to `SqliteSchemaInitializer`.
- [ ] Create `ContentOccurrence` model: `(filePath, offset, lineNumber)`.
- [ ] Create `ContentIndex` model: `Map<String, List<ContentOccurrence>>` wrapper with lookup methods.
- [ ] Implement `ContentIndexer.build()` — tokenize full file content (whitespace split, strip, lowercase, filter), record character offsets and line numbers.
- [ ] Implement `ContentIndexStore` with `write()`, `read()`, `exists()`, `deleteVersion()` following `KeywordIndexStore` patterns.
- [ ] Add `contentIndexCache` (`ConcurrentHashMap`) to `SearchService`; add `getOrLoadContentIndex()`.
- [ ] Refactor `SearchService.searchContent()` to use inverted index for keyword lookup, then read files only for snippet generation.
- [ ] Update `SearchService.invalidateCache()` to also clear `contentIndexCache`.
- [ ] Integrate `ContentIndexer.build()` into `CacheWarmupJob.warmupVersion()` after code sample indexer.
- [ ] Integrate `ContentIndexer.build()` into `CacheRefreshJob.refreshVersion()` after code sample indexer.
- [ ] Add unit tests for `ContentIndexer.build()` — correct word positions, stop word filtering, multi-file indexing.
- [ ] Add unit tests for `ContentIndexStore` read/write round-trip.
- [ ] Add unit tests for refactored `searchContent()` — same result ranking as before but index-driven.
- [ ] Add integration test via `/api/search/content` endpoint confirming correct results.
- [ ] Performance validation: verify no per-request file reads except for snippet generation on matched files.

## Operational notes

- First deployment after this feature requires a full reindex of all cached versions (cache warmup will handle it).
- The content index may be large (every non-stop word with positions). Consider limiting stored positions per word per file (e.g., first 50 occurrences) to control memory and SQLite size.
- If a version's content index is missing (e.g., pre-existing cache), fall back to the current brute-force scan and log a warning.
