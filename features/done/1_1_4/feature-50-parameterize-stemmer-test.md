# Feature 50: Parameterize StemmerTest

> **Dependencies**: None. This is a test-only refactoring with no production code changes.

## Summary

Consolidate 33 test methods (including 1 duplicate) in `StemmerTest.java` down to ~4 test methods by replacing 30 identical-pattern assertions (`assertThat(Stemmer.stem("input")).isEqualTo("expected")`) with a single `@ParameterizedTest` using `@CsvSource`. Remove one exact duplicate test (`"running" → "run"` tested at both line 22 and line 174). Preserve the null-handling test and the morphological consistency test as standalone methods.

## User Story

As a **developer**, I want `StemmerTest` to use parameterized tests so that:
- Adding a new stemming test case requires adding one CSV row, not a new 4-line method
- The test file is ~60 lines instead of 204 lines, reducing maintenance burden
- The exact duplicate test for `"running" → "run"` is eliminated
- Test coverage is identical — every input/output pair is preserved

## Motivation

`StemmerTest` currently has **33 test methods across 204 lines**. Analysis reveals:

| Group | Lines | Tests | Pattern |
|-------|-------|-------|---------|
| Basic suffix stripping | 11–42 | 6 | `assertThat(Stemmer.stem(X)).isEqualTo(Y)` |
| Various suffix rules | 46–117 | 12 | Identical pattern |
| -s stripping | 120–129 | 2 | Identical pattern |
| Edge cases | 133–169 | 5 | Identical pattern (except null) |
| Duplicate consonant reduction | 173–190 | 3 | Identical pattern |
| Consistency test | 194–202 | 1 | Unique — two stems compared |

**30+ tests** follow the exact same one-liner assertion pattern. Additionally, `stemRunningStripsIngAndReducesDuplicate` (line 22) and `stemTrailingDuplicateConsonantReducedAfterIngStrip` (line 174) are **exact duplicates** — both assert `Stemmer.stem("running")` equals `"run"`.

---

## Requirements

### 1. Delete 31 Test Methods (30 unique + 1 duplicate) and Replace with One `@ParameterizedTest`

**Delete these methods** (replaced by CsvSource rows):

1. `stemConfigurationStripsAtion` (line 12)
2. `stemSecurityStripsIty` (line 17)
3. `stemRunningStripsIngAndReducesDuplicate` (line 22)
4. `stemClassesStripsEs` (line 28)
5. `stemUsedDoesNotStripEdWhenTooShort` (line 33)
6. `stemStoppingStripsIngAndReducesDuplicate` (line 39)
7. `stemActionTooShortAfterTionStrip` (line 47)
8. `stemExpressionStripsSion` (line 53)
9. `stemManagementStripsMent` (line 58)
10. `stemDarknessStripsNess` (line 63)
11. `stemConfigurableStripsAble` (line 68)
12. `stemAccessibleStripsIble` (line 73)
13. `stemDangerousStripsOus` (line 78)
14. `stemActiveStripsIve` (line 83)
15. `stemPowerfulStripsFul` (line 88)
16. `stemPowerlessStripsLess` (line 93)
17. `stemQuicklyStripsLy` (line 98)
18. `stemFastestStripsEst` (line 103)
19. `stemRunnerStripsErAndReducesDuplicate` (line 108)
20. `stemConfiguredStripsEd` (line 114)
21. `stemEndpointsStripsS` (line 121)
22. `stemDoesNotStripSFromSsEnding` (line 126)
23. `stemShortWordUnchanged` (line 134)
24. `stemEmptyStringReturnsEmpty` (line 143)
25. `stemTwoCharWordUnchanged` (line 149)
26. `stemAlreadyStemmedWordUnchanged` (line 154)
27. `stemWordEndingInSStripsS` (line 160)
28. `stemWordShorterThanSuffixUnchanged` (line 166)
29. `stemTrailingDuplicateConsonantReducedAfterIngStrip` (line 174) — **EXACT DUPLICATE of line 22**
30. `stemStandaloneDoubleConsonantNotReduced` (line 181)
31. `stemTrailingDuplicateVowelNotReduced` (line 187)

> Note: 30 unique tests + 1 duplicate = 31 methods deleted. The duplicate `"running" → "run"` appears only once in the `@CsvSource`.

**Replace with:**

