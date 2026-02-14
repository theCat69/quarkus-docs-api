# Feature 49: Parameterize SubjectDeriverTest

> **Dependencies**: None. This is a test-only refactoring with no production code changes.

## Summary

Consolidate 54 test methods (481 lines) in `SubjectDeriverTest.java` down to 23 test methods (~200 lines) by replacing 30 nearly-identical pattern-matching tests with a single `@ParameterizedTest`/`@CsvSource` and 3 null/empty/blank edge-case tests with a single `@ParameterizedTest`/`@MethodSource`. All other tests remain as-is because they test unique behaviors.

## User Story

As a **developer**, I want the `SubjectDeriverTest` to use parameterized tests instead of 30+ copy-paste methods so that:
- Adding a new subject mapping requires adding **one CSV row** instead of writing a new 4-line test method
- The test file is ~200 lines instead of 481 lines, making it faster to read and review
- Test failures show the exact `(filename, expectedSubject)` pair that failed, improving debuggability
- The test suite follows the project's TDD conventions for `@ParameterizedTest` usage

## Motivation

`SubjectDeriverTest` currently has **30 tests** (lines 37–215) that follow the exact same pattern:

```java
@Test
void deriveSubjectReturns<Subject>For<Keyword>() {
    assertThat(subjectDeriver.deriveSubject("docs/src/main/asciidoc/<FILENAME>.adoc"))
            .isEqualTo("<EXPECTED_SUBJECT>");
}
```

Every test has identical structure — only the filename and expected subject differ. This is the textbook use case for `@ParameterizedTest` with `@CsvSource`.

Additionally, 3 edge-case tests (null, empty, blank → `"misc"`) follow the same assertion pattern and can be collapsed into a single `@ParameterizedTest` with a `@MethodSource` providing `null`, `""`, and `"   "`.

The remaining tests each verify unique behaviors (overrides, pattern ordering, metadata, document counts, case sensitivity, disabled mode) and should **not** be parameterized.

### Counts

| Group | Current Tests | After | Method |
|-------|--------------|-------|--------|
| Pattern matching (lines 37–215) | 30 | 1 | `@ParameterizedTest` + `@CsvSource` (30 rows) |
| Null/empty/blank edge cases (lines 219–232) | 3 | 1 | `@ParameterizedTest` + `@MethodSource` |
| Case insensitive (line 235) | 1 | 1 | Keep as-is |
| Backslash normalization (line 241) | 1 | 1 | Keep as-is |
| Override tests (lines 248–267) | 2 | 2 | Keep as-is |
| Configured patterns (lines 271–302) | 2 | 2 | Keep as-is |
| Multiple file derivation (lines 306–320) | 1 | 1 | Keep as-is |
| Subject metadata (lines 324–398) | 8 | 8 | Keep as-is |
| Document count tracking (lines 402–447) | 4 | 4 | Keep as-is |
| Case sensitivity & disabled (lines 451–479) | 2 | 2 | Keep as-is |
| **Total** | **54** | **23** | — |

**Net reduction:** 31 fewer test methods, ~280 fewer lines.

---

## Requirements

### 1. Replace 30 Pattern-Matching Tests with One `@ParameterizedTest`

**Delete these 30 methods** (lines 37–215):

