# Feature 98: PostgreSQL Engine Swap (Phase 1)

## Goal

Replace SQLite with PostgreSQL as the database engine. Same 8-table schema, same application logic, different backend. Liquibase manages schema; Quarkus DevServices provides PostgreSQL in dev/test. All existing tests pass.

## Scope

### In scope
- Swap JDBC driver dependency from SQLite to PostgreSQL
- Add Liquibase for schema management (replaces manual DDL in `SqliteSchemaInitializer`)
- Rewrite `init.sql` as PostgreSQL-compatible Liquibase changeset (`BIGSERIAL`, no PRAGMAs)
- Convert standalone store unit tests to `@QuarkusTest` with DevServices
- Replace `resetSchema()` calls in integration test bases with SQL `TRUNCATE ... CASCADE`
- Rename `SqliteSearchScorerTest` → `SearchScorerTest` (pure Java logic, no DB involved)
- Update Javadoc/comments referencing "SQLite" where appropriate

### Out of scope
- New tables, columns, or schema changes beyond the engine swap
- Indexers, parsers, SearchService, SearchScorer, scoring logic
- DocStore, CacheService, cache jobs
- API resources, DTOs, model classes
- Any new features or endpoints

## Tasks

1. **Update dependencies** — In `build.gradle`, remove `quarkiverse-jdbc-sqlite:3.0.11`, uncomment `quarkus-jdbc-postgresql` and `quarkus-liquibase`.
2. **Update datasource config** — In `application.properties`, remove SQLite datasource block (lines 28–31), uncomment PostgreSQL block (lines 2–7). Remove `%test.quarkus.datasource.jdbc.url` SQLite line (121). Add `%test` DevServices config if needed.
3. **Enable Liquibase config** — Uncomment Liquibase properties (lines 10–12). Create Liquibase changelog file(s) at `src/main/resources/db/postgres.yaml` (and `postgres-test.yaml` if distinct) referencing the DDL changeset.
4. **Write PostgreSQL DDL changeset** — Rewrite `src/main/resources/db/scripts/init.sql` as a Liquibase-compatible PostgreSQL DDL: `BIGSERIAL PRIMARY KEY` instead of `INTEGER PRIMARY KEY AUTOINCREMENT`, remove all `PRAGMA` statements, keep all 8 tables (`files`, `file_keywords`, `sections`, `section_keywords`, `code_samples`, `code_sample_keywords`, `github_index`, `document_metadata`), keep all indexes and foreign keys with `ON DELETE CASCADE`.
5. **Delete or gut `SqliteSchemaInitializer.java`** — Remove PRAGMA calls and manual DDL. Either delete the class entirely or reduce it to a no-op startup bean (Liquibase now owns schema creation). If keeping a reduced version, rename it (e.g., `SchemaInitializer`).
6. **Verify store SQL compatibility** — Audit `KeywordIndexStore.java`, `CodeSampleIndexStore.java`, `IndexStore.java`, `DocumentMetadataStore.java`, and `AbstractVersionedStore.java` for any SQLite-specific SQL. Confirm `Statement.RETURN_GENERATED_KEYS` + `getGeneratedKeys()` works with the PostgreSQL JDBC driver (it does). Update Javadoc referencing "SQLite".
7. **Delete `TestSqliteHelper.java`** — Remove `src/test/java/com/fvd/common/TestSqliteHelper.java` entirely; no longer needed.
8. **Convert store unit tests to `@QuarkusTest`** — Rewrite `KeywordIndexStoreTest`, `CodeSampleIndexStoreTest`, `DocumentMetadataStoreTest`, `IndexStoreTest`: remove `@TempDir`/`SQLiteDataSource` setup, add `@QuarkusTest`, inject stores via `@Inject`, use DevServices-managed PostgreSQL. Add `@BeforeEach` TRUNCATE cleanup.
9. **Update `AbstractApiResourceTest`** — Replace `SqliteSchemaInitializer.resetSchema()` with SQL `TRUNCATE files, file_keywords, sections, section_keywords, code_samples, code_sample_keywords, github_index, document_metadata CASCADE` (or inject `DataSource` and run truncates). Remove the `SqliteSchemaInitializer` injection.
10. **Update `AbstractCacheJobIntegrationTest`** — Same TRUNCATE cleanup approach as task 9. Remove `SqliteSchemaInitializer` injection.
11. **Rename `SqliteSearchScorerTest.java`** → `SearchScorerTest.java` — This test exercises pure Java scoring logic with no DB dependency. Rename for clarity; no other changes needed.
12. **Full test run** — Run `./gradlew test` and verify all tests pass against DevServices PostgreSQL.

