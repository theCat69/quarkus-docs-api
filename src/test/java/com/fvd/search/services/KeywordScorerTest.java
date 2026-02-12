package com.fvd.search.services;

import com.fvd.search.TestKeywordScoringConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class KeywordScorerTest {

    private KeywordScorer scorer;

    @BeforeEach
    void setUp() {
        scorer = new KeywordScorer(new TestKeywordScoringConfig());
    }

    // ========================================
    // R1: Section Title Extraction (5x weight)
    // ========================================

    @Test
    void shouldReturnSectionWeightMultiplier() {
        double multiplier = scorer.getMultiplier(KeywordScorer.SOURCE_SECTION);
        assertThat(multiplier).isEqualTo(5.0);
    }

    @Test
    void shouldCalculateScoreForSectionKeyword() {
        double score = scorer.calculateScore(KeywordScorer.SOURCE_SECTION, 1);
        // 5.0 * (1.0 + log(1)) = 5.0 * 1.0 = 5.0
        assertThat(score).isCloseTo(5.0, within(0.001));
    }

    // ========================================
    // R2: Subtitle Extraction (2x weight)
    // ========================================

    @Test
    void shouldReturnSubtitleWeightMultiplier() {
        double multiplier = scorer.getMultiplier(KeywordScorer.SOURCE_SUBTITLE);
        assertThat(multiplier).isEqualTo(2.0);
    }

    @Test
    void shouldCalculateScoreForSubtitleKeyword() {
        double score = scorer.calculateScore(KeywordScorer.SOURCE_SUBTITLE, 1);
        // 2.0 * 1.0 = 2.0
        assertThat(score).isCloseTo(2.0, within(0.001));
    }

    @Test
    void shouldIdentifyH3AsSubtitle() {
        String source = scorer.getSourceFromHeadingLevel(3);
        assertThat(source).isEqualTo(KeywordScorer.SOURCE_SUBTITLE);
    }

    @Test
    void shouldIdentifyH4AsSubtitle() {
        String source = scorer.getSourceFromHeadingLevel(4);
        assertThat(source).isEqualTo(KeywordScorer.SOURCE_SUBTITLE);
    }

    @Test
    void shouldIdentifyH5AsSubtitle() {
        String source = scorer.getSourceFromHeadingLevel(5);
        assertThat(source).isEqualTo(KeywordScorer.SOURCE_SUBTITLE);
    }

    // ========================================
    // R3: Filename Keyword Extraction (10x weight)
    // ========================================

    @Test
    void shouldReturnFilenameWeightMultiplier() {
        double multiplier = scorer.getMultiplier(KeywordScorer.SOURCE_FILENAME);
        assertThat(multiplier).isEqualTo(10.0);
    }

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
    void shouldCalculateScoreForFilenameKeyword() {
        double score = scorer.calculateScore(KeywordScorer.SOURCE_FILENAME, 1);
        // 10.0 * 1.0 = 10.0
        assertThat(score).isCloseTo(10.0, within(0.001));
    }

    @Test
    void shouldReturnEmptyListForNullFilename() {
        List<String> keywords = scorer.extractFilenameKeywords(null);
        assertThat(keywords).isEmpty();
    }

    @Test
    void shouldReturnEmptyListForBlankFilename() {
        List<String> keywords = scorer.extractFilenameKeywords("   ");
        assertThat(keywords).isEmpty();
    }

    // ========================================
    // R4: Document Title Extraction (8x weight)
    // ========================================

    @Test
    void shouldReturnTitleWeightMultiplier() {
        double multiplier = scorer.getMultiplier(KeywordScorer.SOURCE_TITLE);
        assertThat(multiplier).isEqualTo(8.0);
    }

    @Test
    void shouldIdentifyH1AsTitle() {
        String source = scorer.getSourceFromHeadingLevel(1);
        assertThat(source).isEqualTo(KeywordScorer.SOURCE_TITLE);
    }

    @Test
    void shouldIdentifyH2AsSection() {
        String source = scorer.getSourceFromHeadingLevel(2);
        assertThat(source).isEqualTo(KeywordScorer.SOURCE_SECTION);
    }

    @Test
    void shouldParseH1HeadingLevel() {
        int level = scorer.parseHeadingLevel("= Document Title");
        assertThat(level).isEqualTo(1);
    }

    @Test
    void shouldParseH2HeadingLevel() {
        int level = scorer.parseHeadingLevel("== Section Title");
        assertThat(level).isEqualTo(2);
    }

    @Test
    void shouldParseH3HeadingLevel() {
        int level = scorer.parseHeadingLevel("=== Subsection Title");
        assertThat(level).isEqualTo(3);
    }

    @Test
    void shouldReturnZeroForNonHeading() {
        int level = scorer.parseHeadingLevel("Regular text content");
        assertThat(level).isEqualTo(0);
    }

    @Test
    void shouldReturnZeroForBlankLine() {
        int level = scorer.parseHeadingLevel("   ");
        assertThat(level).isEqualTo(0);
    }

    @Test
    void shouldReturnZeroForNullLine() {
        int level = scorer.parseHeadingLevel(null);
        assertThat(level).isEqualTo(0);
    }

    @Test
    void shouldHandleMultiWordTitle() {
        // Verify parsing works with multi-word titles
        int level = scorer.parseHeadingLevel("= Security and Authentication Guide");
        assertThat(level).isEqualTo(1);
    }

    @Test
    void shouldHandleSpecialCharactersInTitle() {
        int level = scorer.parseHeadingLevel("= OAuth2 / OIDC Configuration");
        assertThat(level).isEqualTo(1);
    }

    @Test
    void shouldCalculateScoreForTitleKeyword() {
        double score = scorer.calculateScore(KeywordScorer.SOURCE_TITLE, 1);
        // 8.0 * 1.0 = 8.0
        assertThat(score).isCloseTo(8.0, within(0.001));
    }

    // ========================================
    // R5: Compound Score Calculation
    // ========================================

    @Test
    void shouldReturnBodyWeightMultiplier() {
        double multiplier = scorer.getMultiplier(KeywordScorer.SOURCE_BODY);
        assertThat(multiplier).isEqualTo(1.0);
    }

    @Test
    void shouldReturnBodyWeightForUnknownSource() {
        double multiplier = scorer.getMultiplier("unknown");
        assertThat(multiplier).isEqualTo(1.0);
    }

    @Test
    void shouldReturnBodyWeightForNullSource() {
        double multiplier = scorer.getMultiplier(null);
        assertThat(multiplier).isEqualTo(1.0);
    }

    @Test
    void shouldCalculateFrequencyFactorForSingleOccurrence() {
        double factor = scorer.calculateFrequencyFactor(1);
        // 1.0 + log(1) = 1.0 + 0 = 1.0
        assertThat(factor).isCloseTo(1.0, within(0.001));
    }

    @Test
    void shouldCalculateFrequencyFactorForMultipleOccurrences() {
        double factor = scorer.calculateFrequencyFactor(3);
        // 1.0 + log(3) ≈ 1.0 + 1.099 ≈ 2.0 (capped)
        assertThat(factor).isCloseTo(2.0, within(0.001));
    }

    @Test
    void shouldCapFrequencyFactorAtTwo() {
        double factor = scorer.calculateFrequencyFactor(100);
        assertThat(factor).isEqualTo(2.0);
    }

    @Test
    void shouldReturnZeroFrequencyFactorForZeroCount() {
        double factor = scorer.calculateFrequencyFactor(0);
        assertThat(factor).isEqualTo(0.0);
    }

    @Test
    void shouldReturnZeroFrequencyFactorForNegativeCount() {
        double factor = scorer.calculateFrequencyFactor(-1);
        assertThat(factor).isEqualTo(0.0);
    }

    @Test
    void shouldUseHighestWeightWhenKeywordInMultipleLocations() {
        List<String> sources = List.of(
                KeywordScorer.SOURCE_BODY,
                KeywordScorer.SOURCE_SECTION,
                KeywordScorer.SOURCE_FILENAME
        );
        double highestMultiplier = scorer.getHighestMultiplier(sources);
        assertThat(highestMultiplier).isEqualTo(10.0); // filename weight
    }

    @Test
    void shouldReturnBodyWeightForEmptySourceList() {
        double highestMultiplier = scorer.getHighestMultiplier(List.of());
        assertThat(highestMultiplier).isEqualTo(1.0);
    }

    @Test
    void shouldReturnBodyWeightForNullSourceList() {
        double highestMultiplier = scorer.getHighestMultiplier(null);
        assertThat(highestMultiplier).isEqualTo(1.0);
    }

    @Test
    void shouldCombineScoresFromMultipleSources() {
        List<KeywordScorer.SourceFrequency> scores = List.of(
                new KeywordScorer.SourceFrequency(KeywordScorer.SOURCE_BODY, 2),
                new KeywordScorer.SourceFrequency(KeywordScorer.SOURCE_SECTION, 1),
                new KeywordScorer.SourceFrequency(KeywordScorer.SOURCE_TITLE, 1)
        );
        double combined = scorer.combineScores(scores);
        // Uses highest weight (title = 8.0) with total frequency 4
        // 8.0 * min(1.0 + log(4), 2.0) = 8.0 * min(2.386, 2.0) = 8.0 * 2.0 = 16.0
        assertThat(combined).isCloseTo(16.0, within(0.001));
    }

    @Test
    void shouldReturnZeroForEmptyScoresList() {
        double combined = scorer.combineScores(List.of());
        assertThat(combined).isEqualTo(0.0);
    }

    @Test
    void shouldReturnZeroForNullScoresList() {
        double combined = scorer.combineScores(null);
        assertThat(combined).isEqualTo(0.0);
    }

    @Test
    void shouldApplyFrequencyFactorAfterLocationWeight() {
        // Frequency = 2: factor = min(1.0 + log(2), 2.0) ≈ 1.693
        double score = scorer.calculateScore(KeywordScorer.SOURCE_SECTION, 2);
        // 5.0 * 1.693 ≈ 8.466
        assertThat(score).isCloseTo(8.466, within(0.01));
    }

    @Test
    void shouldCalculateScoreWithBaseScore() {
        double score = scorer.calculateScore(2.0, KeywordScorer.SOURCE_SECTION, 1);
        // 2.0 * 5.0 * 1.0 = 10.0
        assertThat(score).isCloseTo(10.0, within(0.001));
    }

    // ========================================
    // R6: Additional Edge Cases
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
        // Single character tokens should be filtered
        List<String> keywords = scorer.extractFilenameKeywords("a-b-config.adoc");
        assertThat(keywords).contains("config");
        assertThat(keywords).doesNotContain("a", "b");
    }

    @Test
    void shouldHandleMixedSeparators() {
        List<String> keywords = scorer.extractFilenameKeywords("rest-client_config.adoc");
        assertThat(keywords).containsExactlyInAnyOrder("rest", "client", "config");
    }

    @Test
    void shouldPreserveRawScoresForRanking() {
        // Different sources should produce different raw scores
        double filenameScore = scorer.calculateScore(KeywordScorer.SOURCE_FILENAME, 1);
        double titleScore = scorer.calculateScore(KeywordScorer.SOURCE_TITLE, 1);
        double sectionScore = scorer.calculateScore(KeywordScorer.SOURCE_SECTION, 1);
        double bodyScore = scorer.calculateScore(KeywordScorer.SOURCE_BODY, 1);

        assertThat(filenameScore).isGreaterThan(titleScore);
        assertThat(titleScore).isGreaterThan(sectionScore);
        assertThat(sectionScore).isGreaterThan(bodyScore);
    }

    @Test
    void shouldNotTreatEqualsSignsWithoutSpaceAsHeading() {
        int level = scorer.parseHeadingLevel("==NoSpace");
        assertThat(level).isEqualTo(0);
    }

    @Test
    void shouldHandleHeadingWithLeadingWhitespace() {
        int level = scorer.parseHeadingLevel("   == Section Title");
        assertThat(level).isEqualTo(2);
    }
}
