package com.fvd.search.services;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SnippetHighlighterTest {

    @Test
    void shouldHighlightSingleKeyword() {
        String result = SnippetHighlighter.highlight("Quarkus provides security", Set.of("security"));
        assertThat(result).isEqualTo("Quarkus provides **security**");
    }

    @Test
    void shouldHighlightMultipleKeywords() {
        String result = SnippetHighlighter.highlight(
                "security and authentication are important",
                Set.of("security", "authentication"));
        assertThat(result).isEqualTo("**security** and **authentication** are important");
    }

    @Test
    void shouldBeCaseInsensitive() {
        String result = SnippetHighlighter.highlight("Security is important", Set.of("security"));
        assertThat(result).isEqualTo("**Security** is important");
    }

    @Test
    void shouldHandleNullSnippet() {
        String result = SnippetHighlighter.highlight(null, Set.of("security"));
        assertThat(result).isNull();
    }

    @Test
    void shouldHandleEmptySnippet() {
        String result = SnippetHighlighter.highlight("", Set.of("security"));
        assertThat(result).isEmpty();
    }

    @Test
    void shouldHandleNullKeywords() {
        String result = SnippetHighlighter.highlight("Quarkus provides security", null);
        assertThat(result).isEqualTo("Quarkus provides security");
    }

    @Test
    void shouldHandleEmptyKeywords() {
        String result = SnippetHighlighter.highlight("Quarkus provides security", List.of());
        assertThat(result).isEqualTo("Quarkus provides security");
    }

    @Test
    void shouldRespectWordBoundary() {
        String result = SnippetHighlighter.highlight("This section covers rest topics", Set.of("sec"));
        assertThat(result).isEqualTo("This section covers rest topics");
    }

    @Test
    void shouldHighlightLongestFirst() {
        String result = SnippetHighlighter.highlight(
                "Check the configuration guide", Set.of("config", "configuration"));
        assertThat(result).isEqualTo("Check the **configuration** guide");
    }

    @Test
    void shouldNotDoubleWrap() {
        String result = SnippetHighlighter.highlight("Check **security** settings", Set.of("security"));
        assertThat(result).isEqualTo("Check **security** settings");
    }
}
