# Feature 56: Consolidate Quarkiverse Integration Test Setup

> **Dependencies**: None. This is a test-only refactoring with no production code changes.

## Summary

Extract two private helper methods to eliminate duplicated test setup logic in the quarkiverse test files. In `QuarkiverseIntegrationTest.java`, a 10-line index-building block is copy-pasted across 4 of 6 test methods — extract it into a single `buildQuarkiverseIndexes()` helper. In `QuarkiverseServiceTest.java`, a 5-line playbook mock setup pattern is repeated across 5 of 6 test methods — extract it into a single `stubPlaybook(String yamlContent)` helper. Both are intra-file refactorings: no new files, no base classes, no production code changes.

## User Story

As a **developer**, I want duplicated test setup blocks in `QuarkiverseIntegrationTest` and `QuarkiverseServiceTest` extracted into private helpers so that:
- Adding a new test method that requires index-building or playbook stubbing requires **one method call** instead of copy-pasting 5–10 lines
- If the index-building or playbook-stubbing logic changes, I update **one place** instead of 4–5 places
- The test files are shorter and easier to read, with setup noise removed from the test body

## Motivation

### QuarkiverseIntegrationTest — 4× duplicated index-building block

Lines 77–87, 124–133, 149–159, and 178–187 all contain this identical block (with minor variations in which indexers are called):

```java
Map<String, List<String>> filePathsByExtension = new LinkedHashMap<>();
filePathsByExtension.put("quarkus-core", List.of());
for (String path : quarkiversePaths) {
    String[] parts = path.split("/", 3);
    if (parts.length >= 2) {
        String extName = parts[1];
        filePathsByExtension.computeIfAbsent(extName, k -> new ArrayList<>()).add(path);
    }
}
keywordIndexer.build("main", filePathsByExtension);
codeSampleIndexer.build("main", filePathsByExtension);
```

All 4 call `quarkiverseService.fetchAndExtractAll()` immediately before. The only variation is which indexers are invoked:

| Test Method | `keywordIndexer.build()` | `codeSampleIndexer.build()` |
|-------------|:------------------------:|:---------------------------:|
| `quarkiverseDocsAppearInSearchResultsForMainVersion` (line 72) | ✓ | ✓ |
| `quarkiverseDocsNotInIndexEndpoint` (line 120) | ✓ | ✗ |
| `quarkiverseDocsNotInVersionedSearch` (line 146) | ✓ | ✗ |
| `quarkiverseCodeSamplesAppearInSearch` (line 173) | ✗ | ✓ |

Because the helper should cover the common case, it should build **both** indexes. Tests that only need one indexer still benefit from the helper because building the unused index has no side effects on the test assertions. This keeps the helper simple and avoids parameterization.

### QuarkiverseServiceTest — 5× duplicated playbook stub pattern

Lines 66–80, 95–112, 143–157, 191–205, and 227–241 all contain this identical pattern:

```java
String playbookYaml = """
        content:
          sources:
            - url: https://github.com/quarkiverse/quarkus-<name>
              branches: main
              start_path: docs
        """;

GithubApiFile playbookFile = new GithubApiFile();
playbookFile.content = java.util.Base64.getEncoder().encodeToString(playbookYaml.getBytes());
playbookFile.encoding = "base64";

when(gitHubService.fetchFileContentForRepo(
        "quarkiverse", "quarkiverse-docs", "antora-playbook.yml", "main"))
        .thenReturn(playbookFile);
```

The only variation is the YAML content (different extension URLs). The 5 lines that create a `GithubApiFile`, Base64-encode the content, and stub the mock are identical every time.

---

## Requirements

### R1: Extract `buildQuarkiverseIndexes()` in QuarkiverseIntegrationTest

**Add this private method:**

```java
private void buildQuarkiverseIndexes() {
    List<String> paths = quarkiverseService.fetchAndExtractAll();

    Map<String, List<String>> filePathsByExtension = new LinkedHashMap<>();
    filePathsByExtension.put("quarkus-core", List.of());
    for (String path : paths) {
        String[] parts = path.split("/", 3);
        if (parts.length >= 2) {
            String extName = parts[1];
            filePathsByExtension.computeIfAbsent(extName, k -> new ArrayList<>()).add(path);
        }
    }
    keywordIndexer.build("main", filePathsByExtension);
    codeSampleIndexer.build("main", filePathsByExtension);
}
```

