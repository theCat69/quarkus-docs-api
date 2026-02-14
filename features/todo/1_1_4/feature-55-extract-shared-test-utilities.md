# Feature 55: Extract Shared Test Utilities

> **Dependencies**: None. This is a test-only refactoring with no production code changes.

## Summary

Extract duplicated test setup patterns into two shared utility classes (`TestSqliteHelper`, `TestZipHelper`) and deduplicate identical `@BeforeEach` logic in `SearchServiceTest`'s nested classes. The SQLite setup boilerplate is copy-pasted across **8 test files** (9 call sites), and the ZIP builder helper is duplicated in **2 test files**. Two `@Nested` classes in `SearchServiceTest` contain identical 7-line `@BeforeEach` methods that construct the same object graph. After extraction, each duplication site becomes a 1–2 line call.

## User Story

As a **developer**, I want shared test setup logic extracted into reusable utility classes so that:
- Adding a new test that needs an initialized SQLite database requires **one method call** instead of copying 4 lines
- Changing the schema initialization pattern requires updating **one place** instead of 8+ files
- Test files are shorter and focused on the behavior they verify, not infrastructure boilerplate
- The project follows DRY principles in test code

## Motivation

### Duplication 1: SQLite Setup Boilerplate (8 files, 9 call sites)

The following 4-line block (with minor variations) is copy-pasted across 8 test files:

```java
SQLiteDataSource ds = new SQLiteDataSource();
ds.setUrl("jdbc:sqlite:" + tempDir.resolve("test.db"));
SqliteSchemaInitializer initializer = new SqliteSchemaInitializer(ds);
initializer.initSchema();
```

**Exact locations:**

| File | Lines | Variation |
|------|-------|-----------|
| `IndexStoreTest.java` | 26–29 | Standard pattern |
| `KeywordIndexStoreTest.java` | 29–32 | Standard pattern |
| `CodeSampleIndexStoreTest.java` | 28–32 | Sets `initializer.cacheDir = tempDir.toString()` before `initSchema()` |
| `IndexServiceTest.java` | 35–38 | Standard pattern |
| `KeywordIndexerTest.java` | 36–39 | Standard pattern |
| `CodeSampleIndexerTest.java` | 38–41 | Standard pattern |
| `SearchServiceTest.java` (root `setUp`) | 40–43 | Standard pattern |
| `SearchServiceTest.java` → `SectionContentTests` | 553 | Inherits `keywordIndexStore`/`codeSampleIndexStore` from parent; no direct SQLite init (uses parent) |
| `SearchServiceTest.java` → `SectionSearchSnippetAndFilterTests` | 740 | Same as above |

All 7 direct SQLite init sites use the identical 4-line block, except `CodeSampleIndexStoreTest` which additionally sets `initializer.cacheDir = tempDir.toString()`.

### Duplication 2: ZIP Builder Helper (2 files)

Two test files contain nearly identical `createZip()` methods:

| File | Lines | Return Type | Signature |
|------|-------|-------------|-----------|
| `QuarkiverseServiceTest.java` | 268–278 | `byte[]` | `private byte[] createZip(String... nameContentPairs)` |
| `QuarkiverseZipExtractorTest.java` | 114–126 | `InputStream` | `private InputStream createZip(String... nameContentPairs)` |

The core logic is identical: create a `ByteArrayOutputStream`, write `ZipEntry` pairs via `ZipOutputStream`, close. The only difference is the return type — one returns `byte[]`, the other wraps in `ByteArrayInputStream`.

### Duplication 3: Identical `@BeforeEach` in `SearchServiceTest` Nested Classes

Two `@Nested` classes in `SearchServiceTest` contain identical 7-line `@BeforeEach` methods:

