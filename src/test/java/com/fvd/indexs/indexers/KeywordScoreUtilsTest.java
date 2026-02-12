package com.fvd.indexs.indexers;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KeywordScoreUtilsTest {

    @Test
    void toSortedScoresSortsDescendingByScore() {
        Map<String, Integer> keywords = Map.of(
                "low", 3,
                "high", 20,
                "mid", 10);

        List<KeywordScore> result = KeywordScoreUtils.toSortedScores(keywords);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).word).isEqualTo("high");
        assertThat(result.get(0).score).isEqualTo(20);
        assertThat(result.get(1).word).isEqualTo("mid");
        assertThat(result.get(1).score).isEqualTo(10);
        assertThat(result.get(2).word).isEqualTo("low");
        assertThat(result.get(2).score).isEqualTo(3);
    }

    @Test
    void toSortedScoresEmptyMapReturnsEmptyList() {
        List<KeywordScore> result = KeywordScoreUtils.toSortedScores(Map.of());

        assertThat(result).isEmpty();
    }

    @Test
    void toSortedScoresSingleEntry() {
        Map<String, Integer> keywords = Map.of("secur", 15);

        List<KeywordScore> result = KeywordScoreUtils.toSortedScores(keywords);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).word).isEqualTo("secur");
        assertThat(result.get(0).score).isEqualTo(15);
    }
}