1. `deriveSubjectReturnsGettingStartedForQuickstart()`
2. `deriveSubjectReturnsGettingStartedForTutorial()`
3. `deriveSubjectReturnsSecurityForOidc()`
4. `deriveSubjectReturnsSecurityForJwt()`
5. `deriveSubjectReturnsSecurityForKeycloak()`
6. `deriveSubjectReturnsRestApisForRest()`
7. `deriveSubjectReturnsRestApisForResteasy()`
8. `deriveSubjectReturnsDataPersistenceForHibernate()`
9. `deriveSubjectReturnsDataPersistenceForPanache()`
10. `deriveSubjectReturnsDataPersistenceForDatasource()`
11. `deriveSubjectReturnsCoreConceptsForCdi()`
12. `deriveSubjectReturnsCoreConceptsForConfiguration()`
13. `deriveSubjectReturnsCoreConceptsForConfig()`
14. `deriveSubjectReturnsMessagingForKafka()`
15. `deriveSubjectReturnsMessagingForAmqp()`
16. `deriveSubjectReturnsCloudForKubernetes()`
17. `deriveSubjectReturnsCloudForDocker()`
18. `deriveSubjectReturnsCloudForOpenshift()`
19. `deriveSubjectReturnsObservabilityForMetrics()`
20. `deriveSubjectReturnsObservabilityForHealth()`
21. `deriveSubjectReturnsObservabilityForTracing()`
22. `deriveSubjectReturnsTestingForTest()`
23. `deriveSubjectReturnsTestingForJunit()`
24. `deriveSubjectReturnsToolingForCli()`
25. `deriveSubjectReturnsToolingForDevServices()`
26. `deriveSubjectReturnsToolingForMaven()`
27. `deriveSubjectReturnsToolingForGradle()`
28. `deriveSubjectReturnsExtensionsForExtension()`
29. `deriveSubjectReturnsExtensionsForQuarkiverse()`
30. `deriveSubjectReturnsMiscForUnmatchedPath()`

**Replace with:**

```java
@ParameterizedTest(name = "[{index}] {0} → {1}")
@CsvSource({
        "getting-started.adoc,          getting-started",
        "tutorial-getting-started.adoc, getting-started",
        "security-oidc.adoc,            security",
        "security-jwt.adoc,             security",
        "keycloak-admin-client.adoc,    security",
        "rest-client.adoc,              rest-apis",
        "resteasy-reactive.adoc,        rest-apis",
        "hibernate-orm.adoc,            data-persistence",
        "hibernate-orm-panache.adoc,    data-persistence",
        "datasource.adoc,              data-persistence",
        "cdi.adoc,                     core-concepts",
        "configuration.adoc,           core-concepts",
        "config.adoc,                  core-concepts",
        "kafka.adoc,                   messaging",
        "amqp.adoc,                    messaging",
        "kubernetes.adoc,              cloud",
        "container-image-docker.adoc,  cloud",
        "openshift.adoc,               cloud",
        "metrics.adoc,                 observability",
        "health.adoc,                  observability",
        "opentelemetry.adoc,           observability",
        "test.adoc,                    testing",
        "junit.adoc,                   testing",
        "cli.adoc,                     tooling",
        "dev-services.adoc,            tooling",
        "maven-tooling.adoc,           tooling",
        "gradle-tooling.adoc,          tooling",
        "extension-development.adoc,   extensions",
        "quarkiverse.adoc,             extensions",
        "some-random-topic.adoc,       misc"
})
void deriveSubjectReturnsExpectedSubjectForFilename(String filename, String expectedSubject) {
    assertThat(subjectDeriver.deriveSubject("docs/src/main/asciidoc/" + filename))
            .isEqualTo(expectedSubject);
}
```

### 2. Replace 3 Null/Empty/Blank Tests with One `@ParameterizedTest`

**Delete these 3 methods** (lines 219–232):

1. `deriveSubjectReturnsMiscForNull()`
2. `deriveSubjectReturnsMiscForEmpty()`
3. `deriveSubjectReturnsMiscForBlank()`

**Replace with:**

```java
static Stream<Arguments> nullEmptyBlankInputs() {
    return Stream.of(
            Arguments.of((String) null, "null input"),
            Arguments.of("", "empty input"),
            Arguments.of("   ", "blank input")
    );
}

@ParameterizedTest(name = "[{index}] {1} → misc")
@MethodSource("nullEmptyBlankInputs")
void deriveSubjectReturnsMiscForNullEmptyOrBlank(String input, String description) {
    assertThat(subjectDeriver.deriveSubject(input)).isEqualTo("misc");
}
```

### 3. Add Required Imports

```java
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import java.util.stream.Stream;
```

### 4. Keep All Other Tests As-Is

The following test methods must remain **unchanged** — they test unique behaviors with custom mocks, different methods, or different assertion patterns:

