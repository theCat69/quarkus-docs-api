# Feature 53: Extract Shared API Resource Test Base Class

> **Dependencies**: None. This is a test-only refactoring with no production code changes.

## Summary

Extract a shared `AbstractApiResourceTest` base class from five API resource integration test classes to eliminate heavily duplicated `@BeforeEach` cleanup logic, `@Inject` field declarations, and seed helper methods. Additionally, remove 6 redundant error-case tests from individual resource test classes that are already covered (with stricter RFC 7807 assertions) by `ProblemDetailErrorResponseTest`. Net reduction: ~120 lines of duplicated code across 5 files.

## User Story

As a **developer**, I want API resource integration tests to inherit shared setup and seed helpers from a common base class so that:
- Adding a new injected service or cache invalidation requires changing **one place** instead of five
- Seed helpers used by multiple tests are maintained once, not copy-pasted
- Error-case tests are not duplicated when a dedicated error-response test class already covers them with stricter assertions
- New resource tests can extend the base class and get cleanup logic for free

## Motivation

1. **`cleanTestCache()` is copy-pasted 5 times**: The `@BeforeEach` method that clears `build/test-cache`, resets the SQLite schema, and invalidates search caches is duplicated with minor variations across all 5 API resource test classes. Changes to cache invalidation logic must be made in 5 places.

2. **`@Inject` fields are declared 19 times total**: `SqliteSchemaInitializer` appears in all 5 classes. `DocStore`, `SearchService` appear in 4. `KeywordIndexStore` in 3. `CodeSampleIndexStore` in 2. `CatalogService` in 1. A base class can hold the union.

3. **Seed helpers are exact duplicates across test classes**:
   - `seedKeywordIndexMultiple()` — identical in `ApiSearchResourceTest` (lines 240–249) and `DocumentResourceTest` (lines 260–269)
   - `seedKeywordIndexWithExtensions()` — identical in `ApiSearchResourceTest` (lines 252–264) and `DocumentResourceTest` (lines 272–284)
   - `seedDocFilesMultiple()` / `seedDocFileMultiple()` — near-duplicate in `ApiSearchResourceTest` (lines 212–215) and `DocumentResourceTest` (lines 245–248)

4. **6 error-case tests are fully redundant** with `ProblemDetailErrorResponseTest`, which asserts the same status codes plus RFC 7807 fields (`title`, `status`, `detail`, `instance`, `timestamp`). The individual resource tests assert only `statusCode(400)` or `statusCode(404)` — a strict subset.

---

## Requirements

### R1: Create `AbstractApiResourceTest` Base Class

**File**: `src/test/java/com/fvd/api/resources/AbstractApiResourceTest.java`

The class must:
- **NOT** be annotated with `@QuarkusTest` (subclasses already have it)
- Declare all shared `@Inject` fields (see R2)
- Contain the unified `cleanTestCache()` `@BeforeEach` method (see R3)
- Contain shared seed helper methods (see R4)
- Use **no wildcard imports**

```java
package com.fvd.api.resources;

import com.fvd.api.services.CatalogService;
import com.fvd.docs.stores.DocStore;
import com.fvd.indexs.indexers.CodeSampleEntry;
import com.fvd.indexs.indexers.CodeSampleIndex;
import com.fvd.indexs.indexers.FileKeywordEntry;
import com.fvd.indexs.indexers.KeywordIndex;
import com.fvd.indexs.indexers.KeywordScore;
import com.fvd.indexs.indexers.SectionKeywordEntry;
import com.fvd.indexs.stores.CodeSampleIndexStore;
import com.fvd.indexs.stores.KeywordIndexStore;
import com.fvd.indexs.stores.SqliteSchemaInitializer;
import com.fvd.search.services.SearchService;
import jakarta.inject.Inject;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Shared setup and seed helpers for API resource integration tests.
 * Subclasses must be annotated with {@code @QuarkusTest}.
 */
abstract class AbstractApiResourceTest {
    // fields — see R2
    // cleanTestCache() — see R3
    // seed helpers — see R4
}
```

