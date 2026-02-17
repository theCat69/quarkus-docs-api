package com.fvd.api.dto;

import com.fvd.common.StopWords;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SearchSyntaxResponseTest {

    @Test
    void instanceShouldNotBeNull() {
        assertThat(SearchSyntaxResponse.INSTANCE).isNotNull();
    }

    @Test
    void allSectionsShouldBePopulated() {
        SearchSyntaxResponse instance = SearchSyntaxResponse.INSTANCE;

        assertThat(instance.tokenization).isNotNull();
        assertThat(instance.stemming).isNotNull();
        assertThat(instance.scoring).isNotNull();
        assertThat(instance.stopWords).isNotNull();
        assertThat(instance.fuzzyMatching).isNotNull();
        assertThat(instance.supported).isNotNull();
        assertThat(instance.unsupported).isNotNull();
        assertThat(instance.filters).isNotNull();
        assertThat(instance.examples).isNotNull();
        assertThat(instance.tips).isNotNull();
    }

    @Test
    void stopWordsShouldMatchStopWordsDefault() {
        List<String> responseWords = SearchSyntaxResponse.INSTANCE.stopWords.words;

        assertThat(responseWords).containsExactlyInAnyOrderElementsOf(StopWords.DEFAULT);
    }

    @Test
    void stopWordsShouldBeSorted() {
        List<String> words = SearchSyntaxResponse.INSTANCE.stopWords.words;
        List<String> sorted = new ArrayList<>(words);
        sorted.sort(String::compareTo);

        assertThat(words).isEqualTo(sorted);
    }

    @Test
    void stemmingExamplesShouldBePopulated() {
        List<SearchSyntaxResponse.StemmingExample> examples = SearchSyntaxResponse.INSTANCE.stemming.examples;

        assertThat(examples).isNotEmpty();
        for (SearchSyntaxResponse.StemmingExample example : examples) {
            assertThat(example.input).as("input should not be blank").isNotBlank();
            assertThat(example.stemmed).as("stemmed should not be blank").isNotBlank();
            assertThat(example.alsoMatches).as("alsoMatches should not be empty").isNotEmpty();
        }
    }
}
