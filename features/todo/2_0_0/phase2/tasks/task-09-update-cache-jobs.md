# Task 09: Update Cache Jobs

> **Dependencies**: Task 05 (DocChunkBuilder must exist).

## Summary

Update `CacheWarmupJob` and `CacheRefreshJob` to call `DocChunkBuilder.build()` instead of `KeywordIndexer.build()` and `CodeSampleIndexer.build()`. Remove `searchService.invalidateCache()` calls (no in-memory cache). Keep `documentService.invalidateDocumentCache()` — DocumentService is rewritten in Task 10b, not deleted.

## Changes

### `src/main/java/com/fvd/cache/jobs/CacheWarmupJob.java` *(modified)*

- Remove injected `KeywordIndexer`, `CodeSampleIndexer`
- Inject `DocChunkBuilder`
- Replace `buildIndexes(version, filePaths)` body: swap `keywordIndexer.build()` + `codeSampleIndexer.build()` with `docChunkBuilder.build(version, filePaths)`
- Replace `buildMainWithQuarkiverse()`: swap both indexer `.build("main", filePathsByExtension)` calls with `docChunkBuilder.build("main", filePathsByExtension)`
- Note: `CacheWarmupJob` does NOT inject `SearchService` — no `invalidateCache()` to remove here

### `src/main/java/com/fvd/cache/jobs/CacheRefreshJob.java` *(modified)*

- Remove injected `KeywordIndexer`, `CodeSampleIndexer`, `SearchService`
- Inject `DocChunkBuilder`
- In `refreshVersion()`: replace `keywordIndexer.build()` + `codeSampleIndexer.build()` with `docChunkBuilder.build(version, allFilePaths)`
- Remove `searchService.invalidateCache(version)` call
- **Keep** `documentService.invalidateDocumentCache(version)` (DocumentService rewritten in Task 10b)
- In `refreshQuarkiverse()`: replace both indexer calls with `docChunkBuilder.build("main", filePathsByExtension)`
- Remove `searchService.invalidateCache("main")` call
- **Keep** `documentService.invalidateDocumentCache("main")`

Unchanged: `IndexService`, `DocStore`, `WarmupStatusTracker`, `IndexStore`.

## Acceptance Criteria

- [ ] No references to `KeywordIndexer` or `CodeSampleIndexer` in either job
- [ ] No references to `SearchService` or `invalidateCache()` in either job
- [ ] `documentService.invalidateDocumentCache()` calls are preserved
- [ ] `DocChunkBuilder.build()` overloads match the calling pattern of the old indexers
- [ ] `./gradlew compileJava` succeeds

## Files

- `src/main/java/com/fvd/cache/jobs/CacheWarmupJob.java` *(modified)*
- `src/main/java/com/fvd/cache/jobs/CacheRefreshJob.java` *(modified)*