**`SectionContentTests.setUpSectionContent()` (lines 552–559):**
```java
CacheService cacheService = new CacheService(tempDir.toString());
realDocStore = new DocStore(cacheService);
SearchConfig searchConfig = new TestSearchConfig();
FuzzyMatcher fuzzyMatcher = new FuzzyMatcher(searchConfig);
SearchScorer searchScorer = new SqliteSearchScorer(searchConfig);
sectionSearchService = new SearchService(
        keywordIndexStore, codeSampleIndexStore, realDocStore, docParser, cacheService, searchConfig, fuzzyMatcher, searchScorer);
```

**`SectionSearchSnippetAndFilterTests.setUpSnippetTests()` (lines 740–746):**
```java
CacheService cacheService = new CacheService(tempDir.toString());
realDocStore = new DocStore(cacheService);
SearchConfig searchConfig = new TestSearchConfig();
FuzzyMatcher fuzzyMatcher = new FuzzyMatcher(searchConfig);
SearchScorer searchScorer = new SqliteSearchScorer(searchConfig);
snippetSearchService = new SearchService(
        keywordIndexStore, codeSampleIndexStore, realDocStore, docParser, cacheService, searchConfig, fuzzyMatcher, searchScorer);
```

These are **character-for-character identical** (except the field name: `sectionSearchService` vs `snippetSearchService`).

---

## Requirements

### R1: Create `TestSqliteHelper` Utility Class

**File:** `src/test/java/com/fvd/common/TestSqliteHelper.java`

**Package:** `com.fvd.common`

**Annotations:** `@UtilityClass` (Lombok) — makes class final, constructor private, all methods static.

**Method:**

```java
/**
 * Creates an SQLiteDataSource pointing to a "test.db" file inside the given directory,
 * initializes the schema, and returns the ready-to-use data source.
 */
static SQLiteDataSource createInitializedDataSource(Path tempDir) {
    SQLiteDataSource ds = new SQLiteDataSource();
    ds.setUrl("jdbc:sqlite:" + tempDir.resolve("test.db"));
    SqliteSchemaInitializer initializer = new SqliteSchemaInitializer(ds);
    initializer.initSchema();
    return ds;
}
```

**Imports (no wildcards):**
```java
import lombok.experimental.UtilityClass;
import org.sqlite.SQLiteDataSource;
import com.fvd.indexs.stores.SqliteSchemaInitializer;
import java.nio.file.Path;
```

### R2: Create `TestZipHelper` Utility Class

**File:** `src/test/java/com/fvd/common/TestZipHelper.java`

**Package:** `com.fvd.common`

**Annotations:** `@UtilityClass` (Lombok)

**Methods:**

```java
/**
 * Builds an in-memory ZIP archive from name/content pairs.
 * Arguments must be provided in pairs: name1, content1, name2, content2, ...
 *
 * @param nameContentPairs alternating entry names and their string content
 * @return the ZIP archive as a byte array
 */
static byte[] createZip(String... nameContentPairs) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (ZipOutputStream zos = new ZipOutputStream(baos)) {
        for (int i = 0; i < nameContentPairs.length; i += 2) {
            zos.putNextEntry(new ZipEntry(nameContentPairs[i]));
            zos.write(nameContentPairs[i + 1].getBytes());
            zos.closeEntry();
        }
    }
    return baos.toByteArray();
}

/**
 * Builds an in-memory ZIP archive and returns it as an InputStream.
 * Convenience wrapper around {@link #createZip(String...)}.
 */
static InputStream createZipAsStream(String... nameContentPairs) throws IOException {
    return new ByteArrayInputStream(createZip(nameContentPairs));
}
```

**Imports (no wildcards):**
```java
import lombok.experimental.UtilityClass;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
```

### R3: Extract Shared `@BeforeEach` in `SearchServiceTest` Nested Classes

Extract the duplicated setup into a private helper method in `SearchServiceTest`:

```java
private SearchService createSearchServiceWithDocStore(DocStore docStore) {
    CacheService cs = new CacheService(tempDir.toString());
    SearchConfig searchConfig = new TestSearchConfig();
    FuzzyMatcher fuzzyMatcher = new FuzzyMatcher(searchConfig);
    SearchScorer searchScorer = new SqliteSearchScorer(searchConfig);
    return new SearchService(
            keywordIndexStore, codeSampleIndexStore, docStore, docParser, cs, searchConfig, fuzzyMatcher, searchScorer);
}
```

