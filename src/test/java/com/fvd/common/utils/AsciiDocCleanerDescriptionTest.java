package com.fvd.common.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AsciiDocCleanerDescriptionTest {

    @Test
    void stripsAdmonitionLabelsAndBlockDelimiters() {
        String input = "[WARNING]\n====\ntext\n====\nmore text";
        assertThat(AsciiDocCleaner.cleanDescription(input)).isEqualTo("text more text");
    }

    @Test
    void stripsAttributeReferences() {
        assertThat(AsciiDocCleaner.cleanDescription("Use {quarkus-version} for your project"))
                .isEqualTo("Use for your project");
    }

    @Test
    void stripsImageMacros() {
        assertThat(AsciiDocCleaner.cleanDescription("See image::diagram.png[arch] for details"))
                .isEqualTo("See for details");
    }

    @Test
    void stripsInlineFormatting() {
        assertThat(AsciiDocCleaner.cleanDescription("Use *bold* and _italic_ and `mono`"))
                .isEqualTo("Use bold and italic and mono");
    }

    @Test
    void stripsPassthroughMacro() {
        assertThat(AsciiDocCleaner.cleanDescription("Use pass:[HTML] here"))
                .isEqualTo("Use here");
    }

    @Test
    void stripsStemMacro() {
        assertThat(AsciiDocCleaner.cleanDescription("Formula stem:[x^2] result"))
                .isEqualTo("Formula result");
    }

    @Test
    void stripsCalloutNumbers() {
        assertThat(AsciiDocCleaner.cleanDescription("int x = 0; <1>"))
                .isEqualTo("int x = 0;");
    }

    @Test
    void handlesCombinedPatterns() {
        String input = "[WARNING]\n====\nThis feature is *experimental*.\n====\n" +
                "Use {project-name} with `quarkus-config` and pass:[content] here.";
        String result = AsciiDocCleaner.cleanDescription(input);
        assertThat(result).doesNotContain("[WARNING]");
        assertThat(result).doesNotContain("====");
        assertThat(result).doesNotContain("*experimental*");
        assertThat(result).doesNotContain("{project-name}");
        assertThat(result).doesNotContain("`quarkus-config`");
        assertThat(result).doesNotContain("pass:[content]");
        assertThat(result).contains("experimental");
        assertThat(result).contains("quarkus-config");
        assertThat(result).contains("here");
    }

    @Test
    void handlesNullInput() {
        assertThat(AsciiDocCleaner.cleanDescription(null)).isEmpty();
    }

    @Test
    void cleanTextUnchanged() {
        assertThat(AsciiDocCleaner.cleanDescription("Simple plain text"))
                .isEqualTo("Simple plain text");
    }

    @Test
    void normalizesWhitespace() {
        assertThat(AsciiDocCleaner.cleanDescription("hello   world\n\nnext"))
                .isEqualTo("hello world next");
    }

    @Test
    void stripsInlineAdmonitionPrefixes() {
        assertThat(AsciiDocCleaner.cleanDescription("WARNING: Be careful here"))
                .isEqualTo("Be careful here");
    }

    @Test
    void existingCleanMethodUnchangedByCleanDescription() {
        // Verify that clean() still works the same
        assertThat(AsciiDocCleaner.clean("include::_attributes.adoc[]")).isEmpty();
        assertThat(AsciiDocCleaner.clean("Hello world")).isEqualTo("Hello world");
        assertThat(AsciiDocCleaner.clean(null)).isEmpty();
    }

    @Test
    void stripsDoubleBold() {
        assertThat(AsciiDocCleaner.cleanDescription("Use **strong** emphasis"))
                .isEqualTo("Use strong emphasis");
    }

    @Test
    void stripsDoubleItalic() {
        assertThat(AsciiDocCleaner.cleanDescription("Use __italic__ emphasis"))
                .isEqualTo("Use italic emphasis");
    }

    @Test
    void admonitionBlockDelimiterOnly() {
        assertThat(AsciiDocCleaner.cleanDescription("====\ncontent\n===="))
                .isEqualTo("content");
    }

    @Test
    void stripsMultipleCalloutNumbers() {
        assertThat(AsciiDocCleaner.cleanDescription("line <1> and <2> end"))
                .isEqualTo("line and end");
    }
}
