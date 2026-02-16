# Task 10b: Rewrite Orphaned Services

> **Dependencies**: Task 06 (DocChunkSearchService), Task 10 (old classes deleted).

## Summary

Rewrite `DocumentService`, `CatalogService`, and `RelatedDocumentService` to use `DocChunkStore` / `DocChunkSearchService` instead of deleted legacy classes (`KeywordIndexStore`, `SearchService`, `KeywordIndex`, etc.). These three services were intentionally kept alive in Task 10.

## Changes

### `src/main/java/com/fvd/api/services/DocumentService.java` *(modified)*

- Remove references to `SearchService`, `KeywordIndexStore`, and any deleted search/scoring classes
- Inject `DocChunkSearchService` for search-driven document lookups
- Inject `DocChunkStore` for direct chunk queries (e.g., by page/version)
- Update methods to use `PaginatedChunkResult` / `ChunkSearchResult` instead of old result types
- Keep `invalidateDocumentCache(String version)` method (called by `CacheRefreshJob`)

### `src/main/java/com/fvd/api/services/CatalogService.java` *(modified)*

- Remove references to `KeywordIndexStore` and old model types (`KeywordIndex`, `FileKeywordEntry`)
- Inject `DocChunkStore` for catalog data (distinct pages/topics/extensions per version)
- Update catalog building to query `doc_chunks` instead of legacy keyword index

### `src/main/java/com/fvd/api/services/RelatedDocumentService.java` *(modified)*

- Remove references to `KeywordIndexStore`, `KeywordIndex`, `SearchScorer`, and old scoring infrastructure
- Inject `DocChunkStore` for finding related documents via topic/extension overlap or content similarity
- Update similarity computation to leverage `doc_chunks` data (shared topics, extensions, or fuzzy content match)

### Test files *(modified)*

- `src/test/java/com/fvd/api/services/DocumentServiceTest.java` — update mocks
- `src/test/java/com/fvd/api/services/CatalogServiceTest.java` — update mocks
- `src/test/java/com/fvd/api/services/RelatedDocumentServiceTest.java` — update mocks

### Cleanup after rewrite

- `PaginatedResult.java` can now be deleted (no remaining consumers)
- Verify no stale imports remain

## Acceptance Criteria

- [ ] `DocumentService` uses `DocChunkSearchService` / `DocChunkStore` — no references to deleted classes
- [ ] `CatalogService` uses `DocChunkStore` — no references to `KeywordIndexStore`
- [ ] `RelatedDocumentService` uses `DocChunkStore` — no references to old scoring classes
- [ ] `invalidateDocumentCache()` still works in `DocumentService`
- [ ] All three services' existing test files are updated and pass
- [ ] `PaginatedResult.java` is deleted after rewrite
- [ ] `./gradlew test` succeeds

## Files

- `src/main/java/com/fvd/api/services/DocumentService.java` *(modified)*
- `src/main/java/com/fvd/api/services/CatalogService.java` *(modified)*
- `src/main/java/com/fvd/api/services/RelatedDocumentService.java` *(modified)*
- `src/test/java/com/fvd/api/services/DocumentServiceTest.java` *(modified)*
- `src/test/java/com/fvd/api/services/CatalogServiceTest.java` *(modified)*
- `src/test/java/com/fvd/api/services/RelatedDocumentServiceTest.java` *(modified)*
- `src/main/java/com/fvd/search/services/PaginatedResult.java` *(deleted)*
