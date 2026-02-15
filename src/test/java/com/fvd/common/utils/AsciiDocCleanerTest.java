package com.fvd.common.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AsciiDocCleanerTest {

    @Test
    void stripsIncludeDirective() {
        assertThat(AsciiDocCleaner.clean("include::_attributes.adoc[]")).isEmpty();
    }

    @Test
    void stripsCommentBlock() {
        String input = "before\n////\ncomment\n////\nafter";
        assertThat(AsciiDocCleaner.clean(input)).isEqualTo("before\n\nafter");
    }

    @Test
    void stripsAttributeDeclaration() {
        assertThat(AsciiDocCleaner.clean(":categories: web, rest")).isEmpty();
    }

    @Test
    void stripsIfdefEndif() {
        String input = "ifdef::env[]\ntext\nendif::[]";
        assertThat(AsciiDocCleaner.clean(input)).isEqualTo("text");
    }

    @Test
    void stripsBlockAttributeId() {
        assertThat(AsciiDocCleaner.clean("[id=\"getting-started\"]")).isEmpty();
    }

    @Test
    void stripsBlockAttributeRole() {
        assertThat(AsciiDocCleaner.clean("[.discrete]")).isEmpty();
    }

    @Test
    void extractsXrefLinkText() {
        assertThat(AsciiDocCleaner.clean("see xref:security.adoc[Security Guide]"))
                .isEqualTo("see Security Guide");
    }

    @Test
    void extractsLinkText() {
        assertThat(AsciiDocCleaner.clean("visit link:https://example.com[Example]"))
                .isEqualTo("visit Example");
    }

    @Test
    void extractsInlineXrefText() {
        assertThat(AsciiDocCleaner.clean("see <<security,Security section>>"))
                .isEqualTo("see Security section");
    }

    @Test
    void handlesEmptyXref() {
        assertThat(AsciiDocCleaner.clean("xref:path.adoc[]")).isEmpty();
    }

    @Test
    void preservesNormalText() {
        assertThat(AsciiDocCleaner.clean("Hello world")).isEqualTo("Hello world");
    }

    @Test
    void handlesNullInput() {
        assertThat(AsciiDocCleaner.clean(null)).isEmpty();
    }

    @Test
    void collapsesBlanksLines() {
        assertThat(AsciiDocCleaner.clean("a\n\n\n\nb")).isEqualTo("a\n\nb");
    }

    @Test
    void stripsSourceBlockAttribute() {
        assertThat(AsciiDocCleaner.clean("[source,java]")).isEmpty();
    }

    @Test
    void handlesMixedArtifacts() {
        String input = "include::_attributes.adoc[]\n" +
                ":categories: web\n" +
                "This guide covers xref:security.adoc[Security] features.\n" +
                "ifdef::env[]\nvisible\nendif::[]";
        String result = AsciiDocCleaner.clean(input);
        assertThat(result).contains("This guide covers Security features.");
        assertThat(result).doesNotContain("include::");
        assertThat(result).doesNotContain(":categories:");
        assertThat(result).doesNotContain("ifdef::");
        assertThat(result).doesNotContain("xref:");
    }
}
