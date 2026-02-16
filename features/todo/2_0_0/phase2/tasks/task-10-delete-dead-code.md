# Task 10: Delete Dead Code

> **Dependencies**: Task 08 (search endpoint replaced), Task 09 (cache jobs updated).

## Summary

Delete all classes no longer referenced after the search re-architecture. **Keep alive**: `Stemmer.java`, `StopWords.java`, `PaginatedResult.java` (until Task 10b), `SearchConfig.java`, `SearchConstants.java`. **Do NOT delete** `DocumentService`, `CatalogService`, `RelatedDocumentService` — rewritten in Task 10b. `SqliteSchemaInitializer` was already deleted in Phase 1.

## Changes

### Delete — Indexers & Models (`com.fvd.indexs.indexers`)

`KeywordIndexer.java`, `CodeSampleIndexer.java`, `KeywordScoreUtils.java`, `KeywordIndex.java`, `CodeSampleIndex.java`, `FileKeywordEntry.java`, `CodeSampleEntry.java`, `SectionKeywordEntry.java`, `KeywordScore.java`

### Delete — Stores (`com.fvd.indexs.stores`)

`KeywordIndexStore.java`, `CodeSampleIndexStore.java`, `DocumentMetadataStore.java`, `AbstractVersionedStore.java`

### Delete — Scoring & Search (`com.fvd.search.services`)

`SearchScorer.java` (interface), `SqliteSearchScorer.java`, `KeywordScorer.java`, `SearchService.java`, `FileSearchResult.java`, `SectionSearchResult.java`, `CodeSampleSearchResult.java`, `SectionContentResult.java`, `MatchedKeyword.java`, `SearchKeywords.java`, `SnippetHighlighter.java`

### Delete — Config (`com.fvd.search`)

`KeywordScoringConfig.java` (only used by deleted classes)

### Delete — API Services (`com.fvd.api.services`)

`QuickSearchService.java`, `CodeSampleService.java`

### Delete — Test files

`KeywordIndexerTest.java`, `KeywordIndexerOriginalWordTest.java`, `KeywordIndexerMetadataIntegrationTest.java`, `CodeSampleIndexerTest.java`, `KeywordScoreUtilsTest.java`, `KeywordIndexStoreTest.java`, `CodeSampleIndexStoreTest.java`, `DocumentMetadataStoreTest.java`, `SearchServiceTest.java`, `SqliteSearchScorerTest.java`, `KeywordScorerTest.java`, `SearchKeywordsTest.java`, `SnippetHighlighterTest.java`, `CodeSampleResourceTest.java`, `TestKeywordScoringConfig.java`

### Verify

- Remove stale imports in remaining files
- `./gradlew compileJava` and `./gradlew compileTestJava` succeed

## Acceptance Criteria

- [ ] All ~30 listed files deleted
- [ ] `Stemmer`, `StopWords`, `PaginatedResult`, `SearchConfig`, `SearchConstants` are NOT deleted
- [ ] `DocumentService`, `CatalogService`, `RelatedDocumentService` are NOT deleted
- [ ] No production or test file imports any deleted class
- [ ] `./gradlew compileJava` and `./gradlew compileTestJava` succeed

## Files

- ~30 files **deleted** (see lists above)
- Any files with stale imports **modified** to remove dead references
