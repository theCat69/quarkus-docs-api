# Task 04a: Delete TestSqliteHelper & Rename SqliteSearchScorer → SearchScorer

> **Dependencies**: Task 01 (PG dependencies), Task 02 (schema), Task 03 (SqliteSchemaInitializer removed).

## Summary

Prerequisite housekeeping for all test conversions. Delete the obsolete `TestSqliteHelper.java` test utility and rename the production class `SqliteSearchScorer` → `SearchScorer` (plus its test `SqliteSearchScorerTest` → `SearchScorerTest`), updating all references across the codebase.

## Changes

### Delete `src/test/java/com/fvd/common/TestSqliteHelper.java`

Helper that created standalone `SQLiteDataSource` instances — obsolete with DevServices.

### Rename production class

- `SqliteSearchScorer.java` → `SearchScorer.java` — rename file, class name, and all references across the codebase (imports, injection points, test files).

### Rename test class

- `SqliteSearchScorerTest.java` → `SearchScorerTest.java` — rename file, class name, update internal references to use `SearchScorer`.

## Acceptance Criteria

- [ ] `TestSqliteHelper.java` is deleted
- [ ] `SqliteSearchScorer.java` renamed to `SearchScorer.java`; all references updated
- [ ] `SqliteSearchScorerTest.java` renamed to `SearchScorerTest.java`; all references updated
- [ ] No compile errors from stale `SqliteSearchScorer` or `TestSqliteHelper` references
- [ ] Project compiles successfully

## Files

- **Deleted**: `src/test/java/com/fvd/common/TestSqliteHelper.java`
- **Renamed (production)**: `SqliteSearchScorer.java` → `SearchScorer.java`
- **Renamed (test)**: `SqliteSearchScorerTest.java` → `SearchScorerTest.java`
- **Modified**: any files importing or referencing the renamed classes