**Refactor these 4 test methods** to replace their inline blocks with `buildQuarkiverseIndexes()`:

1. `quarkiverseDocsAppearInSearchResultsForMainVersion` (lines 72–99) — replace lines 74–87 with `buildQuarkiverseIndexes()`
2. `quarkiverseDocsNotInIndexEndpoint` (lines 120–143) — replace lines 122–133 with `buildQuarkiverseIndexes()`
3. `quarkiverseDocsNotInVersionedSearch` (lines 146–170) — replace lines 148–159 with `buildQuarkiverseIndexes()`
4. `quarkiverseCodeSamplesAppearInSearch` (lines 173–200) — replace lines 175–187 with `buildQuarkiverseIndexes()`

**Note:** Tests 2 and 3 previously only called `keywordIndexer.build()`, and test 4 previously only called `codeSampleIndexer.build()`. The helper calls both. This is safe because:
- Building an unused index does not affect assertion outcomes
- Both indexers are already injected fields in the test class
- The tests assert against specific API endpoints, not against indexer state

### R2: Extract `stubPlaybook(String yamlContent)` in QuarkiverseServiceTest

**Add this private method:**

```java
private void stubPlaybook(String yamlContent) {
    GithubApiFile playbookFile = new GithubApiFile();
    playbookFile.content = java.util.Base64.getEncoder().encodeToString(yamlContent.getBytes());
    playbookFile.encoding = "base64";

    when(gitHubService.fetchFileContentForRepo(
            "quarkiverse", "quarkiverse-docs", "antora-playbook.yml", "main"))
            .thenReturn(playbookFile);
}
```

**Refactor these 5 test methods** to replace their inline `GithubApiFile` creation + mock stubbing with `stubPlaybook(yamlContent)`:

1. `fetchAndExtractAllFetchesPlaybookAndExtractsDocs` (line 65) — replace lines 74–80 with `stubPlaybook(playbookYaml)`
2. `fetchAndExtractAllContinuesWhenSingleExtensionFails` (line 94) — replace lines 106–112 with `stubPlaybook(playbookYaml)`
3. `refreshAllReturnsTrueWhenChangesDetected` (line 142) — replace lines 151–157 with `stubPlaybook(playbookYaml)`
4. `refreshAllReturnsFalseWhenNoChanges` (line 190) — replace lines 199–205 with `stubPlaybook(playbookYaml)`
5. `refreshAllReturnsTrueForNewExtension` (line 226) — replace lines 235–241 with `stubPlaybook(playbookYaml)`

Each test retains its local `playbookYaml` string variable (the YAML content differs per test) and passes it to the helper. The helper encapsulates only the encoding + mock stubbing.

### R3: Leave Remaining Tests Unchanged

- `QuarkiverseIntegrationTest.fetchAndExtractAllExtractsQuarkiverseDocs` (line 57) — does not build indexes, no change needed
- `QuarkiverseIntegrationTest.quarkiverseDocRetrievableViaDocumentsEndpoint` (line 103) — does not build indexes, no change needed
- `QuarkiverseServiceTest.fetchAndExtractAllReturnsEmptyWhenPlaybookFetchFails` (line 131) — stubs `thenThrow` instead of `thenReturn`, so `stubPlaybook` does not apply

### R4: No New Imports Required

Both test files already import all necessary types (`LinkedHashMap`, `ArrayList`, `List`, `Map`, `GithubApiFile`, `Base64`, etc.). No new imports are needed.

---

## Implementation Notes

### Intra-File Refactoring Only

Both helpers are `private` methods within their respective test classes. No new files, no abstract base classes, no test utilities shared across files. This is the simplest possible refactoring.

### Building Both Indexes Is Intentional