Then update both nested classes:

**`SectionContentTests.setUpSectionContent()`:**
```java
@BeforeEach
void setUpSectionContent() {
    CacheService cs = new CacheService(tempDir.toString());
    realDocStore = new DocStore(cs);
    sectionSearchService = createSearchServiceWithDocStore(realDocStore);
}
```

**`SectionSearchSnippetAndFilterTests.setUpSnippetTests()`:**
```java
@BeforeEach
void setUpSnippetTests() {
    CacheService cs = new CacheService(tempDir.toString());
    realDocStore = new DocStore(cs);
    snippetSearchService = createSearchServiceWithDocStore(realDocStore);
}
```

> Note: `CacheService` creation stays in the nested `@BeforeEach` because the nested classes also store `realDocStore` as a field, and `DocStore` depends on its own `CacheService` instance. The helper method accepts the already-constructed `DocStore`.

### R4: Update All Affected Test Files to Use `TestSqliteHelper`

**Files to update and their changes:**

| # | File | Current Lines | After |
|---|------|--------------|-------|
| 1 | `IndexStoreTest.java` | Lines 26–29: 4-line block | `SQLiteDataSource ds = TestSqliteHelper.createInitializedDataSource(tempDir);` |
| 2 | `KeywordIndexStoreTest.java` | Lines 29–32: 4-line block | `SQLiteDataSource ds = TestSqliteHelper.createInitializedDataSource(tempDir);` |
| 3 | `IndexServiceTest.java` | Lines 35–38: 4-line block | `SQLiteDataSource ds = TestSqliteHelper.createInitializedDataSource(tempDir);` |
| 4 | `KeywordIndexerTest.java` | Lines 36–39: 4-line block | `SQLiteDataSource ds = TestSqliteHelper.createInitializedDataSource(tempDir);` |
| 5 | `CodeSampleIndexerTest.java` | Lines 38–41: 4-line block | `SQLiteDataSource ds = TestSqliteHelper.createInitializedDataSource(tempDir);` |
| 6 | `SearchServiceTest.java` | Lines 40–43: 4-line block | `SQLiteDataSource ds = TestSqliteHelper.createInitializedDataSource(tempDir);` |

**Each updated file must:**
- Add import: `import com.fvd.common.TestSqliteHelper;`
- Remove import: `import com.fvd.indexs.stores.SqliteSchemaInitializer;` (if no longer referenced)
- Keep import: `import org.sqlite.SQLiteDataSource;` (still needed — `ds` is passed to store constructors)

### R5: Update ZIP Test Files to Use `TestZipHelper`

| # | File | Change |
|---|------|--------|
| 1 | `QuarkiverseServiceTest.java` | Delete `createZip()` method (lines 268–278). Replace calls with `TestZipHelper.createZip(...)`. Add import `com.fvd.common.TestZipHelper`. |
| 2 | `QuarkiverseZipExtractorTest.java` | Delete `createZip()` method (lines 114–126). Replace calls with `TestZipHelper.createZipAsStream(...)`. Add import `com.fvd.common.TestZipHelper`. |

**Call site updates in `QuarkiverseServiceTest.java`:**
- Line 82: `byte[] zipBytes = createZip(...)` → `byte[] zipBytes = TestZipHelper.createZip(...)`
- Line 119: `byte[] zipBytes = createZip(...)` → `byte[] zipBytes = TestZipHelper.createZip(...)`

**Call site updates in `QuarkiverseZipExtractorTest.java`:**
- Lines 38, 58, 71, 83, 94, 105: `InputStream zip = createZip(...)` → `InputStream zip = TestZipHelper.createZipAsStream(...)`

**Import changes in `QuarkiverseServiceTest.java`:**
- Add: `import com.fvd.common.TestZipHelper;`
- Remove (if no longer used directly): `import java.io.ByteArrayOutputStream;`, `import java.util.zip.ZipEntry;`, `import java.util.zip.ZipOutputStream;`

