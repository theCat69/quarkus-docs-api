package com.fvd.search.services;

import com.fvd.indexs.indexers.KeywordScore;
import com.fvd.repository.domain.MatchedKeyword;
import com.fvd.search.TestSearchConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SqliteSearchScorerTest {

    SqliteSearchScorer scorer;

    @BeforeEach
    void setUp() {
        scorer = new SqliteSearchScorer(new TestSearchConfig());
    }

    @Test
    void exactMatchReturnsFullScore() {
        List<KeywordScore> indexed = List.of(new KeywordScore("secur", 10));
        Set<String> query = Set.of("secur");

        SearchScorer.MatchResult result = scorer.computeScore(indexed, query);

        assertThat(result.score()).isEqualTo(10.0);
        assertThat(result.matchedCount()).isEqualTo(1);
        assertThat(result.hasMatches()).isTrue();
    }

    @Test
    void prefixMatchReturnsDiscountedScore() {
        List<KeywordScore> indexed = List.of(new KeywordScore("configur", 10));
        Set<String> query = Set.of("config");

        SearchScorer.MatchResult result = scorer.computeScore(indexed, query);

        // 10 * 0.8 = 8.0
        assertThat(result.score()).isEqualTo(8.0);
        assertThat(result.matchedCount()).isEqualTo(1);
        assertThat(result.hasMatches()).isTrue();
    }

    @Test
    void exactMatchTakesPrecedenceOverPrefix() {
        // "secur" is exact match for query "secur", should get full score
        // Even though "security" would be a prefix match too
        List<KeywordScore> indexed = List.of(
                new KeywordScore("secur", 10),
                new KeywordScore("security", 5));
        Set<String> query = Set.of("secur");

        SearchScorer.MatchResult result = scorer.computeScore(indexed, query);

        // secur exact match: 10, security prefix match: 5 * 0.8 = 4.0
        // total = 10 + 4 = 14.0
        assertThat(result.score()).isEqualTo(14.0);
        assertThat(result.matchedCount()).isEqualTo(1);
        // The matched keyword should have the highest weight (10.0 from exact match)
        assertThat(result.matchedKeywords()).hasSize(1);
        assertThat(result.matchedKeywords().get(0).weight()).isEqualTo(10.0);
    }

    @Test
    void multipleKeywordsAccumulateScores() {
        List<KeywordScore> indexed = List.of(
                new KeywordScore("secur", 10),
                new KeywordScore("oidc", 8));
        Set<String> query = Set.of("secur", "oidc");

        SearchScorer.MatchResult result = scorer.computeScore(indexed, query);

        assertThat(result.score()).isEqualTo(18.0);
        assertThat(result.matchedCount()).isEqualTo(2);
        assertThat(result.hasMatches()).isTrue();
    }

    @Test
    void noMatchesReturnsEmpty() {
        List<KeywordScore> indexed = List.of(new KeywordScore("secur", 10));
        Set<String> query = Set.of("nonexistent");

        SearchScorer.MatchResult result = scorer.computeScore(indexed, query);

        assertThat(result.score()).isEqualTo(0.0);
        assertThat(result.matchedCount()).isEqualTo(0);
        assertThat(result.hasMatches()).isFalse();
        assertThat(result.matchedKeywords()).isEmpty();
    }

    @Test
    void sourcePropagatedFromKeywordScore() {
        List<KeywordScore> indexed = List.of(new KeywordScore("secur", 10, "filename", 1));
        Set<String> query = Set.of("secur");

        SearchScorer.MatchResult result = scorer.computeScore(indexed, query);

        assertThat(result.matchedKeywords()).hasSize(1);
        MatchedKeyword matched = result.matchedKeywords().get(0);
        assertThat(matched.keyword()).isEqualTo("secur");
        assertThat(matched.source()).isEqualTo("filename");
        assertThat(matched.weight()).isEqualTo(10.0);
    }

    @Test
    void highestWeightMatchWinsForSameQueryKeyword() {
        // Two indexed keywords match the same query keyword "secur"
        // "secur" exact match score 5, "security" prefix match score 20 * 0.8 = 16
        List<KeywordScore> indexed = List.of(
                new KeywordScore("secur", 5, "body", 1),
                new KeywordScore("security", 20, "filename", 1));
        Set<String> query = Set.of("secur");

        SearchScorer.MatchResult result = scorer.computeScore(indexed, query);

        // Total: 5 (exact) + 16 (prefix) = 21.0
        assertThat(result.score()).isEqualTo(21.0);
        assertThat(result.matchedCount()).isEqualTo(1);
        // Highest weight match should win (16.0 from prefix of "security")
        assertThat(result.matchedKeywords()).hasSize(1);
        assertThat(result.matchedKeywords().get(0).weight()).isEqualTo(16.0);
    }

    @Test
    void nullSourceDefaultsToBody() {
        List<KeywordScore> indexed = List.of(new KeywordScore("secur", 10, null, 1));
        Set<String> query = Set.of("secur");

        SearchScorer.MatchResult result = scorer.computeScore(indexed, query);

        assertThat(result.matchedKeywords()).hasSize(1);
        assertThat(result.matchedKeywords().get(0).source()).isEqualTo("body");
    }

    @Test
    void emptyMatchResultConstant() {
        assertThat(SearchScorer.MatchResult.EMPTY.score()).isEqualTo(0.0);
        assertThat(SearchScorer.MatchResult.EMPTY.matchedCount()).isEqualTo(0);
        assertThat(SearchScorer.MatchResult.EMPTY.matchedKeywords()).isEmpty();
        assertThat(SearchScorer.MatchResult.EMPTY.hasMatches()).isFalse();
    }
}
