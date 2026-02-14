package com.fvd.search.services;

import com.fvd.search.TestKeywordScoringConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class KeywordScorerTest {

    private KeywordScorer scorer;

    @BeforeEach
    void setUp() {
        scorer = new KeywordScorer(new TestKeywordScoringConfig());
    }

    // ========================================
    // Multiplier tests (all sources)
    // ========================================

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

    // ========================================
    // calculateScore for single occurrence
    // ========================================

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

    // ========================================
    // Heading level identification
    // ========================================

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

    // ========================================
    // parseHeadingLevel
    // ========================================

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

    // ========================================
    // Filename Keyword Extraction
    // ========================================

    @Test
    void shouldExtractKeywordsFromFilename() {
        List<String> keywords = scorer.extractFilenameKeywords("security-oidc-configuration.adoc");
        assertThat(keywords).containsExactlyInAnyOrder("security", "oidc", "configuration");
    }

    @Test
    void shouldRemoveExtensionBeforeExtraction() {
        List<String> keywords = scorer.extractFilenameKeywords("hibernate-panache.adoc");
        assertThat(keywords).containsExactlyInAnyOrder("hibernate", "panache");
        assertThat(keywords).doesNotContain("adoc");
    }

    @Test
    void shouldSplitByHyphens() {
        List<String> keywords = scorer.extractFilenameKeywords("rest-client-guide.adoc");
        assertThat(keywords).contains("rest", "client");
    }

    @Test
    void shouldSplitByUnderscores() {
        List<String> keywords = scorer.extractFilenameKeywords("rest_client_config.md");
        assertThat(keywords).contains("rest", "client", "config");
    }

    @Test
    void shouldFilterStopwords() {
        List<String> keywords = scorer.extractFilenameKeywords("security-guide.adoc");
        assertThat(keywords).contains("security");
        assertThat(keywords).doesNotContain("guide");
    }

    @Test
    void shouldFilterTutorialStopword() {
        List<String> keywords = scorer.extractFilenameKeywords("kafka-tutorial.adoc");
        assertThat(keywords).contains("kafka");
        assertThat(keywords).doesNotContain("tutorial");
    }

    @Test
    void shouldFilterDocStopword() {
        List<String> keywords = scorer.extractFilenameKeywords("config-doc.adoc");
        assertThat(keywords).contains("config");
        assertThat(keywords).doesNotContain("doc");
    }

    @Test
    void shouldExtractFromFilenameWithPath() {
        List<String> keywords = scorer.extractFilenameKeywords("_versions/3.27/guides/security-oidc.adoc");
        assertThat(keywords).containsExactlyInAnyOrder("security", "oidc");
    }

    @Test
    void shouldReturnEmptyListForNullFilename() {
        assertThat(scorer.extractFilenameKeywords(null)).isEmpty();
    }
    @Test
    void shouldReturnEmptyListForBlankFilename() {
        assertThat(scorer.extractFilenameKeywords("   ")).isEmpty();
    }

    // ========================================
    // Compound Score Calculation
    // ========================================

    @Test
    void shouldCalculateFrequencyFactorForSingleOccurrence() {
        // 1.0 + log(1) = 1.0 + 0 = 1.0
        assertThat(scorer.calculateFrequencyFactor(1)).isCloseTo(1.0, within(0.001));
    }

    @Test
    void shouldCalculateFrequencyFactorForMultipleOccurrences() {
        // 1.0 + log(3) ≈ 1.0 + 1.099 ≈ 2.0 (capped)
        assertThat(scorer.calculateFrequencyFactor(3)).isCloseTo(2.0, within(0.001));
    }

    @Test
    void shouldCapFrequencyFactorAtTwo() {
        assertThat(scorer.calculateFrequencyFactor(100)).isEqualTo(2.0);
    }
    @Test
    void shouldReturnZeroFrequencyFactorForZeroCount() {
        assertThat(scorer.calculateFrequencyFactor(0)).isEqualTo(0.0);
    }
    @Test
    void shouldReturnZeroFrequencyFactorForNegativeCount() {
        assertThat(scorer.calculateFrequencyFactor(-1)).isEqualTo(0.0);
    }

    @Test
    void shouldUseHighestWeightWhenKeywordInMultipleLocations() {
        List<String> sources = List.of(
                KeywordScorer.SOURCE_BODY,
                KeywordScorer.SOURCE_SECTION,
                KeywordScorer.SOURCE_FILENAME
        );
        assertThat(scorer.getHighestMultiplier(sources)).isEqualTo(10.0);
    }

    @Test
    void shouldReturnBodyWeightForEmptySourceList() {
        assertThat(scorer.getHighestMultiplier(List.of())).isEqualTo(1.0);
    }
    @Test
    void shouldReturnBodyWeightForNullSourceList() {
        assertThat(scorer.getHighestMultiplier(null)).isEqualTo(1.0);
    }

    @Test
    void shouldCombineScoresFromMultipleSources() {
        List<KeywordScorer.SourceFrequency> scores = List.of(
                new KeywordScorer.SourceFrequency(KeywordScorer.SOURCE_BODY, 2),
                new KeywordScorer.SourceFrequency(KeywordScorer.SOURCE_SECTION, 1),
                new KeywordScorer.SourceFrequency(KeywordScorer.SOURCE_TITLE, 1)
        );
        // Uses highest weight (title = 8.0) with total frequency 4
        // 8.0 * min(1.0 + log(4), 2.0) = 8.0 * 2.0 = 16.0
        assertThat(scorer.combineScores(scores)).isCloseTo(16.0, within(0.001));
    }

    @Test
    void shouldReturnZeroForEmptyScoresList() {
        assertThat(scorer.combineScores(List.of())).isEqualTo(0.0);
    }
    @Test
    void shouldReturnZeroForNullScoresList() {
        assertThat(scorer.combineScores(null)).isEqualTo(0.0);
    }

    @Test
    void shouldApplyFrequencyFactorAfterLocationWeight() {
        // Frequency = 2: factor = min(1.0 + log(2), 2.0) ≈ 1.693; 5.0 * 1.693 ≈ 8.466
        double score = scorer.calculateScore(KeywordScorer.SOURCE_SECTION, 2);
        assertThat(score).isCloseTo(8.466, within(0.01));
    }
    @Test
    void shouldCalculateScoreWithBaseScore() {
        // 2.0 * 5.0 * 1.0 = 10.0
        double score = scorer.calculateScore(2.0, KeywordScorer.SOURCE_SECTION, 1);
        assertThat(score).isCloseTo(10.0, within(0.001));
    }

    // ========================================
    // Additional Edge Cases
    // ========================================

    @Test
    void shouldExtractStemmedFilenameKeywords() {
        List<String> stemmed = scorer.extractStemmedFilenameKeywords("security-configuration.adoc");
        assertThat(stemmed).contains("secur", "configur");
    }

    @Test
    void shouldHandleFilenameWithoutExtension() {
        List<String> keywords = scorer.extractFilenameKeywords("security-config");
        assertThat(keywords).containsExactlyInAnyOrder("security", "config");
    }

    @Test
    void shouldFilterShortTokens() {
        List<String> keywords = scorer.extractFilenameKeywords("a-b-config.adoc");
        assertThat(keywords).contains("config");
        assertThat(keywords).doesNotContain("a", "b");
    }

    @Test
    void shouldHandleMixedSeparators() {
        List<String> keywords = scorer.extractFilenameKeywords("rest-client_config.adoc");
        assertThat(keywords).containsExactlyInAnyOrder("rest", "client", "config");
    }
}