**Import changes in `QuarkiverseZipExtractorTest.java`:**
- Add: `import com.fvd.common.TestZipHelper;`
- Remove (if no longer used directly): `import java.io.ByteArrayOutputStream;`, `import java.util.zip.ZipEntry;`, `import java.util.zip.ZipOutputStream;`

### R6: Exclude `CodeSampleIndexStoreTest` from SQLite Helper Refactoring

`CodeSampleIndexStoreTest` is the only test that sets `initializer.cacheDir = tempDir.toString()` before calling `initSchema()`. The `cacheDir` field on `SqliteSchemaInitializer` has package-private (default) visibility in package `com.fvd.indexs.stores`, which is inaccessible from `TestSqliteHelper` in package `com.fvd.common`. 

**Decision**: `CodeSampleIndexStoreTest` keeps its existing 5-line setup block unchanged. This avoids introducing production code changes, reflection hacks, or cross-package visibility workarounds.

---

## Implementation Notes

### Exception Handling in `TestZipHelper`

The `createZip()` method declares `throws IOException` to match the existing behavior in both source files. `QuarkiverseServiceTest.createZip()` declares `throws IOException` and `QuarkiverseZipExtractorTest.createZip()` declares `throws Exception`. The test methods calling `createZipAsStream` in `QuarkiverseZipExtractorTest` already declare `throws Exception`, so adding `throws IOException` from `TestZipHelper` is compatible.

### Import Cleanup

After replacing the boilerplate, some test files will have unused imports for `SqliteSchemaInitializer`, `ByteArrayOutputStream`, `ZipEntry`, and `ZipOutputStream`. These must be removed. The `SQLiteDataSource` import will typically still be needed because the `ds` variable is used after creation (passed to store constructors).

### No Production Code Changes

This feature modifies only test files and creates only test utility classes. No files under `src/main/` are changed.

### File Organization

Both utility classes go in `src/test/java/com/fvd/common/` alongside existing test files like `StemmerTest.java`, `StopWordsTest.java`, etc. This is a natural location for shared test infrastructure.

### `@UtilityClass` Behavior

Lombok's `@UtilityClass` makes the class `final`, adds a private no-args constructor that throws `UnsupportedOperationException`, and makes all members `static`. This is the correct annotation for stateless helper classes.

---

## Tasks

- [ ] Create `src/test/java/com/fvd/common/TestSqliteHelper.java` with `@UtilityClass` and `createInitializedDataSource(Path)` method
- [ ] Create `src/test/java/com/fvd/common/TestZipHelper.java` with `@UtilityClass`, `createZip()` and `createZipAsStream()` methods
- [ ] Update `IndexStoreTest.java` — replace 4-line block with `TestSqliteHelper.createInitializedDataSource(tempDir)`, clean imports
- [ ] Update `KeywordIndexStoreTest.java` — replace 4-line block, clean imports
- [ ] Update `IndexServiceTest.java` — replace 4-line block, clean imports
- [ ] Update `KeywordIndexerTest.java` — replace 4-line block, clean imports
- [ ] Update `CodeSampleIndexerTest.java` — replace 4-line block, clean imports
- [ ] Update `SearchServiceTest.java` root `setUp()` — replace 4-line block, clean imports
- [ ] Extract `createSearchServiceWithDocStore(DocStore)` helper in `SearchServiceTest`
- [ ] Update `SectionContentTests.setUpSectionContent()` to use the helper
- [ ] Update `SectionSearchSnippetAndFilterTests.setUpSnippetTests()` to use the helper
- [ ] Update `QuarkiverseServiceTest.java` — delete `createZip()`, replace calls with `TestZipHelper.createZip(...)`, clean imports
- [ ] Update `QuarkiverseZipExtractorTest.java` — delete `createZip()`, replace calls with `TestZipHelper.createZipAsStream(...)`, clean imports
- [ ] Verify no new wildcard imports introduced in any new or modified file
- [ ] Run `./gradlew test` — all tests pass with zero failures