```java
@ParameterizedTest(name = "stem(\"{0}\") → \"{1}\"")
@CsvSource({
        // Basic suffix stripping
        "configuration, configur",
        "security,      secur",
        "running,       run",
        "classes,       class",
        "used,          used",
        "stopping,      stop",
        // Various suffix rules
        "action,        action",
        "expression,    expres",
        "management,    manage",
        "darkness,      dark",
        "configurable,  configur",
        "accessible,    access",
        "dangerous,     danger",
        "active,        act",
        "powerful,      power",
        "powerless,     power",
        "quickly,       quick",
        "fastest,       fast",
        "runner,        run",
        "configured,    configur",
        // -s stripping
        "endpoints,     endpoint",
        "class,         class",
        // Edge cases (non-null)
        "'',            ''",
        "go,            go",
        "to,            to",
        "configur,      configur",
        "quarkus,       quarku",
        "ing,           ing",
        // Trailing duplicate consonant behavior
        "runn,          runn",
        "see,           see"
})
void stemProducesExpectedResult(String input, String expected) {
    assertThat(Stemmer.stem(input)).isEqualTo(expected);
}
```

### 2. Keep the Null Test as a Standalone `@Test`

`@CsvSource` cannot represent `null` inputs directly. Keep this as a standalone test:

```java
@Test
void stemNullReturnsNull() {
    assertThat(Stemmer.stem(null)).isNull();
}
```

### 3. Keep the Morphological Consistency Test as a Standalone `@Test`

This test has a different assertion structure and is semantically distinct. Keep unchanged:

```java
@Test
void stemMorphologicalVariantsProduceSameStem() {
    String stem1 = Stemmer.stem("configure");
    String configStem1 = Stemmer.stem("configuration");
    String configStem2 = Stemmer.stem("configurable");
    assertThat(configStem1).isEqualTo(configStem2).isEqualTo("configur");
}
```

(Note: the existing unused `stem1` variable is preserved as-is to match the 'keep unchanged' policy)


### 4. Add Required Imports

```java
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
```

---

## Implementation Notes

### `@CsvSource` Empty String Handling

JUnit 5.8+ treats `''` (two single quotes) as an empty string in `@CsvSource`. Quarkus uses JUnit 5.9+, so this is supported.

### Test Count Semantics

The parameterized test with 30 rows produces 30 test invocations. Combined with the null test and consistency test, the total test invocation count is 32. Coverage is identical; the method count drops from 33 to 3.

### No Gradle Changes Required

`junit-jupiter-params` is a transitive dependency of `io.quarkus:quarkus-junit5`.

---

## Tasks

- [x] Add imports for `ParameterizedTest` and `CsvSource`
- [x] Create `stemProducesExpectedResult()` with `@CsvSource` (~30 rows)
- [x] Delete all 31 old methods (30 unique + 1 duplicate)
- [x] Keep `stemNullReturnsNull` as standalone `@Test`
- [x] Keep `stemMorphologicalVariantsProduceSameStem` as standalone `@Test`
- [x] Remove old section comment blocks
- [x] Run `./gradlew test --tests "com.fvd.common.StemmerTest"` — all pass
- [x] Run `./gradlew test` — full suite passes
- [x] Confirm file is ≤ 80 lines (down from 204)

---

## Acceptance Criteria

1. `StemmerTest.java` contains exactly 3–4 test methods (1 parameterized + 2 standalone)
2. Every original input/output pair is preserved in the `@CsvSource`
3. The duplicate test (`"running" → "run"`) appears only once
4. The null test remains standalone
5. The consistency test is unchanged
6. `./gradlew test` passes with zero failures
7. File size is ≤ 80 lines (down from 204)
8. No wildcard imports
9. `Stemmer.java` is not modified

---

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| `junit-jupiter-params` not on classpath | Very Low | Medium | Transitively included via `quarkus-junit5` |
| `@CsvSource` empty string (`''`) syntax not supported | Low | Low | Quarkus uses JUnit 5.9+ which supports `''` |
| Accidentally deleting a unique test case | Low | High | All pairs listed explicitly in this spec |
| `@CsvSource` whitespace trimming alters test data | Very Low | Medium | No test inputs have significant whitespace |

---

## Estimated Effort

| Task | Hours |
|------|-------|
| Write parameterized test + delete old methods | 0.5 |
| Verify and run tests | 0.25 |
| **Total** | **~1 hour** |

---

END OF FILE
