package com.fvd.search.services;

import org.junit.jupiter.api.Test;

import java.util.List;
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
}
