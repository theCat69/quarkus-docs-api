package com.fvd.common.matchers;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class FuzzyMatcherTest {

    @Test
    void levenshteinSimilarityIdenticalStrings() {
        assertThat(FuzzyMatcher.levenshteinSimilarity("hello", "hello")).isEqualTo(1.0);
    }

    @Test
    void levenshteinSimilarityCaseInsensitive() {
        assertThat(FuzzyMatcher.levenshteinSimilarity("Hello", "hello")).isEqualTo(1.0);
    }

    @Test
    void levenshteinSimilarityCompletelyDifferent() {
        double score = FuzzyMatcher.levenshteinSimilarity("abc", "xyz");
        assertThat(score).isLessThan(0.5);
    }

    @Test
    void levenshteinSimilarityMinorTypo() {
        double score = FuzzyMatcher.levenshteinSimilarity("Overview", "Overvew");
        assertThat(score).isGreaterThan(0.7);
    }

    @Test
    void levenshteinSimilarityNullInputs() {
        assertThat(FuzzyMatcher.levenshteinSimilarity(null, "hello")).isEqualTo(0.0);
        assertThat(FuzzyMatcher.levenshteinSimilarity("hello", null)).isEqualTo(0.0);
    }

    @Test
    void containmentScoreFullContainment() {
        double score = FuzzyMatcher.containmentScore("Overview", "Security Overview");
        assertThat(score).isGreaterThan(0.3);
    }

    @Test
    void containmentScoreNoContainment() {
        double score = FuzzyMatcher.containmentScore("xyz", "Security Overview");
        assertThat(score).isEqualTo(0.0);
    }

    @Test
    void containmentScoreNullInputs() {
        assertThat(FuzzyMatcher.containmentScore(null, "hello")).isEqualTo(0.0);
        assertThat(FuzzyMatcher.containmentScore("hello", null)).isEqualTo(0.0);
    }

    @Test
    void wordOverlapScorePartialOverlap() {
        double score = FuzzyMatcher.wordOverlapScore("security config", "Security Overview");
        assertThat(score).isGreaterThan(0.0);
        assertThat(score).isLessThan(1.0);
    }

    @Test
    void wordOverlapScoreNoOverlap() {
        double score = FuzzyMatcher.wordOverlapScore("xyz abc", "Security Overview");
        assertThat(score).isEqualTo(0.0);
    }

    @Test
    void wordOverlapScoreFullOverlap() {
        double score = FuzzyMatcher.wordOverlapScore("Security Overview", "Security Overview");
        assertThat(score).isEqualTo(1.0);
    }

    @Test
    void wordOverlapScoreNullInputs() {
        assertThat(FuzzyMatcher.wordOverlapScore(null, "hello")).isEqualTo(0.0);
    }

    @Test
    void bestMatchReturnsExactMatch() {
        List<String> candidates = List.of("Overview", "Configuration", "Security");
        Optional<FuzzyMatcher.MatchResult> result = FuzzyMatcher.bestMatch("Overview", candidates);

        assertThat(result).isPresent();
        assertThat(result.get().value()).isEqualTo("Overview");
        assertThat(result.get().score()).isEqualTo(1.0);
        assertThat(result.get().matchType()).isEqualTo("exact");
    }

    @Test
    void bestMatchReturnsExactMatchCaseInsensitive() {
        List<String> candidates = List.of("Overview", "Configuration", "Security");
        Optional<FuzzyMatcher.MatchResult> result = FuzzyMatcher.bestMatch("overview", candidates);

        assertThat(result).isPresent();
        assertThat(result.get().value()).isEqualTo("Overview");
        assertThat(result.get().score()).isEqualTo(1.0);
        assertThat(result.get().matchType()).isEqualTo("exact");
    }

    @Test
    void bestMatchReturnsFuzzyMatchForPartialTitle() {
        List<String> candidates = List.of("Security Overview", "Configuration Guide", "Getting Started");
        Optional<FuzzyMatcher.MatchResult> result = FuzzyMatcher.bestMatch("Security", candidates);

        assertThat(result).isPresent();
        assertThat(result.get().value()).isEqualTo("Security Overview");
        assertThat(result.get().score()).isGreaterThan(0.3);
        assertThat(result.get().matchType()).isNotEqualTo("exact");
    }

    @Test
    void bestMatchReturnsFuzzyMatchForTypo() {
        List<String> candidates = List.of("Overview", "Configuration", "Security");
        Optional<FuzzyMatcher.MatchResult> result = FuzzyMatcher.bestMatch("Overvew", candidates);

        assertThat(result).isPresent();
        assertThat(result.get().value()).isEqualTo("Overview");
        assertThat(result.get().score()).isGreaterThan(0.3);
    }

    @Test
    void bestMatchReturnsEmptyWhenBelowThreshold() {
        List<String> candidates = List.of("Security Overview", "Configuration Guide");
        Optional<FuzzyMatcher.MatchResult> result = FuzzyMatcher.bestMatch("xyzabc123", candidates);

        assertThat(result).isEmpty();
    }

    @Test
    void bestMatchReturnsEmptyForNullQuery() {
        Optional<FuzzyMatcher.MatchResult> result = FuzzyMatcher.bestMatch(null, List.of("Overview"));
        assertThat(result).isEmpty();
    }

    @Test
    void bestMatchReturnsEmptyForEmptyCandidates() {
        Optional<FuzzyMatcher.MatchResult> result = FuzzyMatcher.bestMatch("Overview", List.of());
        assertThat(result).isEmpty();
    }

    @Test
    void bestMatchSelectsBestCandidateAmongMultiple() {
        List<String> candidates = List.of(
                "Security Overview",
                "Security Configuration",
                "Getting Started with Security"
        );
        Optional<FuzzyMatcher.MatchResult> result = FuzzyMatcher.bestMatch("Security Overview", candidates);

        assertThat(result).isPresent();
        assertThat(result.get().value()).isEqualTo("Security Overview");
        assertThat(result.get().score()).isEqualTo(1.0);
        assertThat(result.get().matchType()).isEqualTo("exact");
    }

    @Test
    void bestMatchWithCustomThreshold() {
        List<String> candidates = List.of("Security Overview", "Configuration Guide");
        // High threshold — partial match should fail
        Optional<FuzzyMatcher.MatchResult> result = FuzzyMatcher.bestMatch("Security", candidates, 0.95);
        assertThat(result).isEmpty();

        // Low threshold — should succeed
        result = FuzzyMatcher.bestMatch("Security", candidates, 0.3);
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
        Optional<FuzzyMatcher.MatchResult> result = FuzzyMatcher.bestMatch("authentication", candidates);

        assertThat(result).isPresent();
        assertThat(result.get().value()).isEqualTo("Authentication Methods");
    }

    @Test
    void combinedScoreIsReasonableForSimilarStrings() {
        double score = FuzzyMatcher.combinedScore("Security Overview", "Security Overview Guide");
        assertThat(score).isGreaterThan(0.5);
    }

    @Test
    void combinedScoreIsLowForDissimilarStrings() {
        double score = FuzzyMatcher.combinedScore("abc", "xyz123456");
        assertThat(score).isLessThan(0.3);
    }
}
