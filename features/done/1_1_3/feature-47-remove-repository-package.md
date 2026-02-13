# Feature 47: Remove `repository/` Package & Relocate `MatchedKeyword`

> **Dependencies**: None. This is the first feature to implement in v1.1.3.

## Summary

Delete the entire `com.fvd.repository` package (30 classes across 4 subpackages) which is unused by active production code. The package was built as an abstraction layer for a planned PostgreSQL migration but was superseded by the `indexs/stores/` classes. The only class from the package still used by active production code — `MatchedKeyword` — must be relocated to `com.fvd.search.services` before deletion.

## User Story

As a **developer**, I want to remove the unused `repository/` package so that:
- The codebase contains only code that is actually used in production
- New contributors are not confused by an entire package of dead code
- CDI context is clean — no unused `@ApplicationScoped` beans consuming startup time
- The false impression that a repository abstraction layer is needed is eliminated
- Build times are marginally improved by compiling fewer classes

## Motivation

The `repository/` package contains **30 Java classes** across 4 subpackages:

| Subpackage | Files | Purpose (original) | Current Status |
|------------|-------|-------------------|----------------|
| `repository/api/` | 5 | Repository interfaces | **Unused** — no production code injects these interfaces |
| `repository/domain/` | 15 | Domain models | **14 unused**, 1 used (`MatchedKeyword`) |
| `repository/sqlite/` | 9 | SQLite implementations | **Unused** — `indexs/stores/` handles all persistence |
| `repository/exceptions/` | 1 | `RepositoryException` | **Unused** outside the repository package itself |

This dead code:
- Adds ~1,900 lines of production code that serves no purpose
- Registers 5 `@ApplicationScoped` CDI beans that are never injected
- Includes a duplicate `SqliteSchemaInitializerImpl` with `@Observes StartupEvent` that runs schema creation unnecessarily (the active `indexs.stores.SqliteSchemaInitializer` already handles this)
- Contains `@LookupIfProperty` annotations referencing `app.database.type` — a config property that can be removed once these beans are gone
- Has 3 associated test files (~500 lines) that test unused code

### Evidence: No Injection Points

A grep for all `repository/` imports in production code outside the `repository/` package itself reveals that **only `MatchedKeyword`** is used:

```
src/main/java/com/fvd/api/services/DocumentService.java      → import MatchedKeyword
src/main/java/com/fvd/api/services/CodeSampleService.java     → import MatchedKeyword
src/main/java/com/fvd/api/services/QuickSearchService.java    → import MatchedKeyword
src/main/java/com/fvd/search/services/SqliteSearchScorer.java → import MatchedKeyword
src/main/java/com/fvd/search/services/SearchScorer.java       → import MatchedKeyword
src/main/java/com/fvd/search/services/CodeSampleSearchResult.java → import MatchedKeyword
src/main/java/com/fvd/search/services/SectionSearchResult.java    → import MatchedKeyword
src/main/java/com/fvd/search/services/FileSearchResult.java       → import MatchedKeyword
```

No production file imports `SearchRepository`, `KeywordIndexRepository`, `CodeSampleIndexRepository`, `GithubIndexRepository`, `SchemaInitializer`, `RepositoryException`, or any other `repository/` class.

---

## Requirements

### 1. Relocate `MatchedKeyword` to `com.fvd.search.services`

Before deleting the `repository/` package, move `MatchedKeyword` to its new home.

**Current location:** `src/main/java/com/fvd/repository/domain/MatchedKeyword.java`
**New location:** `src/main/java/com/fvd/search/services/MatchedKeyword.java`

**Rationale for `search.services`:**
- `MatchedKeyword` is a search result DTO — it belongs with other search result types
- The majority of its consumers are in `search.services` (`SearchScorer`, `SqliteSearchScorer`, `FileSearchResult`, `SectionSearchResult`, `CodeSampleSearchResult`)
- The `api.services` consumers (`DocumentService`, `CodeSampleService`, `QuickSearchService`) import it to map search results — a cross-package import is acceptable
- It lives alongside `FileSearchResult`, `SectionSearchResult`, `CodeSampleSearchResult` which all reference it

**The file content is unchanged** — only the `package` statement changes:

```java
// BEFORE
package com.fvd.repository.domain;

// AFTER
package com.fvd.search.services;
```

### 2. Update All Import Statements

Update every file that imports `com.fvd.repository.domain.MatchedKeyword` to use the new package.

**Production files to update (8 files):**

