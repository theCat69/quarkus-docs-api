package com.fvd.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StopWordsTest {

    @Test
    void defaultSetIsNotEmpty() {
        assertThat(StopWords.DEFAULT).isNotEmpty();
    }

    @Test
    void defaultSetContainsExactly35Words() {
        assertThat(StopWords.DEFAULT).hasSize(35);
    }

    @Test
    void defaultSetContainsKnownStopWords() {
        assertThat(StopWords.DEFAULT).contains("a", "the", "and", "is", "for", "of", "please", "your");
    }

    @Test
    void defaultSetIsImmutable() {
        assertThatThrownBy(() -> StopWords.DEFAULT.add("test"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
