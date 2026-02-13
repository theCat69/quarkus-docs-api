# Quarkus Docs API v1.1.3 Feature Specifications

## Overview

Version 1.1.3 focuses on **full cleanup of unused production code**. The `repository/` package — originally built as an abstraction layer for a planned PostgreSQL migration — was superseded by the `indexs/stores/` classes and is now dead code. This release removes the entire package (30 classes), relocates the single actively-used class (`MatchedKeyword`), and deletes additional unused services and dead methods elsewhere in the codebase.

## Release Summary

| Aspect | Description |
|--------|-------------|
| Version | 1.1.3 |
| Type | Dead code removal (no API changes) |
| Goal | Remove ~2,500 lines of unused production code and ~500 lines of orphaned tests |
| Database | SQLite (unchanged) |
| Breaking Changes | None (all changes are internal) |

## Features

### Dead Code Removal

| Feature | File | Priority | Description |
|---------|------|----------|-------------|
| [Feature 47: Remove `repository/` Package & Relocate `MatchedKeyword`](feature-47-remove-repository-package.md) | `feature-47-*.md` | HIGH | Delete all 30 classes in `repository/`, relocate `MatchedKeyword` to `search.services`, remove 3 orphaned test classes |
| [Feature 48: Remove Unused Services & Dead Methods](feature-48-remove-unused-services-dead-methods.md) | `feature-48-*.md` | MEDIUM | Delete `DocService`, `ZipStreamProcessor`, their tests, and 4 dead private methods in `KeywordIndexer` |

## Code Removed

| Area | Files Deleted | Lines Removed (est.) |
|------|--------------|---------------------|
| `repository/api/` | 5 interfaces | ~120 |
| `repository/domain/` | 14 domain classes (MatchedKeyword relocated) | ~350 |
| `repository/sqlite/` | 9 implementation classes | ~1,400 |
| `repository/exceptions/` | 1 exception class | ~20 |
| Repository test classes | 3 test classes | ~500 |
| `DocService` + test | 2 files | ~130 |
| `ZipStreamProcessor` + test | 2 files | ~200 |
| Dead methods in `KeywordIndexer` | 4 private methods | ~30 |
| **Total** | **36 files deleted, ~10 modified** | **~2,750 lines** |

## Implementation Order

The features should be implemented in this order due to dependencies:

```
1. Feature 47: Remove repository/ Package & Relocate MatchedKeyword
   └── No dependencies
   └── MUST be completed first (Feature 48 runs full test suite which depends on clean CDI context)

2. Feature 48: Remove Unused Services & Dead Methods
   └── Depends on: Feature 47 (clean CDI context, all tests passing)
```

> **Important:** Feature 47 must be fully completed and verified before starting Feature 48. The CDI bean removal in Feature 47 could mask or cause test failures that would complicate debugging if both features are in progress simultaneously.

## Configuration Cleanup

| Property | File | Action |
|----------|------|--------|
| `app.database.type=sqlite` | `application.properties` | **Remove** — no longer referenced by any `@LookupIfProperty` |

## Testing Strategy

- All existing tests must pass after each feature (minus deleted test files)
- No new tests are required — this is pure deletion
- Run `./gradlew test` after each feature
- Verify Quarkus application starts without CDI resolution errors
- Verify no `UnsatisfiedResolutionException` at startup

## Dependencies

- No new external dependencies
- No dependency changes

## Estimated Effort

| Feature | Hours |
|---------|-------|
| Feature 47 | 2-4 |
| Feature 48 | 1-2 |
| **Total** | **3-6 hours** |

---

END OF FILE
