# Task 04c: Convert Indexer & Service Tests to DevServices PostgreSQL

> **Dependencies**: Task 04a (helper deleted, scorer renamed).

## Summary

Convert 6 test files (4 indexer tests + 2 service tests) to `@QuarkusTest` integration tests backed by DevServices PostgreSQL. Replace manual `DataSource` setup with `@Inject` and TRUNCATE-based cleanup.

## Changes

### Indexer tests (4 files in `com.fvd.indexs.indexers`)

Convert `KeywordIndexerTest`, `KeywordIndexerOriginalWordTest`, `KeywordIndexerMetadataIntegrationTest`, `CodeSampleIndexerTest` — apply `@QuarkusTest`, `@Inject` for dependencies and `DataSource`, TRUNCATE cleanup in `@BeforeEach`.

### Service tests (2 files)

Convert `IndexServiceTest` (`com.fvd.indexs.services`) and `SearchServiceTest` (`com.fvd.search.services`) — same conversion pattern.

**Note**: `SearchServiceTest` references `SqliteSearchScorer` which was renamed to `SearchScorer` in Task 04a — update the reference.

### Conversion pattern

Remove `@TempDir`, `TestSqliteHelper`, and manual instantiation. Add:
```java
@Inject DataSource dataSource;
@BeforeEach void cleanup() throws SQLException {
    try (var conn = dataSource.getConnection(); var stmt = conn.createStatement()) {
        stmt.execute("TRUNCATE files, file_keywords, sections, section_keywords, "
            + "code_samples, code_sample_keywords, github_index, document_metadata CASCADE");
    }
}
```

## Acceptance Criteria

- [ ] All 6 tests use `@QuarkusTest` with `@Inject` for dependencies and `DataSource`
- [ ] No references to `SQLiteDataSource`, `TestSqliteHelper`, or `@TempDir`
- [ ] `SearchServiceTest` references `SearchScorer` (not `SqliteSearchScorer`)
- [ ] Each test's `@BeforeEach` truncates all 8 tables with `CASCADE`
- [ ] All existing test methods and assertions are preserved unchanged
- [ ] All 6 tests pass against DevServices PostgreSQL

## Files

- **Modified**: `KeywordIndexerTest.java`
- **Modified**: `KeywordIndexerOriginalWordTest.java`
- **Modified**: `KeywordIndexerMetadataIntegrationTest.java`
- **Modified**: `CodeSampleIndexerTest.java`
- **Modified**: `IndexServiceTest.java`
- **Modified**: `SearchServiceTest.java`
