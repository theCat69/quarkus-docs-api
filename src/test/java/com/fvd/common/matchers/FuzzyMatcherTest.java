package com.fvd.common.matchers;

import com.fvd.search.TestSearchConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class FuzzyMatcherTest {

    private final FuzzyMatcher fuzzyMatcher = new FuzzyMatcher(new TestSearchConfig());

    @Test
    void levenshteinSimilarityIdenticalStrings() {
        assertThat(fuzzyMatcher.levenshteinSimilarity("hello", "hello")).isEqualTo(1.0);
    }

    @Test
    void levenshteinSimilarityCaseInsensitive() {
        assertThat(fuzzyMatcher.levenshteinSimilarity("Hello", "hello")).isEqualTo(1.0);
    }

    @Test
    void levenshteinSimilarityCompletelyDifferent() {
        double score = fuzzyMatcher.levenshteinSimilarity("abc", "xyz");
        assertThat(score).isLessThan(0.5);
    }

    @Test
    void levenshteinSimilarityMinorTypo() {
        double score = fuzzyMatcher.levenshteinSimilarity("Overview", "Overvew");
        assertThat(score).isGreaterThan(0.7);
    }

    @Test
    void levenshteinSimilarityNullInputs() {
        assertThat(fuzzyMatcher.levenshteinSimilarity(null, "hello")).isEqualTo(0.0);
        assertThat(fuzzyMatcher.levenshteinSimilarity("hello", null)).isEqualTo(0.0);
    }

    @Test
    void containmentScoreFullContainment() {
        double score = fuzzyMatcher.containmentScore("Overview", "Security Overview");
        assertThat(score).isGreaterThan(0.3);
    }

    @Test
    void containmentScoreNoContainment() {
        double score = fuzzyMatcher.containmentScore("xyz", "Security Overview");
        assertThat(score).isEqualTo(0.0);
    }

    @Test
    void containmentScoreNullInputs() {
        assertThat(fuzzyMatcher.containmentScore(null, "hello")).isEqualTo(0.0);
        assertThat(fuzzyMatcher.containmentScore("hello", null)).isEqualTo(0.0);
    }

    @Test
    void wordOverlapScorePartialOverlap() {
        double score = fuzzyMatcher.wordOverlapScore("security config", "Security Overview");
        assertThat(score).isGreaterThan(0.0);
        assertThat(score).isLessThan(1.0);
    }

    @Test
    void wordOverlapScoreNoOverlap() {
        double score = fuzzyMatcher.wordOverlapScore("xyz abc", "Security Overview");
        assertThat(score).isEqualTo(0.0);
    }

    @Test
    void wordOverlapScoreFullOverlap() {
        double score = fuzzyMatcher.wordOverlapScore("Security Overview", "Security Overview");
        assertThat(score).isEqualTo(1.0);
    }

    @Test
    void wordOverlapScoreNullInputs() {
        assertThat(fuzzyMatcher.wordOverlapScore(null, "hello")).isEqualTo(0.0);
    }

    @Test
    void bestMatchReturnsExactMatch() {
        List<String> candidates = List.of("Overview", "Configuration", "Security");
        Optional<FuzzyMatcher.MatchResult> result = fuzzyMatcher.bestMatch("Overview", candidates);

        assertThat(result).isPresent();
        assertThat(result.get().value()).isEqualTo("Overview");
        assertThat(result.get().score()).isEqualTo(1.0);
        assertThat(result.get().matchType()).isEqualTo("exact");
    }

    @Test
    void bestMatchReturnsExactMatchCaseInsensitive() {
        List<String> candidates = List.of("Overview", "Configuration", "Security");
        Optional<FuzzyMatcher.MatchResult> result = fuzzyMatcher.bestMatch("overview", candidates);

        assertThat(result).isPresent();
        assertThat(result.get().value()).isEqualTo("Overview");
        assertThat(result.get().score()).isEqualTo(1.0);
        assertThat(result.get().matchType()).isEqualTo("exact");
    }

    @Test
    void bestMatchReturnsFuzzyMatchForPartialTitle() {
        List<String> candidates = List.of("Security Overview", "Configuration Guide", "Getting Started");
        Optional<FuzzyMatcher.MatchResult> result = fuzzyMatcher.bestMatch("Security", candidates);

        assertThat(result).isPresent();
        assertThat(result.get().value()).isEqualTo("Security Overview");
        assertThat(result.get().score()).isGreaterThan(0.3);
        assertThat(result.get().matchType()).isNotEqualTo("exact");
    }

    @Test
    void bestMatchReturnsFuzzyMatchForTypo() {
        List<String> candidates = List.of("Overview", "Configuration", "Security");
        Optional<FuzzyMatcher.MatchResult> result = fuzzyMatcher.bestMatch("Overvew", candidates);

        assertThat(result).isPresent();
        assertThat(result.get().value()).isEqualTo("Overview");
        assertThat(result.get().score()).isGreaterThan(0.3);
    }

    @Test
    void bestMatchReturnsEmptyWhenBelowThreshold() {
        List<String> candidates = List.of("Security Overview", "Configuration Guide");
        Optional<FuzzyMatcher.MatchResult> result = fuzzyMatcher.bestMatch("xyzabc123", candidates);

        assertThat(result).isEmpty();
    }

    @Test
    void bestMatchReturnsEmptyForNullQuery() {
        Optional<FuzzyMatcher.MatchResult> result = fuzzyMatcher.bestMatch(null, List.of("Overview"));
        assertThat(result).isEmpty();
    }

    @Test
    void bestMatchReturnsEmptyForEmptyCandidates() {
        Optional<FuzzyMatcher.MatchResult> result = fuzzyMatcher.bestMatch("Overview", List.of());
        assertThat(result).isEmpty();
    }

    @Test
    void bestMatchSelectsBestCandidateAmongMultiple() {
        List<String> candidates = List.of(
                "Security Overview",
                "Security Configuration",
                "Getting Started with Security"
        );
        Optional<FuzzyMatcher.MatchResult> result = fuzzyMatcher.bestMatch("Security Overview", candidates);

        assertThat(result).isPresent();
        assertThat(result.get().value()).isEqualTo("Security Overview");
        assertThat(result.get().score()).isEqualTo(1.0);
        assertThat(result.get().matchType()).isEqualTo("exact");
    }

    @Test
    void bestMatchWithCustomThreshold() {
        List<String> candidates = List.of("Security Overview", "Configuration Guide");
        // High threshold — partial match should fail
        Optional<FuzzyMatcher.MatchResult> result = fuzzyMatcher.bestMatch("Security", candidates, 0.95);
        assertThat(result).isEmpty();

        // Low threshold — should succeed
        result = fuzzyMatcher.bestMatch("Security", candidates, 0.3);
        assertThat(result).isPresent();
        assertThat(result.get().value()).isEqualTo("Security Overview");
    }

    @Test
    void bestMatchKeywordOverlap() {
        List<String> candidates = List.of(
                "Authentication Methods",
                "Authorization and Roles",
                "OIDC Configuration"
        );
        Optional<FuzzyMatcher.MatchResult> result = fuzzyMatcher.bestMatch("authentication", candidates);

        assertThat(result).isPresent();
        assertThat(result.get().value()).isEqualTo("Authentication Methods");
    }

    @Test
    void combinedScoreIsReasonableForSimilarStrings() {
        double score = fuzzyMatcher.combinedScore("Security Overview", "Security Overview Guide");
        assertThat(score).isGreaterThan(0.5);
    }

    @Test
    void combinedScoreIsLowForDissimilarStrings() {
        double score = fuzzyMatcher.combinedScore("abc", "xyz123456");
        assertThat(score).isLessThan(0.3);
    }
}
