# Quarkus Docs API v1.1.4 Feature Specifications

## Overview

Version 1.1.4 focuses on **test duplication removal and test refactoring**. All 8 features are test-only changes — no production code is modified and no API changes are introduced. The release systematically eliminates copy-paste test methods via `@ParameterizedTest`, extracts shared test base classes, and creates reusable test utilities.

## Release Summary

| Aspect | Description |
|--------|-------------|
| Version | 1.1.4 |
| Type | Test refactoring (no API changes, no production code changes) |
| Goal | Eliminate test duplication, extract shared test infrastructure |
| Database | SQLite (unchanged) |
| Breaking Changes | None |

## Features

### Test Parameterization

| Feature | File | Priority | Description |
|---------|------|----------|-------------|
| [Feature 49: Parameterize SubjectDeriverTest](feature-49-parameterize-subject-deriver-test.md) | `feature-49-*.md` | MEDIUM | Replace 33 copy-paste test methods with 2 `@ParameterizedTest` methods (54 → 23 methods, ~280 lines saved) |
| [Feature 50: Parameterize StemmerTest](feature-50-parameterize-stemmer-test.md) | `feature-50-*.md` | MEDIUM | Replace 31 identical-pattern tests with 1 `@ParameterizedTest` + remove 1 duplicate (33 → 3 methods, ~140 lines saved) |
| [Feature 51: Consolidate KeywordScorerTest](feature-51-consolidate-keyword-scorer-test.md) | `feature-51-*.md` | MEDIUM | Replace 27 tests across 4 groups with 4 `@ParameterizedTest` methods (54 → 31 methods, ~120 lines saved) |

### Test Consolidation

| Feature | File | Priority | Description |
|---------|------|----------|-------------|
| [Feature 52: Consolidate SearchServiceTest](feature-52-consolidate-search-service-test.md) | `feature-52-*.md` | MEDIUM | Remove 6 redundant extension-filtering tests + merge null/blank/empty tests (~8 fewer methods, ~100 lines saved) |

### Test Base Class Extraction

| Feature | File | Priority | Description |
|---------|------|----------|-------------|
| [Feature 53: Extract API Resource Test Base](feature-53-extract-api-resource-test-base.md) | `feature-53-*.md` | HIGH | Extract `AbstractApiResourceTest` base class from 5 API resource test classes; remove 6 redundant error tests (~120 lines saved) |
| [Feature 54: Consolidate Cache Integration Tests](feature-54-consolidate-cache-integration-tests.md) | `feature-54-*.md` | HIGH | Extract `AbstractCacheJobIntegrationTest` base class; add `simulateWarmup()` helper (~150 lines saved) |

### Shared Test Utilities

| Feature | File | Priority | Description |
|---------|------|----------|-------------|
| [Feature 55: Extract Shared Test Utilities](feature-55-extract-shared-test-utilities.md) | `feature-55-*.md` | MEDIUM | Create `TestSqliteHelper` and `TestZipHelper` utility classes; deduplicate setup across 8+ test files (~80 lines saved) |
| [Feature 56: Consolidate Quarkiverse Test Setup](feature-56-consolidate-quarkiverse-test-setup.md) | `feature-56-*.md` | LOW | Extract `buildQuarkiverseIndexes()` and `stubPlaybook()` helpers within quarkiverse test files (~58 lines saved) |

## Impact Summary

| Metric | Before | After | Change |
|--------|--------|-------|--------|
| Test methods (approx.) | ~290 | ~240 | ~50 fewer methods |
| Test lines saved (est.) | — | — | **~950 lines** |
| New test files created | — | 3 | `AbstractApiResourceTest`, `AbstractCacheJobIntegrationTest`, `TestSqliteHelper`, `TestZipHelper` (4 files) |
| Test files modified | — | ~20 | Across all 8 features |
| Production code changes | — | 0 | None |
| Test execution count | ~290 | ~290 | Unchanged (parameterized tests preserve invocation count) |

## Implementation Order

The features have no inter-dependencies and can be implemented in any order:

```
Features 49–52: Test parameterization (independent)
   └── No dependencies between them

Feature 53: API Resource Test Base
   └── No dependencies

Feature 54: Cache Integration Test Base
   └── No dependencies

Feature 55: Shared Test Utilities
   └── No dependencies

Feature 56: Quarkiverse Test Setup
   └── No dependencies
```

## Testing Strategy

- All existing tests must pass after each feature (`./gradlew test`)
- Total test execution count must remain unchanged (parameterized tests generate the same number of invocations)
- No production code files are modified in any feature

## Dependencies

- No new external dependencies
- Uses existing: JUnit 5 `junit-jupiter-params` (transitive via `quarkus-junit5`), Lombok `@UtilityClass`

## Estimated Effort

| Feature | Hours |
|---------|-------|
| Feature 49 | ~1.5 |
| Feature 50 | ~1 |
| Feature 51 | ~1 |
| Feature 52 | ~0.75 |
| Feature 53 | ~2.25 |
| Feature 54 | ~1.25 |
| Feature 55 | ~2 |
| Feature 56 | ~0.75 |
| **Total** | **~10.5 hours** |

---

END OF FILE
