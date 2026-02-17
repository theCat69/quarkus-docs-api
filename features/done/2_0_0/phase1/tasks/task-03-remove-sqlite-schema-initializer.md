# Task 03: Remove SqliteSchemaInitializer

> **Dependencies**: Task 02 (Liquibase schema must be in place before removing manual DDL).

## Summary

Delete `SqliteSchemaInitializer.java` entirely. It manually creates tables via DDL, runs SQLite PRAGMAs (`WAL`, `foreign_keys`, `synchronous`, `cache_size`, `temp_store`), and provides `resetSchema()` for tests. Liquibase now owns schema creation (Task 02) and test cleanup moves to TRUNCATE (Tasks 04 & 05). Audit all store classes to confirm no SQLite-specific SQL remains in production code.

## Changes

### Delete

- `src/main/java/com/fvd/indexs/stores/SqliteSchemaInitializer.java` — `@Priority(100)` startup observer with manual DDL and PRAGMAs; fully replaced by Liquibase.

### Verify & Update

- `AbstractVersionedStore.java` — standard JDBC, no SQLite syntax; update Javadoc if it references "SQLite".
- `KeywordIndexStore.java` — uses `Statement.RETURN_GENERATED_KEYS` (PostgreSQL-compatible); no changes expected.
- `CodeSampleIndexStore.java` — same pattern as above; no changes expected.
- `IndexStore.java` — standard SQL; no changes expected.
- `DocumentMetadataStore.java` — standard SQL; no changes expected.
- Replace any `"SQLite"` references in store Javadoc/comments with DB-agnostic language.

### Not touched (deferred)

- `AbstractApiResourceTest` and `AbstractCacheJobIntegrationTest` inject `SqliteSchemaInitializer` — updated in Task 05 after test base classes are converted.
- `QuarkiverseIntegrationTest.java` directly injects `SqliteSchemaInitializer` — it is a standalone test (does not extend the abstract bases) and is updated in Task 04.

## Acceptance Criteria

- [ ] `SqliteSchemaInitializer.java` is deleted from the source tree
- [ ] No production Java file imports or references `SqliteSchemaInitializer`
- [ ] No `PRAGMA` or `AUTOINCREMENT` string exists in any production Java file
- [ ] All store Javadoc/comments are DB-agnostic (no "SQLite" mentions)
- [ ] Store SQL remains PostgreSQL-compatible (`RETURN_GENERATED_KEYS`, standard INSERT/SELECT/DELETE)
- [ ] `./gradlew compileJava` succeeds (full test run depends on Tasks 04 & 05)

## Files

- **Deleted**: `src/main/java/com/fvd/indexs/stores/SqliteSchemaInitializer.java`
- **Modified (Javadoc only, if needed)**: `AbstractVersionedStore.java`, other store classes
