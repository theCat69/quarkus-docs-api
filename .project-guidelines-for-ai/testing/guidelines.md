# Testing Guidelines

## Project Context

Quarkus REST API (Java 21, Gradle wrapper) that caches and indexes Quarkus documentation from GitHub. SQLite-backed indexes. This project follows TDD.

## Philosophy

- This project uses TDD. Tests should be written or updated before implementation.
- Prefer tight feedback loops -- run targeted tests frequently.
- Only run JVM tests. Do not run native tests (`./gradlew testNative`) unless explicitly requested.

## Test Commands

| Task | Command |
|------|---------|
| All tests | `./gradlew test` |
| Single class | `./gradlew test --tests "com.fvd.search.resources.SearchResourceTest"` |
| Single method | `./gradlew test --tests "com.fvd.search.resources.SearchResourceTest.testMethod"` |

## Test Profile

The `%test` profile is automatically active when running tests. It configures:

- Cache directory: `build/test-cache` (isolated from runtime).
- SQLite database: `build/test-cache/index.db`.
- REST clients pointed at WireMock (no real GitHub API calls).
- Scheduler disabled.
- Quarkiverse ingestion disabled.
- Minimum keyword score lowered to `1`.

## What to Report

When running tests, report:
- Total tests run, passed, failed, skipped.
- For failures: the test class, method name, and failure message.
- If the build itself fails (compilation error), report the compilation error.

## Do Not

- Do not run native tests unless explicitly requested.
- Do not modify test code -- that is the coding agent's responsibility.
- Do not modify `application.properties` test profile settings unless explicitly requested.
