# Quarkus Docs API v1.1.2 Feature Specifications

## Overview

Version 1.1.2 focuses on **code consolidation and refactoring** to prepare the codebase for the PostgreSQL migration in v1.2.0. This release contains no breaking API changes — all refactoring is internal.

## Release Summary

| Aspect | Description |
|--------|-------------|
| Version | 1.1.2 |
| Type | Internal refactoring (no API changes) |
| Goal | Eliminate code duplication, prepare for PostgreSQL FTS |
| Database | SQLite (unchanged) |
| Breaking Changes | None |

## Features

### Repository Layer

| Feature | File | Priority | Description |
|---------|------|----------|-------------|
| [Feature 43: Repository Transaction Template](feature-43-repository-transaction-template.md) | `feature-43-*.md` | HIGH | Extract duplicated transaction handling into `TransactionTemplate` and `SqlUtils` utilities |

### Search & Indexing

| Feature | File | Priority | Description |
|---------|------|----------|-------------|
| [Feature 44: Search Scorer Abstraction](feature-44-search-scorer-abstraction.md) | `feature-44-*.md` | HIGH | Create `SearchScorer` interface for PostgreSQL FTS compatibility |

### API Layer

| Feature | File | Priority | Description |
|---------|------|----------|-------------|
| [Feature 45: API Layer Consolidation](feature-45-api-layer-consolidation.md) | `feature-45-*.md` | MEDIUM | Consolidate DTOs, validation, and utility patterns |

### Common Utilities

| Feature | File | Priority | Description |
|---------|------|----------|-------------|
| [Feature 46: Common Utilities Cleanup](feature-46-common-utilities-cleanup.md) | `feature-46-*.md` | MEDIUM | Abstract exception mappers, extract file/zip utilities |

## Duplication Eliminated

| Area | Before | After | Savings |
|------|--------|-------|---------|
| Transaction handling | 3 repos × 15 lines | 1 utility class | ~30 lines |
| exists()/delete() patterns | 3 repos × 12 lines | 1 utility class | ~24 lines |
| Store write patterns | 3 stores × 20 lines | 1 abstract base | ~40 lines |
| Exception mappers | 4 mappers × 20 lines | 1 abstract base | ~60 lines |
| Document title extraction | 3 services × 10 lines | 1 utility class | ~20 lines |
| Extension path mapping | 2 jobs × 20 lines | 1 utility class | ~20 lines |
| **Total** | | | **~194 lines** |

## Implementation Order

The features should be implemented in this order due to dependencies:

```
1. Feature 43: Repository Transaction Template
   └── No dependencies

2. Feature 46: Common Utilities Cleanup
   └── No dependencies (can parallel with 43)

3. Feature 44: Search Scorer Abstraction
   └── Depends on: Feature 43 (TransactionTemplate)

4. Feature 45: API Layer Consolidation
   └── Depends on: Feature 44 (SearchKeywords utility)
```

## PostgreSQL Migration Impact

This refactoring prepares for v1.2.0 PostgreSQL migration:

| Component | v1.1.2 Preparation | v1.2.0 Migration |
|-----------|-------------------|------------------|
| `SearchScorer` interface | Created | Add `PostgresSearchScorer` implementation |
| `TransactionTemplate` | SQLite-specific | Create PostgreSQL-specific version |
| Repository interfaces | Already abstracted | Add PostgreSQL implementations |
| Custom keyword matching | Isolated in `SqliteSearchScorer` | Replace with `ts_rank()` |

## Testing Strategy

- All existing tests must pass without modification
- New unit tests for all utility classes (>90% coverage target)
- No integration test changes required
- Run `./gradlew test` after each feature

## Dependencies

- No new external dependencies
- Uses existing: Lombok, Jakarta RS, Quarkus ARC

## Estimated Effort

| Feature | Hours |
|---------|-------|
| Feature 43 | 4-6 |
| Feature 44 | 6-8 |
| Feature 45 | 4-6 |
| Feature 46 | 6-9 |
| **Total** | **20-29 hours** |

---

END OF FILE
