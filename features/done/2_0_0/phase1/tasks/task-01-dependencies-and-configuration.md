# Task 01: Dependencies & Configuration

> **Dependencies**: None — this is the first task in Phase 1.

## Summary

Swap the build dependencies and application configuration from SQLite to PostgreSQL. Remove the SQLite JDBC driver, enable the PostgreSQL driver and Liquibase extension, and update `application.properties` so the app targets PostgreSQL with Liquibase-managed schema migrations. Create the test-profile Liquibase changelog file. The `%test` profile relies on Quarkus DevServices (auto-starts a PostgreSQL container, no explicit JDBC URL needed).

## Changes

### `build.gradle`

- **Remove** `implementation 'io.quarkiverse.jdbc:quarkus-jdbc-sqlite:3.0.11'`
- **Uncomment** `implementation 'io.quarkus:quarkus-jdbc-postgresql'`
- **Uncomment** `implementation 'io.quarkus:quarkus-liquibase'`

### `src/main/resources/application.properties`

- **Remove** the SQLite datasource block (lines 28-31): `quarkus.datasource.db-kind=sqlite`, `jdbc:sqlite:...`, `min-size`, `max-size`
- **Uncomment** the PostgreSQL datasource block (lines 2-7): `db-kind=postgresql`, `username`, `password`, `max-size`, `devservices`
- **Uncomment** the Liquibase block (lines 10-12): `change-log`, `migrate-at-start=true`, test changelog
- **Remove** the test SQLite URL (line 121): `%test.quarkus.datasource.jdbc.url=jdbc:sqlite:build/test-cache/index.db`

### Create `src/main/resources/db/postgres-test.yaml`

Create a Liquibase master changelog for the `%test` profile. It mirrors `db/postgres.yaml` but is a separate file so test-specific migrations can be added later. It must include the same `scripts/001-init-schema.sql` changeset used by the main changelog.

After this task, the `%test` profile has no explicit JDBC URL. Quarkus DevServices auto-provisions a PostgreSQL container for dev and test modes.

## Acceptance Criteria

- [ ] `build.gradle` contains no SQLite dependency
- [ ] `build.gradle` includes `quarkus-jdbc-postgresql` and `quarkus-liquibase` as active dependencies
- [ ] `application.properties` specifies `quarkus.datasource.db-kind=postgresql`
- [ ] No `jdbc:sqlite` reference exists in any properties file
- [ ] Liquibase is configured with `migrate-at-start=true`
- [ ] `src/main/resources/db/postgres-test.yaml` exists, is valid YAML, and references `scripts/001-init-schema.sql`
- [ ] `./gradlew compileJava` succeeds (full startup depends on later tasks)

## Files Modified

- `build.gradle`
- `src/main/resources/application.properties`
- `src/main/resources/db/postgres-test.yaml` *(created)*