## Acceptance Criteria

- `./gradlew test` passes with zero failures on PostgreSQL (DevServices)
- No SQLite dependency remains in `build.gradle`
- No `PRAGMA` or `AUTOINCREMENT` in any production source file
- Liquibase changelog creates all 8 tables with correct columns, types, indexes, and foreign keys
- Schema is created automatically on startup via Liquibase (`migrate-at-start=true`)
- All store operations (insert, read, delete, `RETURN_GENERATED_KEYS`) work unchanged
- `TestSqliteHelper.java` is deleted; no test references `SQLiteDataSource`
- Dev mode (`./gradlew quarkusDev`) starts successfully with DevServices PostgreSQL

## Risks / Notes

- **`RETURN_GENERATED_KEYS` with PostgreSQL** — PostgreSQL JDBC driver supports this natively. Low risk, but verify during task 6.
- **TRUNCATE vs DELETE in tests** — `TRUNCATE ... CASCADE` is faster but requires no active transactions. If test isolation issues arise, fall back to `DELETE FROM` in correct FK order.
- **Liquibase changelog format** — Use YAML master changelog pointing to SQL changeset files for simplicity. Avoid XML verbosity.
- **DevServices port conflicts** — Quarkus DevServices auto-assigns ports. No hardcoding needed for test profile; only dev profile pins port 5678 for convenience.
- **`SqliteSchemaInitializer` callers** — `AbstractApiResourceTest` and `AbstractCacheJobIntegrationTest` both inject and call `resetSchema()`. Must update both before deleting the class.
- **No data migration** — This is a greenfield swap. The SQLite `.db` file is ephemeral (rebuilt from GitHub on each warmup). No data migration needed.

## Files

### Deleted (2 files)
- `src/main/java/com/fvd/indexs/stores/SqliteSchemaInitializer.java`
- `src/test/java/com/fvd/common/TestSqliteHelper.java`

### New (2–3 files)
- `src/main/resources/db/postgres.yaml` — Liquibase master changelog
- `src/main/resources/db/scripts/001-init-schema.sql` — PostgreSQL DDL changeset (replaces old `init.sql`)
- `src/main/resources/db/postgres-test.yaml` — Test changelog (if distinct from prod)

### Modified (8+ files)
- `build.gradle` — dependency swap
- `src/main/resources/application.properties` — datasource + Liquibase config
- `src/main/java/com/fvd/indexs/stores/AbstractVersionedStore.java` — Javadoc update
- `src/test/java/com/fvd/indexs/stores/KeywordIndexStoreTest.java` — `@QuarkusTest` conversion
- `src/test/java/com/fvd/indexs/stores/CodeSampleIndexStoreTest.java` — `@QuarkusTest` conversion
- `src/test/java/com/fvd/indexs/stores/DocumentMetadataStoreTest.java` — `@QuarkusTest` conversion
- `src/test/java/com/fvd/indexs/stores/IndexStoreTest.java` — `@QuarkusTest` conversion
- `src/test/java/com/fvd/api/resources/AbstractApiResourceTest.java` — TRUNCATE cleanup
- `src/test/java/com/fvd/cache/jobs/AbstractCacheJobIntegrationTest.java` — TRUNCATE cleanup
- `src/test/java/com/fvd/search/services/SqliteSearchScorerTest.java` → `SearchScorerTest.java` — rename
