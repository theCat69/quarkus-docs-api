package com.fvd;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AsciidocParserTest {

    private final AsciidocParser parser = new AsciidocParser();

    @Test
    void tokenizeReturnsLowercaseWords() {
        List<String> tokens = parser.tokenize("Hello World Quarkus");
        assertThat(tokens).containsExactly("hello", "world", "quarkus");
    }

    @Test
    void tokenizeStripsNonAlphanumeric() {
        List<String> tokens = parser.tokenize("OIDC-based auth. (JWT)");
        assertThat(tokens).containsExactly("oidc-based", "auth", "jwt");
    }

    @Test
    void tokenizeIgnoresShortTokens() {
        List<String> tokens = parser.tokenize("a to be or not");
        assertThat(tokens).containsExactly("not");
    }

    @Test
    void tokenizeReturnsEmptyForBlankInput() {
        assertThat(parser.tokenize("")).isEmpty();
        assertThat(parser.tokenize("   ")).isEmpty();
    }

    @Test
    void extractKeywordsCountsOccurrences() {
        String text = "security oidc security security oidc";
        Map<String, Integer> keywords = parser.extractKeywords(text);
        assertThat(keywords).containsEntry("security", 3);
        assertThat(keywords).containsEntry("oidc", 2);
    }

    @Test
    void extractKeywordsExcludesCodeBlocks() {
        String text = """
                = Title
                
                Some security content.
                
                [source,java]
                ----
                public class SecurityFilter {
                    // code keyword should be excluded
                }
                ----
                
                More oidc content.
                """;
        Map<String, Integer> keywords = parser.extractKeywords(text);
        assertThat(keywords).containsEntry("security", 1);
        assertThat(keywords).containsEntry("oidc", 1);
        assertThat(keywords).doesNotContainKey("class");
        assertThat(keywords).doesNotContainKey("securityfilter");
    }

    @Test
    void extractKeywordsExcludesMultipleCodeBlocks() {
        String text = """
                intro text
                
                ----
                code block one
                ----
                
                middle text
                
                [source,xml]
                ----
                <dependency>code block two</dependency>
                ----
                
                outro text
                """;
        Map<String, Integer> keywords = parser.extractKeywords(text);
        assertThat(keywords).containsKeys("intro", "middle", "outro", "text");
        assertThat(keywords).doesNotContainKey("code");
        assertThat(keywords).doesNotContainKey("block");
        assertThat(keywords).doesNotContainKey("dependency");
    }

    @Test
    void parseSectionsDetectsSectionBoundaries() {
        String text = """
                = Document Title
                
                Intro paragraph.
                
                == First Section
                
                First section content about security.
                
                == Second Section
                
                Second section content about oidc.
                """;
        List<AsciidocParser.Section> sections = parser.parseSections(text);
        assertThat(sections).hasSize(3);

        // Title section (everything before first ==)
        assertThat(sections.get(0).title()).isEqualTo("Document Title");
        assertThat(sections.get(0).startLine()).isEqualTo(1);
        assertThat(sections.get(0).endLine()).isEqualTo(4);

        // First Section
        assertThat(sections.get(1).title()).isEqualTo("First Section");
        assertThat(sections.get(1).startLine()).isEqualTo(5);
        assertThat(sections.get(1).endLine()).isEqualTo(8);

        // Second Section
        assertThat(sections.get(2).title()).isEqualTo("Second Section");
        assertThat(sections.get(2).startLine()).isEqualTo(9);
        assertThat(sections.get(2).endLine()).isEqualTo(12);
    }

    @Test
    void parseSectionsHandlesNestedSections() {
        String text = """
                = Title
                
                == Section One
                
                Content one.
                
                === Subsection A
                
                Sub content a.
                
                == Section Two
                
                Content two.
                """;
        List<AsciidocParser.Section> sections = parser.parseSections(text);
        assertThat(sections).hasSize(4);
        assertThat(sections.get(0).title()).isEqualTo("Title");
        assertThat(sections.get(1).title()).isEqualTo("Section One");
        assertThat(sections.get(2).title()).isEqualTo("Subsection A");
        assertThat(sections.get(3).title()).isEqualTo("Section Two");
    }

    @Test
    void parseSectionsExtractsKeywordsPerSection() {
        String text = """
                = Security Guide
                
                Overview of security.
                
                == OIDC Configuration
                
                Configure oidc provider settings.
                """;
        List<AsciidocParser.Section> sections = parser.parseSections(text);
        assertThat(sections).hasSize(2);

        Map<String, Integer> titleKeywords = sections.get(0).keywords();
        assertThat(titleKeywords).containsEntry("security", 1);
        assertThat(titleKeywords).containsEntry("overview", 1);

        Map<String, Integer> sectionKeywords = sections.get(1).keywords();
        assertThat(sectionKeywords).containsEntry("configure", 1);
        assertThat(sectionKeywords).containsEntry("oidc", 1);
        assertThat(sectionKeywords).containsEntry("provider", 1);
        assertThat(sectionKeywords).containsEntry("settings", 1);
    }

    @Test
    void parseSectionsExcludesCodeBlocksFromKeywords() {
        String text = """
                = Title
                
                == Config
                
                Real content here.
                
                [source,java]
                ----
                public class Config {
                    private String ignored;
                }
                ----
                
                More real content.
                """;
        List<AsciidocParser.Section> sections = parser.parseSections(text);
        AsciidocParser.Section configSection = sections.get(1);
        assertThat(configSection.keywords()).containsKeys("real", "content");
        assertThat(configSection.keywords()).doesNotContainKey("class");
        assertThat(configSection.keywords()).doesNotContainKey("ignored");
        assertThat(configSection.keywords()).doesNotContainKey("private");
    }

    @Test
    void parseSectionsHandlesDocumentWithNoSectionHeaders() {
        String text = """
                Just some plain text.
                No section headers at all.
                """;
        List<AsciidocParser.Section> sections = parser.parseSections(text);
        assertThat(sections).hasSize(1);
        assertThat(sections.get(0).title()).isEmpty();
        assertThat(sections.get(0).startLine()).isEqualTo(1);
        assertThat(sections.get(0).keywords()).containsKeys("just", "some", "plain", "text");
    }

    @Test
    void parseSectionsHandlesEmptyDocument() {
        List<AsciidocParser.Section> sections = parser.parseSections("");
        assertThat(sections).isEmpty();
    }
}
