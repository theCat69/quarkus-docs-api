package com.fvd.search.services;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SearchKeywordsTest {

    @Test
    void prepareStemsAndLowersKeywords() {
        Set<String> result = SearchKeywords.prepare(List.of("Security", "Configuration"));

        // "Security" → lowercase "security" → stem "secur"
        // "Configuration" → lowercase "configuration" → stem "configur"
        assertThat(result).containsExactlyInAnyOrder("secur", "configur");
    }

    @Test
    void prepareDeduplicatesStemmedKeywords() {
        // "security" and "secured" both stem to "secur"
        Set<String> result = SearchKeywords.prepare(List.of("security", "secured"));

        assertThat(result).hasSize(1);
        assertThat(result).containsExactly("secur");
    }

    @Test
    void prepareEmptyListReturnsEmptySet() {
        Set<String> result = SearchKeywords.prepare(List.of());

        assertThat(result).isEmpty();
    }

    @Test
    void prepareWithOriginalsReturnsStemmedToOriginalMapping() {
        Map<String, String> result = SearchKeywords.prepareWithOriginals(
                List.of("Security", "Configuration"));

        assertThat(result).containsEntry("secur", "security");
        assertThat(result).containsEntry("configur", "configuration");
    }

    @Test
    void prepareWithOriginalsDuplicateStemsKeepsFirstOriginal() {
        // "security" and "secured" both stem to "secur", should keep "security"
        Map<String, String> result = SearchKeywords.prepareWithOriginals(
                List.of("security", "secured"));

        assertThat(result).hasSize(1);
        assertThat(result).containsEntry("secur", "security");
    }

    @Test
    void prepareWithOriginalsEmptyListReturnsEmptyMap() {
        Map<String, String> result = SearchKeywords.prepareWithOriginals(List.of());

        assertThat(result).isEmpty();
    }

    @Test
    void prepareWithOriginalsAlreadyStemmedMapsToSelf() {
        // "rest" stems to "rest" — should map to itself
        Map<String, String> result = SearchKeywords.prepareWithOriginals(List.of("rest"));

        assertThat(result).containsEntry("rest", "rest");
    }
}
