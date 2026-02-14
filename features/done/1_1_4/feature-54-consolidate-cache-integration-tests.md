# Feature 54: Consolidate Cache Job Integration Tests

> **Dependencies**: None. This is a test-only refactoring with no production code changes.

## Summary

Extract a shared `AbstractCacheJobIntegrationTest` base class to eliminate duplication between `CacheRefreshJobIntegrationTest` (171 lines) and `CacheWarmupJobIntegrationTest` (183 lines). Both files declare 8 identical `@Inject` fields, an identical `@BeforeEach cleanTestCache()` method, and the refresh test repeats a 4-line warmup simulation preamble 3 times. The base class consolidates the shared fields, setup, and warmup helper, while the refresh test strips redundant warmup assertions that already have full coverage in the warmup test.

## User Story

As a **developer**, I want the cache job integration tests to share a common base class so that:
- Adding a new shared dependency does not require updating two files
- The `cleanTestCache()` setup logic lives in one place
- Simulating warmup in refresh tests is a single method call, not a copy-pasted 4-line block
- Refresh tests focus exclusively on refresh behavior, not re-verifying warmup correctness

## Motivation

1. **8 identical `@Inject` fields** duplicated across both test classes (lines 30–52 in refresh, lines 29–51 in warmup): `ZipDownloadService`, `IndexService`, `KeywordIndexer`, `CodeSampleIndexer`, `DocStore`, `KeywordIndexStore`, `CodeSampleIndexStore`, `SqliteSchemaInitializer`.

2. **Identical `@BeforeEach cleanTestCache()` method** in both classes (lines 60–67 in refresh, lines 53–60 in warmup):
   ```java
   var cachePath = Path.of("build/test-cache").toFile();
   if (cachePath.exists()) { FileUtils.cleanDirectory(cachePath); }
   schemaInitializer.resetSchema();
   ```

3. **Warmup simulation preamble repeated 3 times** within `CacheRefreshJobIntegrationTest` (lines 72–77, 115–118, 150–153):
   ```java
   List<String> extractedFiles = zipDownloadService.streamAndExtract("3.27");
   indexService.getOrFetchIndex("3.27");
   keywordIndexer.build("3.27", extractedFiles);
   codeSampleIndexer.build("3.27", extractedFiles);
   ```

4. **Warmup assertions in refresh tests** (lines 73–90 in `refreshPreservesCodeSampleIndexAfterWarmup`) re-assert warmup behavior (`extractedFiles` content, warmup sample count, specific sample content) that is already fully covered by `CacheWarmupJobIntegrationTest.warmupExtractsDocsFromZipAndBuildsIndexes`.

### Counts

| Duplication | Occurrences | After |
|------------|-------------|-------|
| `@Inject` field declarations | 8 × 2 files = 16 | 8 in base class |
| `cleanTestCache()` method | 2 identical copies | 1 in base class |
| Warmup simulation preamble | 3 copies in refresh test | 1 helper method in base class |
| Warmup assertions in refresh tests | ~10 assertion lines | Removed (covered by warmup tests) |

---

## Requirements

### R1: Create `AbstractCacheJobIntegrationTest` Base Class

**Create** `src/test/java/com/fvd/cache/jobs/AbstractCacheJobIntegrationTest.java`

The base class must:
- Be `package com.fvd.cache.jobs`
- Be `abstract` — not annotated with `@QuarkusTest` (subclasses own that annotation)
- Contain all 8 shared `@Inject` fields:
  - `ZipDownloadService zipDownloadService`
  - `IndexService indexService`
  - `KeywordIndexer keywordIndexer`
  - `CodeSampleIndexer codeSampleIndexer`
  - `DocStore docStore`
  - `KeywordIndexStore keywordIndexStore`
  - `CodeSampleIndexStore codeSampleIndexStore`
  - `SqliteSchemaInitializer schemaInitializer`
- Contain the `@BeforeEach cleanTestCache()` method (unchanged logic)
- Contain a `protected` helper method `simulateWarmup(String version)` that returns `List<String>`:

```java
protected List<String> simulateWarmup(String version) {
    List<String> extractedFiles = zipDownloadService.streamAndExtract(version);
    indexService.getOrFetchIndex(version);
    keywordIndexer.build(version, extractedFiles);
    codeSampleIndexer.build(version, extractedFiles);
    return extractedFiles;
}
```

### R2: Refactor `CacheWarmupJobIntegrationTest` to Extend Base Class

**Modify** `CacheWarmupJobIntegrationTest.java`:

1. Extend `AbstractCacheJobIntegrationTest`
2. **Remove** all 8 `@Inject` fields that are now in the base class (lines 29–51)
3. **Remove** the `cleanTestCache()` method (lines 53–60)
4. Keep `@QuarkusTest` annotation on the subclass
5. All 3 existing test methods remain **unchanged** — they do not use `simulateWarmup()` because they assert intermediate warmup steps

### R3: Refactor `CacheRefreshJobIntegrationTest` to Extend Base Class

**Modify** `CacheRefreshJobIntegrationTest.java`:

1. Extend `AbstractCacheJobIntegrationTest`
2. **Remove** all 8 `@Inject` fields that are now in the base class (lines 30–52)
3. **Remove** the `cleanTestCache()` method (lines 60–67)
4. **Keep** the 2 subclass-specific `@Inject` fields:
   - `CacheRefreshJob cacheRefreshJob` (line 55)
   - `SearchService searchService` (line 58)
5. Keep `@QuarkusTest` annotation on the subclass

### R4: Replace Warmup Preamble in Refresh Tests with `simulateWarmup()`

**In `refreshPreservesCodeSampleIndexAfterWarmup()`** (lines 69–110):

Replace lines 72–77 (warmup preamble + assertion on extractedFiles):
```java
List<String> extractedFiles = zipDownloadService.streamAndExtract("3.27");
assertThat(extractedFiles).containsExactlyInAnyOrder("security-overview.adoc", "config.adoc");
indexService.getOrFetchIndex("3.27");
keywordIndexer.build("3.27", extractedFiles);
CodeSampleIndex warmupIndex = codeSampleIndexer.build("3.27", extractedFiles);
```

With:
```java
simulateWarmup("3.27");
```

**In `refreshPreservesKeywordIndexAfterWarmup()`** (lines 112–145):

Replace lines 115–118:
```java
List<String> extractedFiles = zipDownloadService.streamAndExtract("3.27");
indexService.getOrFetchIndex("3.27");
keywordIndexer.build("3.27", extractedFiles);
codeSampleIndexer.build("3.27", extractedFiles);
```

With:
```java
simulateWarmup("3.27");
```

**In `refreshedCodeSamplesAreSearchable()`** (lines 147–169):

Replace lines 150–153:
```java
List<String> extractedFiles = zipDownloadService.streamAndExtract("3.27");
indexService.getOrFetchIndex("3.27");
keywordIndexer.build("3.27", extractedFiles);
codeSampleIndexer.build("3.27", extractedFiles);
```

With:
```java
simulateWarmup("3.27");
```

### R5: Strip Redundant Warmup Assertions from Refresh Tests

**In `refreshPreservesCodeSampleIndexAfterWarmup()`**, remove these warmup-verification assertions (lines 73–90) that duplicate coverage already in `CacheWarmupJobIntegrationTest`:

```java
// DELETE — covered by warmupExtractsDocsFromZipAndBuildsIndexes
assertThat(extractedFiles).containsExactlyInAnyOrder("security-overview.adoc", "config.adoc");
assertThat(warmupIndex.samples).isNotEmpty();
Optional<CodeSampleIndex> storedAfterWarmup = codeSampleIndexStore.read("3.27");
assertThat(storedAfterWarmup).isPresent();
int warmupSampleCount = storedAfterWarmup.get().samples.size();
assertThat(warmupSampleCount).isGreaterThan(0);
assertThat(storedAfterWarmup.get().samples)
        .anyMatch(s -> s.filePath.equals("security-overview.adoc")
                && s.language.equals("java")
                && s.content.contains("SecurityIdentity"));
```

The refresh-specific assertions (lines 97–109) remain — they verify behavior **after** `refreshVersion()`.

**Note:** The `hasSizeGreaterThanOrEqualTo(warmupSampleCount)` assertion (line 103) should be replaced with `isNotEmpty()` since we no longer capture `warmupSampleCount`. The test's intent is: "refresh does not wipe out code samples."

**In `refreshPreservesKeywordIndexAfterWarmup()`**, similarly remove warmup-verification assertions (lines 120–128):

```java
// DELETE — covered by warmupExtractsDocsFromZipAndBuildsIndexes
Optional<KeywordIndex> storedAfterWarmup = keywordIndexStore.read("3.27");
assertThat(storedAfterWarmup).isPresent();
int warmupFileCount = storedAfterWarmup.get().files.size();
assertThat(warmupFileCount).isGreaterThan(0);
assertThat(storedAfterWarmup.get().files)
        .anyMatch(f -> f.path.equals("security-overview.adoc")
                && f.keywords.stream().anyMatch(k -> k.word.equals("secur")));
```

Replace `hasSizeGreaterThanOrEqualTo(warmupFileCount)` (line 139) with `isNotEmpty()`.

### R6: Update Imports

**Base class** needs:
```java
import com.fvd.docs.stores.DocStore;
import com.fvd.github.services.ZipDownloadService;
import com.fvd.indexs.indexers.CodeSampleIndexer;
import com.fvd.indexs.indexers.KeywordIndexer;
import com.fvd.indexs.services.IndexService;
import com.fvd.indexs.stores.CodeSampleIndexStore;
import com.fvd.indexs.stores.KeywordIndexStore;
import com.fvd.indexs.stores.SqliteSchemaInitializer;
import jakarta.inject.Inject;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
```

**Both subclasses** remove imports for types now only referenced in the base class.

---

## Implementation Notes

### Why Warmup Tests Do NOT Use `simulateWarmup()`