### R2: Shared `@Inject` Fields

Declare the following **6 fields** in `AbstractApiResourceTest`:

```java
@Inject
KeywordIndexStore keywordIndexStore;

@Inject
CodeSampleIndexStore codeSampleIndexStore;

@Inject
DocStore docStore;

@Inject
SqliteSchemaInitializer schemaInitializer;

@Inject
SearchService searchService;

@Inject
CatalogService catalogService;
```

**Rationale per field:**

| Field | Used by | Classes using it |
|-------|---------|-----------------|
| `SqliteSchemaInitializer` | `cleanTestCache()` | All 5 |
| `DocStore` | seed helpers, test methods | 4 of 5 (`ApiSearch`, `CodeSample`, `Document`, `Catalog`) |
| `SearchService` | `cleanTestCache()` | 4 of 5 (`ApiSearch`, `CodeSample`, `Document`, `Catalog`) |
| `KeywordIndexStore` | seed helpers | 3 of 5 (`ApiSearch`, `Document`, `Catalog`) |
| `CodeSampleIndexStore` | seed helpers | 2 of 5 (`CodeSample`, `Catalog`) |
| `CatalogService` | `cleanTestCache()` | 1 of 5 (`Catalog`) |

All 6 fields are used by `cleanTestCache()` or shared seed helpers, justifying their inclusion. Injecting unused fields into a subclass is harmless — Quarkus CDI handles it transparently.

### R3: Unified `cleanTestCache()` `@BeforeEach` Method

The base class must contain a single `cleanTestCache()` that unifies all 5 variants:

```java
@BeforeEach
void cleanTestCache() throws IOException {
    var cachePath = Path.of("build/test-cache").toFile();
    if (cachePath.exists()) {
        FileUtils.cleanDirectory(cachePath);
    }
    schemaInitializer.resetSchema();
    searchService.invalidateCache("3.27");
    searchService.invalidateCache("main");
    catalogService.invalidateCache("3.27");
    catalogService.invalidateCache("main");
}
```

**Unification mapping:**

| Original class | Original lines | Difference from unified |
|----------------|---------------|------------------------|
| `ApiSearchResourceTest` | 44–52 | Identical (minus catalog) |
| `CodeSampleResourceTest` | 43–51 | Identical (minus catalog) |
| `DocumentResourceTest` | 40–48 | Identical (minus catalog) |
| `CatalogResourceTest` | 57–67 | Already has catalog invalidation |
| `ProblemDetailErrorResponseTest` | 26–32 | Subset — no `searchService` or `catalogService` invalidation |

The unified version is a **superset** — it invalidates all caches. This is safe because:
- Invalidating a cache that was never populated is a no-op
- All 5 test classes share the same Quarkus test instance, so cross-test cache pollution is already a risk that the unified cleanup eliminates

### R4: Shared Seed Helper Methods

Move these **4 seed helpers** to the base class:

#### R4.1: `seedKeywordIndexMultiple()`

Currently identical in `ApiSearchResourceTest` (lines 240–249) and `DocumentResourceTest` (lines 260–269):

```java
protected void seedKeywordIndexMultiple() {
    KeywordIndex index = new KeywordIndex(List.of(
            new FileKeywordEntry("security.adoc",
                    List.of(new KeywordScore("security", 15), new KeywordScore("quarkus", 8)),
                    List.of()),
            new FileKeywordEntry("config.adoc",
                    List.of(new KeywordScore("config", 10), new KeywordScore("quarkus", 5)),
                    List.of())
    ));
    keywordIndexStore.write("3.27", index);
}
```

**Delete from**: `ApiSearchResourceTest` (lines 240–249), `DocumentResourceTest` (lines 260–269)

#### R4.2: `seedKeywordIndexWithExtensions()`

Currently identical in `ApiSearchResourceTest` (lines 252–264) and `DocumentResourceTest` (lines 272–284):

