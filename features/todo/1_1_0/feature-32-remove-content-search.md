# Feature 32: Remove Content Search Endpoint and Related Code

> **Dependencies**: None (independent). Best implemented after Feature 31 to avoid updating content search code that will be deleted.

Remove the `/api/search/content` endpoint and all supporting code: `ContentIndex`, `ContentOccurrence`, `ContentIndexer`, `ContentIndexStore`, `ContentSearchResult`, and associated test files. The content search was a brute-force/inverted-index approach that overlaps with keyword and section search. Removing it simplifies the codebase and reduces SQLite storage/index build time.

## Scope and behavior

### Files to DELETE entirely
- `src/main/java/com/fvd/indexs/indexers/ContentIndex.java`
- `src/main/java/com/fvd/indexs/indexers/ContentOccurrence.java`
- `src/main/java/com/fvd/indexs/indexers/ContentIndexer.java`
- `src/main/java/com/fvd/indexs/stores/ContentIndexStore.java`
- `src/main/java/com/fvd/search/services/ContentSearchResult.java`
- `src/test/java/com/fvd/indexs/indexers/ContentIndexerTest.java`
- `src/test/java/com/fvd/indexs/stores/ContentIndexStoreTest.java`

### SearchResource changes
- Remove the entire `searchContent()` method and its `@GET @Path("/content")` mapping.
- Remove the `import` for `ContentSearchResult`.

### SearchService changes
- Remove the `ContentIndexStore contentIndexStore` field.
- Remove the `Map<String, ContentIndex> contentIndexCache` field.
- Remove the `searchContent()` method.
- Remove the `searchContentBruteForce()` method.
- Remove the `getOrLoadContentIndex()` method.
- Remove `contentIndexCache.remove(version)` from `invalidateCache()`.
- **KEEP** `generateSnippet()` and `computeLineNumber()` — these will be reused by Feature 33 (section search snippets).
- Remove imports: `ContentIndex`, `ContentOccurrence`, `ContentIndexStore`.

### CacheWarmupJob changes
- Remove the `ContentIndexer contentIndexer` field.
- Remove `contentIndexer.build(version, extractedFiles)` from `buildIndexes()`.
- Remove `contentIndexer.build("main", filePathsByExtension)` from `buildMainWithQuarkiverse()`.

### CacheRefreshJob changes
- Remove the `ContentIndexer contentIndexer` field.
- Remove `contentIndexer.build(version, allFilePaths)` from `refreshVersion()`.
- Remove `contentIndexer.build("main", filePathsByExtension)` from `refreshQuarkiverse()`.

### SqliteSchemaInitializer changes
- Remove `content_words` and `content_word_positions` table creation from `createTables()`.
- Remove `content_words` and `content_word_positions` indexes from `createTables()`.
- Remove `DROP TABLE IF EXISTS content_word_positions` and `DROP TABLE IF EXISTS content_words` from `resetSchema()`.

### Test file changes
- Remove content search tests from `SearchResourceTest.java` (any tests hitting `/api/search/content`).
- Remove content-related tests from `SearchServiceTest.java` (tests for `searchContent`, `searchContentBruteForce`, `getOrLoadContentIndex`).
- Update `SearchServiceTest.setUp()` — remove `ContentIndexStore` (currently passed as `null`) from the `SearchService` constructor call, and update the constructor signature accordingly.
- Update `CacheWarmupJobTest.java` — remove `ContentIndexer` mock and verification.
- Update `CacheRefreshJobTest.java` — remove `ContentIndexer` mock and verification.

## Internal interfaces

No new interfaces. Only deletions.

## Response shape

The `/api/search/content` endpoint is removed entirely. Clients calling it will receive 404.

## Tasks

- [ ] Delete `ContentIndex.java`, `ContentOccurrence.java`, `ContentIndexer.java`, `ContentIndexStore.java`, `ContentSearchResult.java`.
- [ ] Delete `ContentIndexerTest.java`, `ContentIndexStoreTest.java`.
- [ ] Remove `searchContent()` endpoint from `SearchResource`.
- [ ] Remove `searchContent()`, `searchContentBruteForce()`, `getOrLoadContentIndex()`, `contentIndexCache` from `SearchService`. Keep `generateSnippet()` and `computeLineNumber()`.
- [ ] Remove `ContentIndexStore` field and `contentIndexCache.remove()` from `SearchService.invalidateCache()`.
- [ ] Remove `ContentIndexer` field and calls from `CacheWarmupJob.buildIndexes()` and `buildMainWithQuarkiverse()`.
- [ ] Remove `ContentIndexer` field and calls from `CacheRefreshJob.refreshVersion()` and `refreshQuarkiverse()`.
- [ ] Remove `content_words` and `content_word_positions` tables and indexes from `SqliteSchemaInitializer.createTables()`.
- [ ] Remove `content_words` and `content_word_positions` from `SqliteSchemaInitializer.resetSchema()`.
- [ ] Remove content search tests from `SearchResourceTest.java`.
- [ ] Remove content search tests from `SearchServiceTest.java`.
- [ ] Update `SearchServiceTest.setUp()` — remove `ContentIndexStore` from `SearchService` constructor call.
- [ ] Update `CacheWarmupJobTest.java` — remove `ContentIndexer` mock/verify.
- [ ] Update `CacheRefreshJobTest.java` — remove `ContentIndexer` mock/verify.
- [ ] Run all tests (`./gradlew test`) — all remaining tests must pass.

## Acceptance Criteria

1. `/api/search/content` returns 404 (endpoint removed).
2. No reference to `ContentIndex`, `ContentOccurrence`, `ContentIndexer`, `ContentIndexStore`, or `ContentSearchResult` exists in the codebase.
3. `content_words` and `content_word_positions` tables are no longer created in SQLite.
4. `generateSnippet()` and `computeLineNumber()` remain in `SearchService`.
5. All remaining tests pass.
6. Cache warmup and refresh no longer build a content index.

## Operational notes

- **Breaking change**: The `/api/search/content` endpoint is permanently removed. Clients must migrate to `/api/search/files` or `/api/search/sections`.
- SQLite database size will decrease since `content_words` and `content_word_positions` tables (often the largest) are no longer created.
- Cache warmup time will decrease since content indexing is skipped.
- Existing SQLite databases will retain the old tables (they won't be dropped automatically). They're harmless orphans. A full reset (`app.cache-warmup.full-reset=true`) will clean them up.