`CacheWarmupJobIntegrationTest` tests assert **intermediate** results of each warmup step (e.g., extracted file names, doc content, index entry presence). The `simulateWarmup()` helper is a fire-and-forget shortcut — appropriate for refresh test setup, but not for warmup tests that verify each step.

### `@QuarkusTest` Stays on Subclasses

Quarkus CDI injection requires `@QuarkusTest` on the concrete test class. The abstract base class must NOT have this annotation. CDI will still inject fields declared in the superclass because Quarkus scans the full class hierarchy for `@Inject` annotations.

### No Production Code Changes

This feature modifies **only** test files:
- `src/test/java/com/fvd/cache/jobs/AbstractCacheJobIntegrationTest.java` (new)
- `src/test/java/com/fvd/cache/jobs/CacheRefreshJobIntegrationTest.java` (modified)
- `src/test/java/com/fvd/cache/jobs/CacheWarmupJobIntegrationTest.java` (modified)

### What We Do NOT Change

| Item | Decision | Rationale |
|------|----------|-----------|
| Warmup test method bodies | **Keep as-is** | They test intermediate steps; not suitable for `simulateWarmup()` |
| Refresh test method count | **Keep all 3** | Each tests a distinct refresh concern (code samples, keywords, searchability) |
| WireMock stubs in `src/test/resources/` | **Untouched** | Both tests use the same stubs; no changes needed |
| `CacheRefreshJob` and `SearchService` injects | **Keep in subclass** | Only used by refresh tests |

---

## Tasks

- [x] Create `AbstractCacheJobIntegrationTest.java` with 8 `@Inject` fields, `cleanTestCache()`, and `simulateWarmup()`
- [x] Modify `CacheWarmupJobIntegrationTest` to extend base class; remove 8 fields and `cleanTestCache()`
- [x] Modify `CacheRefreshJobIntegrationTest` to extend base class; remove 8 fields and `cleanTestCache()`
- [x] Replace 3 warmup preamble blocks in refresh test with `simulateWarmup("3.27")`
- [x] Strip redundant warmup assertions from refresh tests
- [x] Replace `hasSizeGreaterThanOrEqualTo(warmupSampleCount)` with `isNotEmpty()` in both refresh tests
- [x] Clean up imports in all 3 files
- [x] Run `./gradlew test` — all tests pass
- [x] Verify combined line count decreased (354 lines → ~200 lines across 3 files)

---

## Acceptance Criteria

1. `AbstractCacheJobIntegrationTest.java` exists at `src/test/java/com/fvd/cache/jobs/`
2. Base class is `abstract`, not annotated with `@QuarkusTest`
3. Base class contains exactly 8 `@Inject` fields (no more, no less)
4. Base class contains `@BeforeEach cleanTestCache()` with identical logic
5. Base class contains `protected List<String> simulateWarmup(String version)` helper
6. `CacheWarmupJobIntegrationTest` extends `AbstractCacheJobIntegrationTest`
7. `CacheWarmupJobIntegrationTest` has zero `@Inject` fields and no `cleanTestCache()` method
8. `CacheWarmupJobIntegrationTest` still has 3 test methods with unchanged assertions
9. `CacheRefreshJobIntegrationTest` extends `AbstractCacheJobIntegrationTest`
10. `CacheRefreshJobIntegrationTest` has exactly 2 `@Inject` fields (`CacheRefreshJob`, `SearchService`)
11. `CacheRefreshJobIntegrationTest` has no `cleanTestCache()` method
12. All 3 refresh test methods use `simulateWarmup("3.27")` instead of inline warmup code
13. No warmup-verification assertions remain in refresh tests (no `assertThat(extractedFiles)`, no `warmupIndex`, no `warmupSampleCount`, no `warmupFileCount`)
14. `./gradlew test` passes with zero failures
15. No production code is modified
16. Total line count across the 3 test files is lower than the current 354 lines across the 3 test files

---

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Quarkus CDI does not inject fields in abstract superclass | Very Low | High | Quarkus 3.x supports `@Inject` field inheritance; verified by existing CDI behavior |
| `@BeforeEach` in superclass not invoked by JUnit 5 | Very Low | High | JUnit 5 spec guarantees `@BeforeEach` methods in superclasses are invoked before subclass methods |
| Removing warmup assertions from refresh tests hides a regression | Low | Medium | Warmup behavior is fully covered by `CacheWarmupJobIntegrationTest` (3 dedicated tests); refresh tests still verify post-refresh state |
| `simulateWarmup()` return value unused in 2 of 3 call sites | Very Low | Low | Acceptable — helper returns the list for callers that need it; ignoring a return value is idiomatic Java |
| Import cleanup misses a required import | Very Low | Low | Compiler error caught immediately by `./gradlew test` |

---

## Estimated Effort

| Task | Hours |
|------|-------|
| Create base class | 0.25 |
| Refactor both subclasses | 0.5 |
| Strip warmup assertions + cleanup | 0.25 |
| Run tests + verify | 0.25 |
| **Total** | **~1.25 hours** |

---

END OF FILE
