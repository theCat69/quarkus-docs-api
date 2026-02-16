# Task 06: Full Validation & Cleanup

> **Dependencies**: Tasks 01–05 must all be complete before starting this task.

## Summary

Final gate for Phase 1. Run the full test suite and a dev-mode smoke test against DevServices PostgreSQL, then audit the entire codebase — including `.project-guidelines-for-ai/` — for leftover SQLite references and update all project guideline files to reflect the new PostgreSQL engine.

## Checklist

- Run `./gradlew test` — all tests must pass with zero failures against DevServices PostgreSQL.
- Run `./gradlew quarkusDev` and confirm: DevServices starts a PostgreSQL container, Liquibase migration succeeds (look for `Successfully acquired change log lock` and table-creation logs), the application responds to requests, and cache warmup populates the database.
- Case-insensitive grep for `sqlite`, `PRAGMA`, `AUTOINCREMENT` across all Java, properties, YAML, and Markdown files — **including `.project-guidelines-for-ai/**`**. Remove or rewrite every match.
- Update `.project-guidelines-for-ai/coding/guidelines.md` — change "SQLite-backed indexes" to "PostgreSQL-backed indexes".
- Update `.project-guidelines-for-ai/testing/guidelines.md` — change references to "SQLite database" / `%test` SQLite file to "PostgreSQL database (DevServices)".
- Update `.project-guidelines-for-ai/documentation/guidelines.md` — replace any SQLite references with PostgreSQL.
- Update `.project-guidelines-for-ai/security/guidelines.md` — replace any SQLite references with PostgreSQL.
- Update `.project-guidelines-for-ai/building/guidelines.md` — replace any SQLite references with PostgreSQL.
- Update `AGENTS.md` project summary — replace any "SQLite" mention with "PostgreSQL".
- Remove stale comments, Javadoc, or log messages that still reference SQLite.
- Verify `SqliteSearchScorer.java` → `SearchScorer.java` rename (Task 04) is complete and no references to old name remain.

## Acceptance Criteria

- [ ] `./gradlew test` — zero failures
- [ ] `./gradlew quarkusDev` — application starts with DevServices PostgreSQL and Liquibase migration
- [ ] No occurrence of `sqlite`, `PRAGMA`, or `AUTOINCREMENT` in any source or resource file (case-insensitive)
- [ ] `.project-guidelines-for-ai/coding/guidelines.md` updated
- [ ] `.project-guidelines-for-ai/testing/guidelines.md` updated
- [ ] `.project-guidelines-for-ai/documentation/guidelines.md` updated
- [ ] `.project-guidelines-for-ai/security/guidelines.md` updated
- [ ] `.project-guidelines-for-ai/building/guidelines.md` updated
- [ ] `AGENTS.md` project summary updated
- [ ] All 6 guideline/config files (5 guideline files + `AGENTS.md`) are free of SQLite references

## Files Modified

- `.project-guidelines-for-ai/coding/guidelines.md`
- `.project-guidelines-for-ai/testing/guidelines.md`
- `.project-guidelines-for-ai/documentation/guidelines.md`
- `.project-guidelines-for-ai/security/guidelines.md`
- `.project-guidelines-for-ai/building/guidelines.md`
- `AGENTS.md`
- Any remaining files containing SQLite references
