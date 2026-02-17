# Task 04d: Convert QuarkiverseIntegrationTest to DevServices PostgreSQL

> **Dependencies**: Task 04a (helper deleted, scorer renamed).

## Summary

Convert `QuarkiverseIntegrationTest` to use DevServices PostgreSQL. Remove the `@Inject SqliteSchemaInitializer` injection (deleted in Task 03) and add `@Inject DataSource` with TRUNCATE-based cleanup.

## Changes

### Convert `QuarkiverseIntegrationTest`

File: `src/test/java/com/fvd/quarkiverse/QuarkiverseIntegrationTest.java`

- Remove `@Inject SqliteSchemaInitializer` field and any calls to it.
- Add `@Inject DataSource dataSource`.
- Add `@BeforeEach` TRUNCATE cleanup:

```java
@BeforeEach void cleanup() throws SQLException {
    try (var conn = dataSource.getConnection(); var stmt = conn.createStatement()) {
        stmt.execute("TRUNCATE files, file_keywords, sections, section_keywords, "
            + "code_samples, code_sample_keywords, github_index, document_metadata CASCADE");
    }
}
```

- Ensure the class is annotated with `@QuarkusTest` (may already be).
- Remove any references to `SQLiteDataSource` or `TestSqliteHelper` if present.

## Acceptance Criteria

- [ ] No references to `SqliteSchemaInitializer`, `SQLiteDataSource`, or `TestSqliteHelper`
- [ ] Uses `@QuarkusTest` with `@Inject DataSource`
- [ ] `@BeforeEach` truncates all 8 tables with `CASCADE`
- [ ] All existing test methods and assertions are preserved unchanged
- [ ] Test passes against DevServices PostgreSQL

## Files

- **Modified**: `src/test/java/com/fvd/quarkiverse/QuarkiverseIntegrationTest.java`