| File | Package |
|------|---------|
| `DocumentService.java` | `com.fvd.api.services` |
| `CodeSampleService.java` | `com.fvd.api.services` |
| `QuickSearchService.java` | `com.fvd.api.services` |
| `SqliteSearchScorer.java` | `com.fvd.search.services` |
| `SearchScorer.java` | `com.fvd.search.services` |
| `CodeSampleSearchResult.java` | `com.fvd.search.services` |
| `SectionSearchResult.java` | `com.fvd.search.services` |
| `FileSearchResult.java` | `com.fvd.search.services` |

**Test files to update (2 files):**

| File | Package |
|------|---------|
| `SqliteSearchScorerTest.java` | `com.fvd.search.services` |
| `SearchServiceTest.java` | `com.fvd.search.services` |

> **Note:** For files in `com.fvd.search.services` (same package as the relocated class), the import statement can be removed entirely since `MatchedKeyword` will be in the same package.

### 3. Delete All `repository/` Package Files

Delete the entire directory tree: `src/main/java/com/fvd/repository/`

**Files to delete — `repository/api/` (5 files):**

| File | Type | CDI Bean |
|------|------|----------|
| `SearchRepository.java` | Interface | No |
| `CodeSampleIndexRepository.java` | Interface | No |
| `GithubIndexRepository.java` | Interface | No |
| `KeywordIndexRepository.java` | Interface | No |
| `SchemaInitializer.java` | Interface | No |

**Files to delete — `repository/domain/` (14 files, MatchedKeyword already relocated):**

| File | Type |
|------|------|
| `CodeSampleEntry.java` | Record/DTO |
| `CodeSampleIndexData.java` | Record/DTO |
| `CodeSampleMatch.java` | Record/DTO |
| `CodeSampleSearchQuery.java` | Record/DTO |
| `FileEntry.java` | Record/DTO |
| `FileMatch.java` | Record/DTO |
| `FileSearchQuery.java` | Record/DTO |
| `GithubFileEntry.java` | Record/DTO |
| `KeywordIndexData.java` | Record/DTO |
| `KeywordWeight.java` | Record/DTO |
| `SearchResult.java` | Record/DTO |
| `SectionEntry.java` | Record/DTO |
| `SectionMatch.java` | Record/DTO |
| `SectionSearchQuery.java` | Record/DTO |

**Files to delete — `repository/sqlite/` (9 files):**

| File | Type | CDI Bean | `@LookupIfProperty` |
|------|------|----------|---------------------|
| `SqliteSchemaInitializerImpl.java` | Class | `@ApplicationScoped` | Yes |
| `SqliteSearchRepository.java` | Class | `@ApplicationScoped` | Yes |
| `SqliteCodeSampleIndexRepository.java` | Class | `@ApplicationScoped` | Yes |
| `SqliteGithubIndexRepository.java` | Class | `@ApplicationScoped` | Yes |
| `SqliteKeywordIndexRepository.java` | Class | `@ApplicationScoped` | Yes |
| `TransactionTemplate.java` | Utility | No | No |
| `TransactionalOperation.java` | Interface | No | No |
| `VoidTransactionalOperation.java` | Interface | No | No |
| `SqlUtils.java` | Utility | No | No |

**Files to delete — `repository/exceptions/` (1 file):**

| File | Type |
|------|------|
| `RepositoryException.java` | Exception class |

### 4. Delete All Repository Test Files

Delete the entire directory: `src/test/java/com/fvd/repository/`

**Test files to delete (3 files):**

| File | Tests For |
|------|-----------|
| `SqliteKeywordIndexRepositoryTest.java` | `SqliteKeywordIndexRepository` (being deleted) |
| `TransactionTemplateTest.java` | `TransactionTemplate` (being deleted) |
| `SqlUtilsTest.java` | `SqlUtils` (being deleted) |

### 5. Clean Up `application.properties`

Remove the `app.database.type` config property since no `@LookupIfProperty` annotations will reference it after the repository beans are deleted:

```properties
# REMOVE these lines:
# Database type (sqlite for now, postgresql planned for v1.2.0)
app.database.type=sqlite
```

### 6. Verify No Duplicate Schema Initialization

Confirm that the active `SqliteSchemaInitializer` in `indexs.stores` handles all schema initialization:

- **Active:** `com.fvd.indexs.stores.SqliteSchemaInitializer` — `@ApplicationScoped`, `@Observes @Priority(100) StartupEvent`
- **Being deleted:** `com.fvd.repository.sqlite.SqliteSchemaInitializerImpl` — `@ApplicationScoped`, `@LookupIfProperty`, `@Observes @Priority(100) StartupEvent`

Both create the exact same tables. After deletion, only the active one remains. No schema gap.

---

## Implementation Notes

### Relocation Target Package