```java
protected void seedKeywordIndexWithExtensions() {
    KeywordIndex index = new KeywordIndex(List.of(
            new FileKeywordEntry("security.adoc",
                    List.of(new KeywordScore("security", 15)),
                    List.of(),
                    "quarkus-core"),
            new FileKeywordEntry("config.adoc",
                    List.of(new KeywordScore("security", 10)),
                    List.of(),
                    "quarkus-openapi-generator")
    ));
    keywordIndexStore.write("3.27", index);
}
```

**Delete from**: `ApiSearchResourceTest` (lines 252–264), `DocumentResourceTest` (lines 272–284)

#### R4.3: `seedDocFilesMultiple()`

Currently near-duplicate in `ApiSearchResourceTest` (lines 212–215, named `seedDocFilesMultiple()`) and `DocumentResourceTest` (lines 245–248, named `seedDocFileMultiple()`). The only difference is the trailing period in `ApiSearchResourceTest`:

- `ApiSearchResourceTest`: `"Content about security and quarkus."` / `"Content about config and quarkus."`
- `DocumentResourceTest`: `"Content about security."` / `"Content about config."`

Unify with the richer content (from `ApiSearchResourceTest`) since the tests that use this helper don't assert on the exact body content:

```java
protected void seedDocFilesMultiple() {
    docStore.write("3.27", "security.adoc", "= Security\nContent about security and quarkus.");
    docStore.write("3.27", "config.adoc", "= Config\nContent about config and quarkus.");
}
```

**Delete from**: `ApiSearchResourceTest` (lines 212–215), `DocumentResourceTest` (lines 245–248 — rename callsites from `seedDocFileMultiple()` to `seedDocFilesMultiple()`)

#### R4.4: `seedCodeSampleIndex()`

Currently only in `CodeSampleResourceTest` (lines 168–180). This is the primary code-sample seed used by multiple tests within that class. Move to the base class so future resource tests that need code samples can reuse it:

```java
protected void seedCodeSampleIndex() {
    CodeSampleIndex index = new CodeSampleIndex(List.of(
            new CodeSampleEntry("security.adoc", "Authentication", "java",
                    "import io.quarkus.security.identity.SecurityIdentity;",
                    5, 10,
                    List.of(new KeywordScore("security", 15), new KeywordScore("identity", 8))),
            new CodeSampleEntry("config.adoc", "Authorization", "java",
                    "@RolesAllowed(\"admin\")",
                    20, 25,
                    List.of(new KeywordScore("security", 10), new KeywordScore("roles", 5)))
    ));
    codeSampleIndexStore.write("3.27", index);
}
```

**Delete from**: `CodeSampleResourceTest` (lines 168–180)

### R5: Remove 6 Redundant Error-Case Tests

Delete these tests from individual resource classes. Each is a **strict subset** of the corresponding test in `ProblemDetailErrorResponseTest` (which asserts `statusCode` + `title` + `status` + `detail`/`instance` + `timestamp`):