The `buildQuarkiverseIndexes()` helper always calls both `keywordIndexer.build()` and `codeSampleIndexer.build()`. This is a deliberate simplification:
- The 2 tests that previously only called `keywordIndexer.build()` now also call `codeSampleIndexer.build()`, which is harmless — they assert against `/api/search` or `/api/catalog`, not `/api/code-samples`
- The 1 test that previously only called `codeSampleIndexer.build()` now also calls `keywordIndexer.build()`, which is harmless — it asserts against `/api/code-samples`
- Keeping the helper parameter-free makes it maximally simple

### The `stubPlaybook` Helper Receives YAML as a String Parameter

The YAML content varies per test (different extension URLs, single vs. multiple sources). The helper receives the content as a `String` parameter and handles only the boilerplate: `GithubApiFile` creation, Base64 encoding, and mock stubbing. Each test still defines its own YAML inline.

### Line Count Impact

| File | Before (lines) | After (lines) | Reduction |
|------|:--------------:|:--------------:|:---------:|
| `QuarkiverseIntegrationTest.java` | 203 | ~170 | ~33 lines |
| `QuarkiverseServiceTest.java` | 280 | ~255 | ~25 lines |
| **Total** | **483** | **~425** | **~58 lines** |

### No Production Code Changes

This feature modifies **only** test files:
- `src/test/java/com/fvd/quarkiverse/QuarkiverseIntegrationTest.java`
- `src/test/java/com/fvd/quarkiverse/services/QuarkiverseServiceTest.java`

---

## Tasks

- [x] Add `buildQuarkiverseIndexes()` private method to `QuarkiverseIntegrationTest`
- [x] Replace inline index-building blocks in 4 test methods with `buildQuarkiverseIndexes()` call
- [x] Add `stubPlaybook(String yamlContent)` private method to `QuarkiverseServiceTest`
- [x] Replace inline `GithubApiFile` creation + mock stubbing in 5 test methods with `stubPlaybook(yamlContent)` call
- [x] Verify the 3 unchanged tests remain unmodified
- [x] Run `./gradlew test --tests "com.fvd.quarkiverse.*"` — all quarkiverse tests pass
- [x] Run `./gradlew test` — full suite passes
- [x] Verify no production code files are modified

---

## Acceptance Criteria

1. `QuarkiverseIntegrationTest.java` contains a `private void buildQuarkiverseIndexes()` method
2. The 10-line index-building block no longer appears in any test method body (4 occurrences removed)
3. All 4 refactored test methods call `buildQuarkiverseIndexes()` instead
4. `QuarkiverseServiceTest.java` contains a `private void stubPlaybook(String yamlContent)` method
5. The 5-line `GithubApiFile` + Base64 + mock stubbing block no longer appears in any test method body (5 occurrences removed)
6. All 5 refactored test methods call `stubPlaybook(playbookYaml)` instead
7. `fetchAndExtractAllReturnsEmptyWhenPlaybookFetchFails` is NOT refactored (it uses `thenThrow`)
8. `./gradlew test` passes with zero failures
9. No production code files are modified
10. Test method count is unchanged (6 tests in `QuarkiverseIntegrationTest`, 6 tests in `QuarkiverseServiceTest`)

---

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Building both indexes in `buildQuarkiverseIndexes()` causes test interference | Very Low | Low | Each test runs in isolation with `@BeforeEach` schema reset; unused index has no effect on assertions |
| `stubPlaybook` helper masks test-specific setup, reducing readability | Low | Low | The YAML content (the meaningful part) stays inline in each test; only boilerplate is extracted |
| Future tests need different `fetchFileContentForRepo` arguments (different repo, branch) | Low | Medium | Helper is private; can be overloaded or adjusted without affecting other tests |
| Merge conflict if other features modify these test files concurrently | Low | Low | Both changes are localized (add 1 method, simplify existing methods); conflicts are easy to resolve |

---

## Estimated Effort

| Task | Hours |
|------|-------|
| Add `buildQuarkiverseIndexes()` and refactor 4 callers | 0.25 |
| Add `stubPlaybook()` and refactor 5 callers | 0.25 |
| Run tests and verify | 0.25 |
| **Total** | **~45 minutes** |

---

END OF FILE
