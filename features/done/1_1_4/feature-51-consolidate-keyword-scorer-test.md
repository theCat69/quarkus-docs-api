# Feature 51: Consolidate KeywordScorerTest

> **Dependencies**: None. This is a test-only refactoring with no production code changes.

## Summary

Reduce test method count in `KeywordScorerTest.java` by consolidating four groups of structurally identical tests into `@ParameterizedTest` methods. The file currently contains **54 test methods across 404 lines**. Four redundancy groups totaling **27 tests** follow input→expected patterns ideal for parameterization. After consolidation, these 27 tests become **4 parameterized test methods**, bringing the total from 54 to ~31 methods with zero loss of behavioral coverage.

## User Story

As a **developer**, I want repetitive `KeywordScorerTest` methods consolidated into parameterized tests so that:
- The file is easier to read, maintain, and extend without duplicating boilerplate
- If scoring weights or heading-level mapping changes, I update 1 data table instead of 7+ tests
- The project follows the `@ParameterizedTest` convention established in v1.1.4

## Motivation

54 tests in a single file is noisy. Many test methods are copy-paste with only the input and expected value changing. This is the project's most bloated test file relative to the logic it covers.

---

## Requirements

### R1: Replace Multiplier Getter Tests with Parameterized Test (7 → 1)

**Delete these 7 methods:**
- `shouldReturnSectionWeightMultiplier` (line 26)
- `shouldReturnSubtitleWeightMultiplier` (line 43)
- `shouldReturnFilenameWeightMultiplier` (line 78)
- `shouldReturnTitleWeightMultiplier` (line 159)
- `shouldReturnBodyWeightMultiplier` (line 237)
- `shouldReturnBodyWeightForUnknownSource` (line 243)
- `shouldReturnBodyWeightForNullSource` (line 249)

**Replace with:**

```java
private static Stream<Arguments> multiplierCases() {
    return Stream.of(
            Arguments.of("filename", 10.0),
            Arguments.of("title", 8.0),
            Arguments.of("section", 5.0),
            Arguments.of("subtitle", 2.0),
            Arguments.of("body", 1.0),
            Arguments.of("unknown", 1.0),
            Arguments.of(null, 1.0)
    );
}

@ParameterizedTest(name = "getMultiplier(\"{0}\") = {1}")
@MethodSource("multiplierCases")
void shouldReturnCorrectMultiplier(String source, double expected) {
    assertThat(scorer.getMultiplier(source)).isEqualTo(expected);
}
```

### R2: Replace calculateScore Same-Pattern Tests with Parameterized Test (5 → 1)

**Delete these 5 methods:**
- `shouldCalculateScoreForSectionKeyword` (line 32)
- `shouldCalculateScoreForSubtitleKeyword` (line 49)
- `shouldCalculateScoreForFilenameKeyword` (line 136)
- `shouldCalculateScoreForTitleKeyword` (line 226)
- `shouldPreserveRawScoresForRanking` (line 380) — absorbed: correct individual values guarantee ranking

**Replace with:**

```java
// Correct values guarantee ranking: filename > title > section > subtitle > body
@ParameterizedTest(name = "calculateScore(\"{0}\", 1) = {1}")
@CsvSource({
        "filename, 10.0",
        "title, 8.0",
        "section, 5.0",
        "subtitle, 2.0"
})
void shouldCalculateScoreForSingleOccurrence(String source, double expected) {
    double score = scorer.calculateScore(source, 1);
    assertThat(score).isCloseTo(expected, within(0.001));
}
```

### R3: Replace Heading Level Identification Tests with Parameterized Test (5 → 1)

**Delete these 5 methods:**
- `shouldIdentifyH3AsSubtitle` (line 56)
- `shouldIdentifyH4AsSubtitle` (line 62)
- `shouldIdentifyH5AsSubtitle` (line 68)
- `shouldIdentifyH1AsTitle` (line 165)
- `shouldIdentifyH2AsSection` (line 171)

**Replace with:**

```java
@ParameterizedTest(name = "getSourceFromHeadingLevel({0}) = \"{1}\"")
@CsvSource({
        "1, title",
        "2, section",
        "3, subtitle",
        "4, subtitle",
        "5, subtitle"
})
void shouldIdentifyCorrectSourceFromHeadingLevel(int level, String expectedSource) {
    assertThat(scorer.getSourceFromHeadingLevel(level)).isEqualTo(expectedSource);
}
```