| # | Redundant test (DELETE) | Superset in `ProblemDetailErrorResponseTest` | Why redundant |
|---|------------------------|---------------------------------------------|--------------|
| 1 | `ApiSearchResourceTest.testSearchMissingKeywords()` (lines 54–61) | `testBadRequestForMissingKeywords()` (lines 66–76) | Both hit `GET /api/search?version=3.27` with no `keywords`. ProblemDetail test asserts `statusCode(400)` + `title` + `status` + `instance` + `timestamp` |
| 2 | `CodeSampleResourceTest.testSearchCodeSamplesMissingKeywords()` (lines 53–60) | `testBadRequestForCodeSamplesMissingKeywords()` (lines 92–103) | Both hit `GET /api/code-samples?version=3.27` with no `keywords`. ProblemDetail test asserts 4 additional fields |
| 3 | `DocumentResourceTest.testMissingPathAndKeywords()` (lines 183–191) | `testBadRequestForDocumentsNeitherPathNorKeywords()` (lines 105–116) | Both hit `GET /api/documents?version=3.27` with neither `path` nor `keywords`. ProblemDetail test asserts `title` + `status` + `detail` + `timestamp` |
| 4 | `DocumentResourceTest.testGetDocumentByPathNotFound()` (lines 67–76) | `testNotFoundReturnsProblemDetail()` (lines 49–63) | Both hit `GET /api/documents?path=nonexistent.adoc&version=3.27`. ProblemDetail test asserts `type` + `title` + `status` + `detail` + `instance` + `timestamp` |
| 5 | `DocumentResourceTest.testGetDocumentByPathInvalid()` (lines 78–86) | `testBadRequestForInvalidPath()` (lines 78–90) | Both hit `GET /api/documents?path=../../etc/passwd&version=3.27`. ProblemDetail test asserts `title` + `status` + `instance` + `timestamp` |
| 6 | `CatalogResourceTest.testCatalogEndpointInvalidVersion()` (lines 133–140) | `testBadRequestReturnsProblemDetail()` (lines 34–47) | Both hit `GET /api/catalog?version=../etc/passwd`. ProblemDetail test asserts `type` + `title` + `status` + `detail` + `instance` + `timestamp` |

### R6: Refactor All 5 Test Classes to Extend the Base Class

Each test class must:
1. Add `extends AbstractApiResourceTest`
2. Remove all `@Inject` fields that are now in the base class
3. Remove the `cleanTestCache()` method
4. Remove shared seed helpers that moved to the base class
5. Remove redundant error-case tests (per R5)
6. Remove now-unused imports
7. Keep all test-specific private seed helpers
8. Keep the `@QuarkusTest` annotation
9. Fix any callsite renames (`seedDocFileMultiple()` → `seedDocFilesMultiple()` in `DocumentResourceTest`)

**Per-class summary:**

#### `ApiSearchResourceTest`
- Remove: 4 `@Inject` fields, `cleanTestCache()`, `seedDocFilesMultiple()`, `seedKeywordIndexMultiple()`, `seedKeywordIndexWithExtensions()`, `testSearchMissingKeywords()`
- Keep: 9 test methods, 7 private seed helpers (`seedDocFile`, `seedDocFileWithKeyword`, `seedDocFileForMain`, `seedKeywordIndex`, `seedKeywordIndexForSnippet`, `seedKeywordIndexWithScores`, `seedKeywordIndexForMain`)
- Remove unused imports: `org.junit.jupiter.api.BeforeEach`, `java.io.IOException`, `java.nio.file.Path`, `org.apache.commons.io.FileUtils`

#### `CodeSampleResourceTest`
- Remove: 4 `@Inject` fields, `cleanTestCache()`, `seedCodeSampleIndex()`, `testSearchCodeSamplesMissingKeywords()`
- Keep: 7 test methods, 4 private seed helpers (`seedDocFile`, `seedCodeSampleIndexWithMultipleLanguages`, `seedCodeSampleIndexWithExtensions`, `seedCodeSampleIndexWithScores`)
- Remove unused imports: `org.junit.jupiter.api.BeforeEach`, `java.io.IOException`, `java.nio.file.Path`, `org.apache.commons.io.FileUtils`

#### `DocumentResourceTest`
- Remove: 4 `@Inject` fields, `cleanTestCache()`, `seedDocFileMultiple()`, `seedKeywordIndexMultiple()`, `seedKeywordIndexWithExtensions()`, `testMissingPathAndKeywords()`, `testGetDocumentByPathNotFound()`, `testGetDocumentByPathInvalid()`
- Fix: rename `seedDocFileMultiple()` calls to `seedDocFilesMultiple()`
- Keep: 7 test methods, 3 private seed helpers (`seedDocFile`, `seedDocFileWithCode`, `seedKeywordIndex`)
- Remove unused imports: `org.junit.jupiter.api.BeforeEach`, `java.io.IOException`, `java.nio.file.Path`, `org.apache.commons.io.FileUtils`
- **Fix wildcard import**: Replace `import static org.hamcrest.Matchers.*` with explicit imports

