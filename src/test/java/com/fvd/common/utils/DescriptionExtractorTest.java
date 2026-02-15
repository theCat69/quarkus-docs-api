package com.fvd.common.utils;

import com.fvd.asciidocs.model.DocumentMetadata;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DescriptionExtractorTest {

    @Nested
    class ExtractWithMetadata {

        @Test
        void usesSummaryFromMetadataWhenAvailable() {
            DocumentMetadata metadata = DocumentMetadata.builder()
                    .summary("OIDC Authorization Code Flow for protecting web apps")
                    .build();
            String content = "= Title\n:description: Some description\n\nFirst paragraph.";

            String result = DescriptionExtractor.extract(content, metadata);

            assertThat(result).isEqualTo("OIDC Authorization Code Flow for protecting web apps");
        }

        @Test
        void fallsBackToDescriptionAttributeWhenNoSummary() {
            DocumentMetadata metadata = DocumentMetadata.empty();
            String content = "= Title\n:description: An overview of Quarkus security\n\nFirst paragraph.";

            String result = DescriptionExtractor.extract(content, metadata);

            assertThat(result).isEqualTo("An overview of Quarkus security");
        }

        @Test
        void fallsBackToFirstParagraphWhenNoSummaryOrDescription() {
            DocumentMetadata metadata = DocumentMetadata.empty();
            String content = "= Title\n\nThis is the first paragraph of the document.";

            String result = DescriptionExtractor.extract(content, metadata);

            assertThat(result).isEqualTo("This is the first paragraph of the document.");
        }

        @Test
        void handlesNullMetadata() {
            String content = "= Title\n:description: Fallback description\n\nContent.";

            String result = DescriptionExtractor.extract(content, null);

            assertThat(result).isEqualTo("Fallback description");
        }

        @Test
        void handlesMetadataWithBlankSummary() {
            DocumentMetadata metadata = DocumentMetadata.builder()
                    .summary("   ")
                    .build();
            String content = "= Title\n:description: Desc from content\n\nFirst paragraph.";

            String result = DescriptionExtractor.extract(content, metadata);

            assertThat(result).isEqualTo("Desc from content");
        }
    }

    @Nested
    class ExtractWithoutMetadata {

        @Test
        void extractsDescriptionAttribute() {
            String content = "= Security Overview\n:description: An overview of security\n\nContent.";

            assertThat(DescriptionExtractor.extract(content))
                    .isEqualTo("An overview of security");
        }

        @Test
        void extractsFirstParagraphWhenNoDescription() {
            String content = "= My Guide\n\nThis guide covers important topics.";

            assertThat(DescriptionExtractor.extract(content))
                    .isEqualTo("This guide covers important topics.");
        }

        @Test
        void handlesNullContent() {
            assertThat(DescriptionExtractor.extract(null)).isEmpty();
        }

        @Test
        void handlesBlankContent() {
            assertThat(DescriptionExtractor.extract("   ")).isEmpty();
        }
    }

    @Nested
    class FirstParagraphExtraction {

        @Test
        void skipsIncludeDirectives() {
            String content = "= Title\n\ninclude::_attributes.adoc[]\n\nActual first paragraph.";

            assertThat(DescriptionExtractor.extract(content))
                    .isEqualTo("Actual first paragraph.");
        }

        @Test
        void skipsIfdefDirectives() {
            String content = "= Title\n\nifdef::env[]\nnot this\nendif::[]\n\nFirst paragraph.";

            assertThat(DescriptionExtractor.extract(content))
                    .isEqualTo("First paragraph.");
        }

        @Test
        void skipsAdmonitionLabels() {
            String content = "= Title\n\n[WARNING]\nFirst real paragraph after admonition.";

            assertThat(DescriptionExtractor.extract(content))
                    .isEqualTo("First real paragraph after admonition.");
        }

        @Test
        void skipsBlockDelimiters() {
            String content = "= Title\n\n====\nContent inside block\n====\n\nAfter block.";

            assertThat(DescriptionExtractor.extract(content))
                    .isEqualTo("After block.");
        }

        @Test
        void stopsAtSectionHeader() {
            String content = "= Title\n\nFirst paragraph.\n\n== Section\n\nSection content.";

            assertThat(DescriptionExtractor.extract(content))
                    .isEqualTo("First paragraph.");
        }

        @Test
        void stopsAtBlankLineAfterContent() {
            String content = "= Title\n\nFirst line.\nSecond line.\n\nThird line is second paragraph.";

            assertThat(DescriptionExtractor.extract(content))
                    .isEqualTo("First line. Second line.");
        }

        @Test
        void skipsAttributeLines() {
            String content = "= Title\n:categories: web, rest\n:topics: security\n\nActual content.";

            assertThat(DescriptionExtractor.extract(content))
                    .isEqualTo("Actual content.");
        }

        @Test
        void skipsBlockAttributes() {
            String content = "= Title\n\n[source,java]\nActual content here.";

            assertThat(DescriptionExtractor.extract(content))
                    .isEqualTo("Actual content here.");
        }

        @Test
        void cleansInlineFormattingFromFirstParagraph() {
            String content = "= Title\n\nUse *bold* and _italic_ and `mono` text.";

            assertThat(DescriptionExtractor.extract(content))
                    .isEqualTo("Use bold and italic and mono text.");
        }

        @Test
        void cleansAttributeReferencesFromFirstParagraph() {
            String content = "= Title\n\nConfigure {project-name} with {quarkus-version}.";

            assertThat(DescriptionExtractor.extract(content))
                    .isEqualTo("Configure with .");
        }

        @Test
        void handlesContentWithNoTitle() {
            String content = "Just some text without a title.\nMore text.";

            // No title found, so nothing is extracted
            assertThat(DescriptionExtractor.extract(content)).isEmpty();
        }

        @Test
        void skipsDashBlockDelimiters() {
            String content = "= Title\n\n----\nlisting block\n----\n\nAfter listing.";

            assertThat(DescriptionExtractor.extract(content))
                    .isEqualTo("After listing.");
        }
    }

    @Nested
    class Truncation {

        @Test
        void shortTextUnchanged() {
            String shortText = "A short description.";
            String content = "= Title\n:description: " + shortText;

            assertThat(DescriptionExtractor.extract(content)).isEqualTo(shortText);
        }

        @Test
        void longTextTruncatedAtWordBoundary() {
            String longText = "A ".repeat(200); // 400 chars
            String content = "= Title\n:description: " + longText;

            String result = DescriptionExtractor.extract(content);

            assertThat(result).hasSizeLessThanOrEqualTo(DescriptionExtractor.MAX_DESCRIPTION_LENGTH + 1); // +1 for ellipsis char
            assertThat(result).endsWith("…");
        }

        @Test
        void truncateReturnsNullForNull() {
            assertThat(DescriptionExtractor.truncate(null, 300)).isNull();
        }

        @Test
        void truncateReturnsTextWithinLimit() {
            assertThat(DescriptionExtractor.truncate("short text", 300))
                    .isEqualTo("short text");
        }

        @Test
        void truncateAppendsEllipsisAtWordBoundary() {
            // Build text that exceeds 300 chars
            String text = "word ".repeat(80); // 400 chars
            String result = DescriptionExtractor.truncate(text, 300);

            assertThat(result).endsWith("…");
            assertThat(result.length()).isLessThanOrEqualTo(301); // 300 + 1 char ellipsis
            // Should not end mid-word (before the ellipsis)
            String beforeEllipsis = result.substring(0, result.length() - 1);
            assertThat(beforeEllipsis).doesNotContain("wor "); // word boundary cut
        }

        @Test
        void truncateExactlyAtLimit() {
            String text = "x".repeat(300);
            assertThat(DescriptionExtractor.truncate(text, 300)).isEqualTo(text);
        }

        @Test
        void truncateAtLimitPlusOne() {
            String text = "x".repeat(301);
            String result = DescriptionExtractor.truncate(text, 300);
            // No spaces, so falls back to hard cut at 300
            assertThat(result).hasSize(301); // 300 + ellipsis
            assertThat(result).endsWith("…");
        }

        @Test
        void summaryFromMetadataIsTruncated() {
            String longSummary = "word ".repeat(80); // 400 chars
            DocumentMetadata metadata = DocumentMetadata.builder()
                    .summary(longSummary)
                    .build();

            String result = DescriptionExtractor.extract("= Title\n\nContent.", metadata);

            assertThat(result).endsWith("…");
            assertThat(result.length()).isLessThanOrEqualTo(301);
        }
    }

    @Nested
    class CleanupIntegration {

        @Test
        void descriptionAttributeIsCleaned() {
            String content = "= Title\n:description: Use *bold* and {attr} markup\n\nContent.";

            String result = DescriptionExtractor.extract(content);

            assertThat(result).doesNotContain("*bold*");
            assertThat(result).doesNotContain("{attr}");
            assertThat(result).contains("bold");
        }

        @Test
        void fullExampleFromSpec() {
            String content = """
                    = My Custom Extension Guide
                    
                    include::_attributes.adoc[]
                    :categories: extensions
                    
                    [WARNING]
                    ====
                    This feature is experimental.
                    ====
                    
                    This extension allows you to configure {project-name} with
                    xref:config-reference.adoc[custom configuration] for your
                    *enterprise* deployment.
                    """;

            String result = DescriptionExtractor.extract(content);

            assertThat(result).doesNotContain("include::");
            assertThat(result).doesNotContain("{project-name}");
            assertThat(result).doesNotContain("*enterprise*");
            assertThat(result).doesNotContain("[WARNING]");
            assertThat(result).doesNotContain("====");
            assertThat(result).contains("enterprise");
            assertThat(result).contains("custom configuration");
        }
    }
}