`MatchedKeyword` is placed in `com.fvd.search.services` because:
1. It is a search result component used by `SearchScorer`, `FileSearchResult`, `SectionSearchResult`, `CodeSampleSearchResult`
2. Five of its eight production consumers are already in `search.services` — they won't need an import at all
3. The three `api.services` consumers have a natural dependency on `search.services` already

### CDI Impact

Removing 5 `@ApplicationScoped` beans that are never injected. Since all 5 use `@LookupIfProperty(lookupIfMissing = true)`, Quarkus ARC registers them conditionally. However, because no code ever performs a lookup on their interfaces (`SearchRepository`, `CodeSampleIndexRepository`, etc.), removing them has zero runtime impact.

### Duplicate `@Observes StartupEvent`

Currently **two** beans observe `StartupEvent` at `@Priority(100)` for schema init:
1. `repository.sqlite.SqliteSchemaInitializerImpl` (being deleted)
2. `indexs.stores.SqliteSchemaInitializer` (remains)

Both execute identical DDL. After deletion, the schema is still initialized correctly by the remaining bean.

### No Gradle/Build Changes

No changes to `build.gradle` are needed — there are no repository-specific dependencies.

---

## Tasks

- [x] Relocate `MatchedKeyword.java` to `com.fvd.search.services` package (change `package` statement)
- [x] Update imports in `DocumentService.java` (`com.fvd.api.services`)
- [x] Update imports in `CodeSampleService.java` (`com.fvd.api.services`)
- [x] Update imports in `QuickSearchService.java` (`com.fvd.api.services`)
- [x] Remove import in `SqliteSearchScorer.java` (same package now)
- [x] Remove import in `SearchScorer.java` (same package now)
- [x] Remove import in `CodeSampleSearchResult.java` (same package now)
- [x] Remove import in `SectionSearchResult.java` (same package now)
- [x] Remove import in `FileSearchResult.java` (same package now)
- [x] Update imports in `SqliteSearchScorerTest.java`
- [x] Update imports in `SearchServiceTest.java`
- [x] Delete `src/main/java/com/fvd/repository/api/` (5 files)
- [x] Delete `src/main/java/com/fvd/repository/domain/` (14 remaining files)
- [x] Delete `src/main/java/com/fvd/repository/sqlite/` (9 files)
- [x] Delete `src/main/java/com/fvd/repository/exceptions/` (1 file)
- [x] Delete `src/test/java/com/fvd/repository/` (3 test files)
- [x] Remove `app.database.type=sqlite` and its comment from `application.properties`
- [x] Run `./gradlew test` — all tests must pass
- [x] Verify application starts cleanly (`./gradlew quarkusDev` — no CDI errors)

---

## Acceptance Criteria

1. The entire `src/main/java/com/fvd/repository/` directory is deleted (30 files)
2. The entire `src/test/java/com/fvd/repository/` directory is deleted (3 files)
3. `MatchedKeyword.java` exists at `src/main/java/com/fvd/search/services/MatchedKeyword.java` with `package com.fvd.search.services`
4. All 10 files that previously imported `com.fvd.repository.domain.MatchedKeyword` have updated or removed imports
5. `application.properties` no longer contains `app.database.type=sqlite`
6. `./gradlew test` passes with zero failures
7. Application starts without `UnsatisfiedResolutionException` or other CDI errors
8. No file in the codebase contains `import com.fvd.repository.` (verified by grep)

---

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Hidden `@Inject` of repository interfaces missed by grep | Very Low | High | Full-text grep for all `repository.` imports confirmed only `MatchedKeyword` used outside package |
| `SqliteSchemaInitializerImpl` removal breaks schema init | Very Low | Critical | Active `indexs.stores.SqliteSchemaInitializer` creates identical tables; verified by diffing both `createTables()` methods |
| `@LookupIfProperty` removal causes ARC warning/error | Very Low | Low | `lookupIfMissing = true` means beans are conditional; ARC won't complain about missing beans nobody looks up |
| `MatchedKeyword` relocation breaks serialization | Very Low | Medium | `@RegisterForReflection` annotation preserved; record structure unchanged; only package changes |
| `app.database.type` property removal breaks something | Low | Medium | Grep entire codebase for all references to this property; only `@LookupIfProperty` annotations use it |
| Test failures due to import changes | Low | Low | Mechanical find-and-replace; run full test suite |

---

## Estimated Effort

| Task | Hours |
|------|-------|
| Relocate `MatchedKeyword` + update 10 imports | 0.5 |
| Delete 30 production files + 3 test files | 0.5 |
| Clean `application.properties` | 0.25 |
| Verify build + tests + startup | 1-2 |
| **Total** | **2-4 hours** |

---

END OF FILE