- `deriveSubjectIsCaseInsensitive()`
- `deriveSubjectNormalizesBackslashes()`
- `deriveSubjectUsesExactOverride()`
- `deriveSubjectPrioritizesOverridesOverPatterns()`
- `deriveSubjectUsesConfiguredPatterns()`
- `deriveSubjectEvaluatesPatternsInOrder()`
- `deriveSubjectsReturnsMapOfSubjects()`
- `getAllSubjectsReturnsAllDefinedSubjects()`
- `getAllSubjectsIncludesDisplayNames()`
- `getAllSubjectsIncludesDescriptions()`
- `getAllSubjectsIncludesKeywords()`
- `getSubjectReturnsSubjectByName()`
- `getSubjectReturnsEmptyForUnknownName()`
- `getSubjectReturnsEmptyForNull()`
- `getSubjectReturnsEmptyForBlank()`
- `recordDocumentTracksCount()`
- `resetDocCountsClearsAllCounts()`
- `getSubjectsWithDocsReturnsOnlyPopulatedSubjects()`
- `getSubjectsWithDocsReturnsEmptyWhenNoDocuments()`
- `caseSensitiveMatchingWhenConfigured()`
- `deriveSubjectReturnsMiscWhenDisabled()`

---

## Implementation Notes

### Test Count Verification

The `@ParameterizedTest` with 30 `@CsvSource` rows generates **30 test executions** at runtime. The null/empty/blank parameterized test generates **3 test executions**. So the total number of test **executions** remains the same as before, but the number of test **methods** drops from 54 to 23.

### Section Comment Preservation

Keep the existing section comments (`// --- Pattern matching tests ---`, `// --- Edge cases ---`, etc.) to maintain readability.

### No Production Code Changes

This feature modifies **only** `src/test/java/com/fvd/subject/services/SubjectDeriverTest.java`.

---

## Tasks

- [ ] Add parameterized test imports (`ParameterizedTest`, `CsvSource`, `MethodSource`, `Arguments`, `Stream`)
- [ ] Delete 30 pattern-matching test methods (lines 37–215)
- [ ] Add `deriveSubjectReturnsExpectedSubjectForFilename()` with `@CsvSource` (30 rows)
- [ ] Delete 3 null/empty/blank test methods (lines 219–232)
- [ ] Add `nullEmptyBlankInputs()` method source and `deriveSubjectReturnsMiscForNullEmptyOrBlank()` parameterized test
- [ ] Verify all remaining test methods are unchanged
- [ ] Run `./gradlew test` — all tests must pass
- [ ] Verify file is ~200 lines (down from 481)

---

## Acceptance Criteria

1. `SubjectDeriverTest.java` contains 23 test methods (2 parameterized + 21 individual)
2. The 30 deleted pattern-matching methods no longer exist in the file
3. The 3 deleted null/empty/blank methods no longer exist in the file
4. `deriveSubjectReturnsExpectedSubjectForFilename()` is annotated with `@ParameterizedTest` and `@CsvSource` containing exactly **30 rows**
5. `deriveSubjectReturnsMiscForNullEmptyOrBlank()` is annotated with `@ParameterizedTest` and `@MethodSource`
6. `./gradlew test` passes with **zero failures**
7. Total test executions for `SubjectDeriverTest` equals the original count (parameterized rows count as individual executions)
8. The file is under 250 lines (down from 481)
9. No production code files are modified

---

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| `@CsvSource` CSV parsing trims or misinterprets filenames with hyphens | Very Low | Low | JUnit 5 `@CsvSource` handles hyphens natively |
| Missing `junit-jupiter-params` dependency | Very Low | Medium | Already transitively included via `quarkus-junit5` |
| `@MethodSource` method not found (wrong name/signature) | Low | Low | Method must be `static`, return `Stream<Arguments>`, name must match `@MethodSource` value |
| Developer removes a test that was NOT in the consolidation group | Low | High | All 30+3 method names are listed explicitly |
| `null` value in `@MethodSource` causes NPE | Very Low | Low | JUnit 5 supports `null` via `Arguments.of((String) null)` |

---

## Estimated Effort

| Task | Hours |
|------|-------|
| Add imports and create parameterized tests | 0.5 |
| Delete 33 old methods | 0.25 |
| Verify remaining tests unchanged | 0.25 |
| Run full test suite and verify | 0.25 |
| **Total** | **~1.5 hours** |

---

END OF FILE
