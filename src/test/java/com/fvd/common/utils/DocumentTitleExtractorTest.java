package com.fvd.common.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentTitleExtractorTest {

    @Test
    void extractTitleWithValidAsciidocTitle() {
        String content = "= My Document Title\n\nSome content here.";

        assertThat(DocumentTitleExtractor.extractTitle(content)).isEqualTo("My Document Title");
    }

    @Test
    void extractTitleWithNullContent() {
        assertThat(DocumentTitleExtractor.extractTitle(null)).isEmpty();
    }

    @Test
    void extractTitleWithBlankContent() {
        assertThat(DocumentTitleExtractor.extractTitle("   ")).isEmpty();
    }

    @Test
    void extractTitleWithEmptyContent() {
        assertThat(DocumentTitleExtractor.extractTitle("")).isEmpty();
    }

    @Test
    void extractTitleWithNoTitle() {
        String content = "Some content without a title.\nAnother line.";

        assertThat(DocumentTitleExtractor.extractTitle(content)).isEmpty();
    }

    @Test
    void extractTitleWithExtraWhitespace() {
        String content = "=   My Spaced Title   \n\nContent.";

        assertThat(DocumentTitleExtractor.extractTitle(content)).isEqualTo("My Spaced Title");
    }

    @Test
    void extractTitleMatchesOnlyFirstLevelOneTitle() {
        String content = "= First Title\n\n== Section Header\n\n= Second Title";

        assertThat(DocumentTitleExtractor.extractTitle(content)).isEqualTo("First Title");
    }

    @Test
    void extractTitleIgnoresSectionHeaders() {
        String content = "== Not a Title\n\n=== Also Not\n\nSome text.";

        assertThat(DocumentTitleExtractor.extractTitle(content)).isEmpty();
    }

    @Test
    void extractTitleFromContentWithMetadata() {
        String content = ":description: Some description\n= The Real Title\n\nContent here.";

        assertThat(DocumentTitleExtractor.extractTitle(content)).isEqualTo("The Real Title");
    }
}
