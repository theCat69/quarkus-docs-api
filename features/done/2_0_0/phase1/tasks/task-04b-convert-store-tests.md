# Task 04b: Convert Store Tests to DevServices PostgreSQL

> **Dependencies**: Task 04a (helper deleted, scorer renamed).

## Summary

Convert 4 store test files in `com.fvd.indexs.stores` to `@QuarkusTest` integration tests backed by DevServices PostgreSQL. Replace `@TempDir` + manual `DataSource` setup with `@Inject` and TRUNCATE-based cleanup.

## Changes

### Convert `KeywordIndexStoreTest`, `CodeSampleIndexStoreTest`, `DocumentMetadataStoreTest`, `IndexStoreTest`

Apply this conversion to each file:

**Old pattern (remove):**
```java
@TempDir Path tempDir;
@BeforeEach void setup() {
    ds = TestSqliteHelper.createInitializedDataSource(tempDir);
    store = new KeywordIndexStore(ds, ...);
}
```

**New pattern (apply):**
```java
@QuarkusTest
class KeywordIndexStoreTest {
    @Inject KeywordIndexStore store;
    @Inject DataSource dataSource;
    @BeforeEach void cleanup() throws SQLException {
        try (var conn = dataSource.getConnection(); var stmt = conn.createStatement()) {
            stmt.execute("TRUNCATE files, file_keywords, sections, section_keywords, "
                + "code_samples, code_sample_keywords, github_index, document_metadata CASCADE");
        }
    }
}
```

**File-specific notes:**
- `CodeSampleIndexStoreTest` directly instantiates `SQLiteDataSource` (no `TestSqliteHelper`) — same conversion applies.
- `DocumentMetadataStoreTest` has `PRAGMA foreign_keys=ON` calls (lines ~123, 132) that will fail on PostgreSQL — remove them.

## Acceptance Criteria

- [ ] All 4 store tests use `@QuarkusTest` with `@Inject` for store and `DataSource`
- [ ] No references to `SQLiteDataSource`, `TestSqliteHelper`, or `@TempDir`
- [ ] `DocumentMetadataStoreTest` has no `PRAGMA` calls
- [ ] Each test's `@BeforeEach` truncates all 8 tables with `CASCADE`
- [ ] All existing test methods and assertions are preserved unchanged
- [ ] All 4 tests pass against DevServices PostgreSQL

## Files

- **Modified**: `KeywordIndexStoreTest.java`
- **Modified**: `CodeSampleIndexStoreTest.java`
- **Modified**: `DocumentMetadataStoreTest.java`
- **Modified**: `IndexStoreTest.java`
