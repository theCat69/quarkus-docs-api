# Task 05: Update Integration Test Base Classes

> **Dependencies**: Task 03 (SqliteSchemaInitializer deleted), Task 04 (store tests converted, DevServices working).

## Summary

Replace `SqliteSchemaInitializer` injection in the two abstract test base classes with a `DataSource`-based TRUNCATE cleanup. After Task 03 deletes the initializer, these classes no longer compile. Inject `DataSource`, execute `TRUNCATE ... CASCADE` in `@BeforeEach`, and keep all other setup logic (cache directory cleanup, etc.) intact.

## Changes

### `AbstractApiResourceTest.java`

- Remove `@Inject SqliteSchemaInitializer schemaInitializer` field.
- Remove `schemaInitializer.resetSchema()` call from `@BeforeEach`.
- Add `@Inject DataSource dataSource`.
- In `@BeforeEach`, obtain a connection and execute:
  ```sql
  TRUNCATE files, file_keywords, sections, section_keywords,
           code_samples, code_sample_keywords, github_index,
           document_metadata CASCADE
  ```
- Retain all existing setup logic (cache directory cleanup, etc.).

### `AbstractCacheJobIntegrationTest.java`

- Same changes: remove `SqliteSchemaInitializer schemaInitializer`, add `DataSource` + TRUNCATE.

### Fallback

If FK ordering causes issues, replace `TRUNCATE ... CASCADE` with ordered `DELETE FROM` statements (leaf tables first): `code_sample_keywords`, `code_samples`, `section_keywords`, `sections`, `document_metadata`, `file_keywords`, `files`, `github_index`.

## Acceptance Criteria

- [ ] No test class imports or references `SqliteSchemaInitializer`
- [ ] Both base classes inject `DataSource` and TRUNCATE all tables in `@BeforeEach`
- [ ] All integration tests extending these bases pass (`./gradlew test`)
- [ ] No data leaks between test runs (each test starts with empty tables)

## Files Modified

- `src/test/java/com/fvd/api/resources/AbstractApiResourceTest.java`
- `src/test/java/com/fvd/cache/jobs/AbstractCacheJobIntegrationTest.java`
