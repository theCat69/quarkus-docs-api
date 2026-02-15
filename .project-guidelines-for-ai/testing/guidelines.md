# Testing Guidelines

## Philosophy

- This project uses TDD. Tests should be written or updated before implementation.
- Prefer tight feedback loops -- run targeted tests frequently.
- Only run JVM tests. Do not run native tests (`./gradlew testNative`) unless explicitly requested.

## Test Commands

| Task | Command |
|------|---------|
| All tests | `./gradlew test` |
| Single class | `./gradlew test --tests "com.fvd.api.resources.DocumentResourceTest"` |
| Single method | `./gradlew test --tests "com.fvd.api.resources.DocumentResourceTest.testMethod"` |

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