#### `CatalogResourceTest`
- Remove: 6 `@Inject` fields, `cleanTestCache()`, `testCatalogEndpointInvalidVersion()`
- Keep: 5 test methods, 2 private seed helpers (`seedKeywordIndex`, `seedKeywordIndexWithExtensions` — note: this is a **different** implementation from the shared one, with `SectionKeywordEntry` data)
- Remove unused imports: `org.junit.jupiter.api.BeforeEach`, `java.io.IOException`, `java.nio.file.Path`, `org.apache.commons.io.FileUtils`

#### `ProblemDetailErrorResponseTest`
- Remove: 1 `@Inject` field (`SqliteSchemaInitializer`), `cleanTestCache()`
- Keep: All 6 test methods (they are the canonical error-response tests)
- Remove unused imports: `org.junit.jupiter.api.BeforeEach`, `java.io.IOException`, `java.nio.file.Path`, `org.apache.commons.io.FileUtils`
- **Fix wildcard import**: Replace `import static org.hamcrest.Matchers.*` with explicit imports

---

## Implementation Notes

### Why `CatalogResourceTest.seedKeywordIndexWithExtensions()` Stays Private

`CatalogResourceTest.seedKeywordIndexWithExtensions()` (lines 152–166) includes `SectionKeywordEntry` data that the shared version does not. It tests catalog-specific behavior (extension listing with section-level data). Keeping it private avoids confusion with the shared helper.

### Why `AbstractApiResourceTest` Is Not `@QuarkusTest`

Quarkus CDI injection works through the subclass's `@QuarkusTest` annotation. Annotating the abstract base class would cause `QuarkusTestExtension` to attempt to instantiate it, which would fail. The `@Inject` fields are inherited and injected when the concrete subclass is instantiated by the test framework.

### `protected` vs. `package-private` for Seed Helpers

Seed helpers should be `protected` to allow subclasses in the same or sub-packages to use them. The `@Inject` fields can remain package-private (default) since all classes are in the same package.

### Handling of `DocumentResourceTest` Wildcard Import

`DocumentResourceTest` (line 22) and `ProblemDetailErrorResponseTest` (line 14) currently use `import static org.hamcrest.Matchers.*`. This must be replaced with explicit imports per project conventions. Inspect the test methods to determine which `Matchers` are used:

**`DocumentResourceTest`**: `containsString`, `equalTo`, `greaterThan`, `is`, `notNullValue`
**`ProblemDetailErrorResponseTest`**: `containsString`, `equalTo`, `notNullValue`

### Test Execution Order

JUnit 5 `@BeforeEach` on a superclass runs **before** any `@BeforeEach` on the subclass. Since the subclasses currently have their own `cleanTestCache()` as their only `@BeforeEach`, moving it to the base class preserves identical execution order.

---

## Tasks

- [ ] Create `AbstractApiResourceTest.java` with 6 `@Inject` fields, unified `cleanTestCache()`, and 4 shared seed helpers
- [ ] Refactor `ApiSearchResourceTest` to extend base class; remove duplicated fields, setup, seeds, and 1 redundant test
- [ ] Refactor `CodeSampleResourceTest` to extend base class; remove duplicated fields, setup, seeds, and 1 redundant test
- [ ] Refactor `DocumentResourceTest` to extend base class; remove duplicated fields, setup, seeds, and 3 redundant tests; fix wildcard import; rename `seedDocFileMultiple()` calls
- [ ] Refactor `CatalogResourceTest` to extend base class; remove duplicated fields, setup, and 1 redundant test
- [ ] Refactor `ProblemDetailErrorResponseTest` to extend base class; remove duplicated fields and setup; fix wildcard import
- [ ] Run `./gradlew test` — all tests pass
- [ ] Verify 6 redundant error-case tests are deleted
- [ ] Verify no wildcard imports remain in any of the 6 files