### R4: Replace parseHeadingLevel Tests with Parameterized Test (10 → 1)

**Delete these 10 methods:**
- `shouldParseH1HeadingLevel` (line 177)
- `shouldParseH2HeadingLevel` (line 183)
- `shouldParseH3HeadingLevel` (line 189)
- `shouldReturnZeroForNonHeading` (line 195)
- `shouldReturnZeroForBlankLine` (line 201)
- `shouldReturnZeroForNullLine` (line 207)
- `shouldHandleMultiWordTitle` (line 213)
- `shouldHandleSpecialCharactersInTitle` (line 220)
- `shouldNotTreatEqualsSignsWithoutSpaceAsHeading` (line 393)
- `shouldHandleHeadingWithLeadingWhitespace` (line 399)

**Replace with:**

```java
private static Stream<Arguments> parseHeadingLevelCases() {
    return Stream.of(
            Arguments.of("= Document Title", 1),
            Arguments.of("== Section Title", 2),
            Arguments.of("=== Subsection Title", 3),
            Arguments.of("= Security and Authentication Guide", 1),
            Arguments.of("= OAuth2 / OIDC Configuration", 1),
            Arguments.of("   == Section Title", 2),
            Arguments.of("Regular text content", 0),
            Arguments.of("==NoSpace", 0),
            Arguments.of("   ", 0),
            Arguments.of(null, 0)
    );
}

@ParameterizedTest(name = "parseHeadingLevel(\"{0}\") = {1}")
@MethodSource("parseHeadingLevelCases")
void shouldParseHeadingLevelCorrectly(String line, int expectedLevel) {
    assertThat(scorer.parseHeadingLevel(line)).isEqualTo(expectedLevel);
}
```

### R5: Add Required Imports

```java
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.stream.Stream;
```

### R6: Tests to Keep As-Is (~27 tests)

All `extractFilenameKeywords` tests (varied assertions), `extractStemmedFilenameKeywords`, `calculateFrequencyFactor` tests, `combineScores` tests, `getHighestMultiplier` tests, `calculateScore` with `baseScore`, and `shouldApplyFrequencyFactorAfterLocationWeight` remain unchanged.

---

## Implementation Notes

1. Groups 1 and 4 use `@MethodSource` because they include `null` test cases. Groups 2 and 3 use `@CsvSource` (no nulls).
2. The ranking test (`shouldPreserveRawScoresForRanking`) is absorbed — correct individual values mathematically guarantee ordering.
3. The existing `import org.junit.jupiter.api.Test` must be kept (27 remaining `@Test` methods use it).

---

## Tasks

- [x] Add parameterized test imports
- [x] Implement Group 1 consolidation (multiplier: 7 → 1)
- [x] Implement Group 2 consolidation (calculateScore: 5 → 1)
- [x] Implement Group 3 consolidation (heading level: 5 → 1)
- [x] Implement Group 4 consolidation (parseHeadingLevel: 10 → 1)
- [x] Reorganize section comments
- [x] Clean up imports
- [x] Run `./gradlew test` — all tests pass
- [x] Verify test count: ~31 methods, ~54 test executions

---

## Acceptance Criteria

1. `KeywordScorerTest.java` contains ~31 test methods (4 parameterized + ~27 individual)
2. All 4 parameterized tests execute the same number of invocations as the deleted methods
3. `./gradlew test` passes with zero failures
4. No production code is modified
5. No wildcard imports
6. Every deleted test's input/expected pair appears in the replacement parameterized data
7. File line count reduced by ≥ 30% (from 404 to ≤ 283)

---

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| `junit-jupiter-params` not on test classpath | Very Low | Medium | Transitively included via `quarkus-junit5` |
| `@CsvSource` whitespace handling for `"   "` in Group 4 | Low | Low | Using `@MethodSource` which preserves whitespace |
| `null` handling in `@CsvSource` | Medium | Medium | Groups 1 and 4 use `@MethodSource` specifically for null |
| Absorbing ranking test removes defense-in-depth | Low | Low | Parameterized test proves each value; ordering is mathematical consequence |

---

## Estimated Effort

| Task | Hours |
|------|-------|
| Implement all 4 consolidations | 0.75 |
| Cleanup and verification | 0.25 |
| **Total** | **~1 hour** |

---

END OF FILE
