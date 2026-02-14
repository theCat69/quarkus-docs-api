# Feature 52: Consolidate SearchServiceTest

> **Dependencies**: None. This is a test-only refactoring with no production code changes.

## Summary

Reduce redundant test methods in `SearchServiceTest.java` (the project's largest test file at 1278 lines / ~61 test methods) by removing duplicate extension-filtering tests that repeat the same `FilterUtils.matchesFilter` assertion across three entity types, merging null/blank extension tests that exercise the same code branch, and merging null/empty `filePaths` tests that verify identical behavior. Net reduction: ~8 test methods.

## User Story

As a **developer**, I want `SearchServiceTest` to be free of duplicated test logic so that I can understand what each test uniquely verifies and reduce test maintenance burden.

## Motivation

1. **Extension filtering duplication** (11 → 5 tests): The `ExtensionFilteringTests` nested class has 11 tests. Three scenarios are copy-pasted across all three entity types (files, sections, code samples), but all three delegate to the **same** `FilterUtils.matchesFilter` utility. The file-level tests fully exercise this utility; section and code-sample copies add no coverage.

2. **Null vs. blank extension** (2 → 1 test): `searchFilesWithNullExtensionReturnsAllFiles` and `searchFilesWithBlankExtensionReturnsAllFiles` both exercise the same `filter == null || filter.isBlank()` branch.

3. **Null vs. empty filePaths** (2 → 1 test): `searchSectionsSearchesAllFilesWhenFilePathsIsNull` and `searchSectionsSearchesAllFilesWhenFilePathsIsEmpty` test the same `filePaths == null || filePaths.isEmpty()` branch.

---

## Requirements

### R1: Remove Redundant Section Extension-Filtering Tests

**DELETE** these 3 tests from `ExtensionFilteringTests`:

- `searchSectionsWithExtensionFilterReturnsOnlyMatchingSections` (line 1157)
- `searchSectionsWithNullExtensionReturnsAllSections` (line 1179)
- `searchSectionsWithNonexistentExtensionReturnsEmpty` (line 1200)

### R2: Remove Redundant Code Sample Extension-Filtering Tests

**DELETE** these 3 tests from `ExtensionFilteringTests`:

- `searchCodeSamplesWithExtensionFilterReturnsOnlyMatchingSamples` (line 1219)
- `searchCodeSamplesWithNullExtensionReturnsAllSamples` (line 1237)
- `searchCodeSamplesWithNonexistentExtensionReturnsEmpty` (line 1254)

**Add Javadoc to `ExtensionFilteringTests`:**
```java
/**
 * Extension filtering is tested at the file level only. All three search methods
 * (searchFiles, searchSections, searchCodeSamples) delegate to FilterUtils.matchesFilter,
 * so file-level tests provide full coverage of the filtering logic.
 */
```

### R3: Merge Null and Blank Extension Tests into Parameterized Test

**MERGE** these 2 tests:
- `searchFilesWithNullExtensionReturnsAllFiles` (line 1087)
- `searchFilesWithBlankExtensionReturnsAllFiles` (line 1104)

**INTO:**

```java
@ParameterizedTest(name = "extension={0} returns all files")
@NullSource
@ValueSource(strings = {"", "  "})
void searchFilesWithNullOrBlankExtensionReturnsAllFiles(String extension) {
    // single test body using 'extension' parameter
}
```

### R4: Merge Null and Empty filePaths Tests into Parameterized Test

**MERGE** these 2 tests:
- `searchSectionsSearchesAllFilesWhenFilePathsIsNull` (line 502)
- `searchSectionsSearchesAllFilesWhenFilePathsIsEmpty` (line 524)

**INTO:**

```java
@ParameterizedTest(name = "filePaths={0} searches all files")
@MethodSource("nullAndEmptyFilePaths")
void searchSectionsSearchesAllFilesWhenFilePathsIsNullOrEmpty(List<String> filePaths) {
    // single test body using 'filePaths' parameter
}

static Stream<Arguments> nullAndEmptyFilePaths() {
    return Stream.of(
        Arguments.of((List<String>) null),
        Arguments.of(List.of())
    );
}
```

### R5: Add Required Imports

```java
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import java.util.stream.Stream;
```

---

## Implementation Notes

### Conservative Approach — What We DO NOT Merge

| Tests | Decision | Rationale |
|-------|----------|-----------|
| File pagination vs. section pagination | **KEEP BOTH** | Different methods, different iteration logic |
| File "returns empty" vs. section "returns empty" | **KEEP BOTH** | Different code paths |
| File prefix match vs. section/code-sample prefix match | **KEEP ALL** | Each validates specific score discounts per entity type |
| File stemming vs. section stemming | **KEEP BOTH** | Different search APIs |

### Why Extension Filtering Is Safe to Deduplicate

All three search methods call `FilterUtils.matchesFilter(extension, entry.extension)`. `FilterUtils.matchesFilter` is a **stateless, 1-line utility** (`return filter == null || filter.isBlank() || filter.equals(value)`). The file-level tests exercise every branch.

---

## Tasks

- [x] Delete 6 redundant extension-filtering tests (3 section + 3 code sample)
- [x] Add Javadoc to `ExtensionFilteringTests` explaining dedup rationale
- [x] Merge null + blank extension tests into parameterized test
- [x] Merge null + empty filePaths tests into parameterized test
- [x] Add parameterized test imports
- [x] Run `./gradlew test` — all tests pass
- [x] Verify test count decreased by ~8 methods

---

## Acceptance Criteria

1. `SearchServiceTest.java` contains ~55 test methods (was ~61)
2. No section-level extension-filtering tests exist
3. No code-sample-level extension-filtering tests exist
4. `searchFilesWithNullOrBlankExtensionReturnsAllFiles` is a `@ParameterizedTest`
5. `searchSectionsSearchesAllFilesWhenFilePathsIsNullOrEmpty` is a `@ParameterizedTest`
6. `ExtensionFilteringTests` has a Javadoc explaining dedup rationale
7. `./gradlew test` passes with zero failures
8. No production code is modified
9. Line count reduced by ~100–130 lines

---

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Future divergence: extension filtering may become entity-specific | Low | Medium | Javadoc documents the assumption; re-add tests if filtering logic diverges |
| `junit-jupiter-params` not on classpath | Very Low | Medium | Transitively included via `quarkus-junit5` |
| Over-consolidation: merging tests covering different code paths | Low | High | Spec is conservative; only proven-identical code branches are merged |

---

## Estimated Effort

| Task | Hours |
|------|-------|
| Delete + merge tests | 0.5 |
| Verify and run suite | 0.25 |
| **Total** | **~45 minutes** |

---

END OF FILE