---

## Acceptance Criteria

1. `AbstractApiResourceTest.java` exists at `src/test/java/com/fvd/api/resources/AbstractApiResourceTest.java`
2. `AbstractApiResourceTest` is **not** annotated with `@QuarkusTest`
3. `AbstractApiResourceTest` declares exactly 6 `@Inject` fields: `KeywordIndexStore`, `CodeSampleIndexStore`, `DocStore`, `SqliteSchemaInitializer`, `SearchService`, `CatalogService`
4. `AbstractApiResourceTest.cleanTestCache()` is annotated with `@BeforeEach` and invalidates all caches (schema reset + search + catalog for both `"3.27"` and `"main"`)
5. `AbstractApiResourceTest` contains exactly 4 `protected` seed helpers: `seedKeywordIndexMultiple()`, `seedKeywordIndexWithExtensions()`, `seedDocFilesMultiple()`, `seedCodeSampleIndex()`
6. All 5 test classes extend `AbstractApiResourceTest`
7. No test class declares `@Inject` fields that are in the base class
8. No test class declares its own `cleanTestCache()` method
9. 6 redundant error-case tests are deleted (1 from `ApiSearch`, 1 from `CodeSample`, 3 from `Document`, 1 from `Catalog`)
10. `ProblemDetailErrorResponseTest` retains all 6 of its test methods unchanged
11. No wildcard imports in any of the 6 files
12. `./gradlew test` passes with zero failures
13. No production code is modified
14. Total test method count across the 5 subclasses decreases by 6 (redundant error tests removed)
15. Total line count across all 6 files is lower than the current total across the 5 files

---

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Quarkus CDI fails to inject fields declared in a non-`@QuarkusTest` superclass | Very Low | High | Quarkus CDI injection follows standard Java inheritance; `@Inject` fields on superclasses are injected when the subclass is the managed bean. Verified by Quarkus documentation. |
| `@BeforeEach` in superclass not invoked by JUnit 5 | Very Low | High | JUnit 5 spec guarantees `@BeforeEach` methods on superclasses are called before subclass methods. |
| Removing error-case tests reduces coverage | Low | Medium | `ProblemDetailErrorResponseTest` already covers all 6 scenarios with **stricter** assertions (RFC 7807 fields). The deleted tests asserted only `statusCode`. |
| Unified `cleanTestCache()` invalidates caches not used by a subclass (e.g., `catalogService` in `ProblemDetailErrorResponseTest`) | Very Low | Very Low | Invalidating a cache that was never populated is a no-op. No performance impact in tests. |
| `CatalogResourceTest.seedKeywordIndexWithExtensions()` name collision with base class method | Low | Medium | The catalog version has a different implementation (includes `SectionKeywordEntry`). It stays `private` in `CatalogResourceTest`. In Java, a `private` method does not shadow or override an inherited `protected` method — both coexist independently. The subclass's own methods will resolve to its private version by name, while `super.seedKeywordIndexWithExtensions()` would invoke the base class version. Alternatively, rename it to `seedKeywordIndexWithExtensionsAndSections()` if the naming overlap is confusing. |
| `seedDocFilesMultiple()` content change (richer content from `ApiSearchResourceTest` vs. simpler from `DocumentResourceTest`) | Very Low | Low | Neither `DocumentResourceTest` nor `ApiSearchResourceTest` asserts on the body content of docs seeded by this helper. Tests only verify search results, paths, and scores. |

---

## Estimated Effort

| Task | Hours |
|------|-------|
| Create `AbstractApiResourceTest` base class | 0.5 |
| Refactor 5 test classes to extend base | 1.0 |
| Remove 6 redundant tests + fix wildcard imports | 0.5 |
| Run full test suite and verify | 0.25 |
| **Total** | **~2.25 hours** |

---

END OF FILE