---

## Acceptance Criteria

1. `TestSqliteHelper.java` exists at `src/test/java/com/fvd/common/TestSqliteHelper.java`
2. `TestSqliteHelper` is annotated with `@UtilityClass` and has a `createInitializedDataSource(Path)` method
3. `TestZipHelper.java` exists at `src/test/java/com/fvd/common/TestZipHelper.java`
4. `TestZipHelper` is annotated with `@UtilityClass` and has `createZip()` and `createZipAsStream()` methods
5. No test file (except `CodeSampleIndexStoreTest`) contains the 4-line SQLite setup block (`new SQLiteDataSource()` + `setUrl` + `new SqliteSchemaInitializer` + `initSchema`)
6. No test file contains a private `createZip()` method
7. `SearchServiceTest.SectionContentTests` and `SearchServiceTest.SectionSearchSnippetAndFilterTests` share setup logic via a helper method; no duplicated 7-line `@BeforeEach` body
8. All 10 modified/created test files compile and pass: `./gradlew test` passes with zero failures
9. No new wildcard imports introduced in any new or modified file
10. No production code files (`src/main/`) are modified
11. `SqliteSchemaInitializer` import is removed from test files that no longer reference it directly
12. `CodeSampleIndexStoreTest.java` is unchanged (excluded due to `cacheDir` visibility constraint)

---

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| `@UtilityClass` makes class final — breaks subclassing | Very Low | None | Utility classes should never be subclassed; this is the desired behavior |
| `cacheDir` package-private visibility prevents helper overload | Confirmed | Low | `CodeSampleIndexStoreTest` excluded from refactoring; keeps its own setup block |
| `TestZipHelper.createZip` throws `IOException` — callers may need signature changes | Very Low | Low | All calling test methods already declare `throws Exception` or `throws IOException` |
| Changing `@BeforeEach` in nested classes breaks field initialization order | Low | Medium | `keywordIndexStore` and `codeSampleIndexStore` are initialized in the parent `@BeforeEach` which runs before nested `@BeforeEach`; order is preserved |
| Test isolation — shared utility may hide test-specific setup nuances | Low | Low | Both helpers are stateless; they create new instances per call |
| Future schema init changes require updating `TestSqliteHelper` | Low | Low | Single point of change is the goal; updating 1 file is better than 7 |

---

## Estimated Effort

| Task | Hours |
|------|-------|
| Create `TestSqliteHelper` and `TestZipHelper` | 0.5 |
| Update 6 test files for SQLite helper | 0.5 |
| Update 2 test files for ZIP helper | 0.25 |
| Extract `SearchServiceTest` shared setup | 0.25 |
| Import cleanup and verification | 0.25 |
| Run full test suite | 0.25 |
| **Total** | **~2 hours** |

---

## Files Modified

### New Files (2)
- `src/test/java/com/fvd/common/TestSqliteHelper.java`
- `src/test/java/com/fvd/common/TestZipHelper.java`

### Modified Files (8)
- `src/test/java/com/fvd/indexs/stores/IndexStoreTest.java`
- `src/test/java/com/fvd/indexs/stores/KeywordIndexStoreTest.java`
- `src/test/java/com/fvd/indexs/services/IndexServiceTest.java`
- `src/test/java/com/fvd/indexs/indexers/KeywordIndexerTest.java`
- `src/test/java/com/fvd/indexs/indexers/CodeSampleIndexerTest.java`
- `src/test/java/com/fvd/search/services/SearchServiceTest.java`
- `src/test/java/com/fvd/quarkiverse/services/QuarkiverseServiceTest.java`
- `src/test/java/com/fvd/quarkiverse/services/QuarkiverseZipExtractorTest.java`

### Unchanged Files (1 — excluded from scope)
- `src/test/java/com/fvd/indexs/stores/CodeSampleIndexStoreTest.java` — excluded due to `cacheDir` package-private access constraint

---

END OF FILE
