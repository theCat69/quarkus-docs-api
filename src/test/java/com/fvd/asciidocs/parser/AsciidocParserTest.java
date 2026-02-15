package com.fvd.asciidocs.parser;

import com.fvd.docs.parser.DocParser;
import com.fvd.search.TestSearchConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AsciidocParserTest {

    private final AsciidocParser parser = new AsciidocParser(new TestSearchConfig());

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
        assertThat(keywords).containsEntry("secur", 3);
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
        assertThat(keywords).containsEntry("secur", 1);
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
        List<DocParser.Section> sections = parser.parseSections(text);
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
        List<DocParser.Section> sections = parser.parseSections(text);
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
        List<DocParser.Section> sections = parser.parseSections(text);
        assertThat(sections).hasSize(2);

        Map<String, Integer> titleKeywords = sections.get(0).keywords();
        assertThat(titleKeywords).containsEntry("secur", 1);
        assertThat(titleKeywords).containsEntry("overview", 1);

        Map<String, Integer> sectionKeywords = sections.get(1).keywords();
        assertThat(sectionKeywords).containsEntry("configure", 1);
        assertThat(sectionKeywords).containsEntry("oidc", 1);
        assertThat(sectionKeywords).containsEntry("provid", 1);
        assertThat(sectionKeywords).containsEntry("setting", 1);
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
        List<DocParser.Section> sections = parser.parseSections(text);
        DocParser.Section configSection = sections.get(1);
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
        List<DocParser.Section> sections = parser.parseSections(text);
        assertThat(sections).hasSize(1);
        assertThat(sections.get(0).title()).isEmpty();
        assertThat(sections.get(0).startLine()).isEqualTo(1);
        assertThat(sections.get(0).keywords()).containsKeys("just", "some", "plain", "text");
    }

    @Test
    void parseSectionsHandlesEmptyDocument() {
        List<DocParser.Section> sections = parser.parseSections("");
        assertThat(sections).isEmpty();
    }

    @Test
    void parseCodeBlocksExtractsSingleBlock() {
        String text = """
                = Title
                
                == My Section
                
                Some text.
                
                [source,java]
                ----
                import jakarta.ws.rs.GET;
                
                public class MyResource {
                }
                ----
                """;
        List<DocParser.CodeBlock> blocks = parser.parseCodeBlocks(text);
        assertThat(blocks).hasSize(1);
        assertThat(blocks.get(0).language()).isEqualTo("java");
        assertThat(blocks.get(0).content()).contains("import jakarta.ws.rs.GET");
        assertThat(blocks.get(0).content()).contains("public class MyResource");
        assertThat(blocks.get(0).sectionTitle()).isEqualTo("My Section");
        assertThat(blocks.get(0).startLine()).isEqualTo(8);
        assertThat(blocks.get(0).endLine()).isEqualTo(13);
    }

    @Test
    void parseCodeBlocksExtractsMultipleBlocks() {
        String text = """
                = Title
                
                == First
                
                [source,java]
                ----
                code one
                ----
                
                == Second
                
                [source,xml]
                ----
                code two
                ----
                """;
        List<DocParser.CodeBlock> blocks = parser.parseCodeBlocks(text);
        assertThat(blocks).hasSize(2);
        assertThat(blocks.get(0).language()).isEqualTo("java");
        assertThat(blocks.get(0).content()).isEqualTo("code one");
        assertThat(blocks.get(0).sectionTitle()).isEqualTo("First");
        assertThat(blocks.get(1).language()).isEqualTo("xml");
        assertThat(blocks.get(1).content()).isEqualTo("code two");
        assertThat(blocks.get(1).sectionTitle()).isEqualTo("Second");
    }

    @Test
    void parseCodeBlocksHandlesBlockWithoutSourceAttribute() {
        String text = """
                = Title
                
                ----
                some code without language
                ----
                """;
        List<DocParser.CodeBlock> blocks = parser.parseCodeBlocks(text);
        assertThat(blocks).hasSize(1);
        assertThat(blocks.get(0).language()).isEmpty();
        assertThat(blocks.get(0).content()).isEqualTo("some code without language");
        assertThat(blocks.get(0).sectionTitle()).isEqualTo("Title");
    }

    @Test
    void parseCodeBlocksHandlesSourceWithoutLanguage() {
        String text = """
                = Title
                
                [source]
                ----
                generic code
                ----
                """;
        List<DocParser.CodeBlock> blocks = parser.parseCodeBlocks(text);
        assertThat(blocks).hasSize(1);
        assertThat(blocks.get(0).language()).isEqualTo("");
    }

    @Test
    void parseCodeBlocksTracksCorrectSectionAcrossHeaders() {
        String text = """
                = Title
                
                Intro text.
                
                == Section A
                
                [source,java]
                ----
                code in A
                ----
                
                == Section B
                
                No code here.
                
                == Section C
                
                [source,properties]
                ----
                key=value
                ----
                """;
        List<DocParser.CodeBlock> blocks = parser.parseCodeBlocks(text);
        assertThat(blocks).hasSize(2);
        assertThat(blocks.get(0).sectionTitle()).isEqualTo("Section A");
        assertThat(blocks.get(1).sectionTitle()).isEqualTo("Section C");
        assertThat(blocks.get(1).language()).isEqualTo("properties");
    }

    @Test
    void parseCodeBlocksReturnsEmptyForNoCodeBlocks() {
        String text = """
                = Title
                
                Just text, no code.
                """;
        List<DocParser.CodeBlock> blocks = parser.parseCodeBlocks(text);
        assertThat(blocks).isEmpty();
    }

    @Test
    void parseCodeBlocksReturnsEmptyForBlankInput() {
        assertThat(parser.parseCodeBlocks("")).isEmpty();
        assertThat(parser.parseCodeBlocks(null)).isEmpty();
    }

    @Test
    void parseCodeBlocksPreservesMultiLineContent() {
        String text = """
                = Title
                
                [source,java]
                ----
                line one
                line two
                line three
                ----
                """;
        List<DocParser.CodeBlock> blocks = parser.parseCodeBlocks(text);
        assertThat(blocks).hasSize(1);
        assertThat(blocks.get(0).content()).isEqualTo("line one\nline two\nline three");
    }

    @Test
    void parseCodeBlocksNormalizesLanguageToLowercase() {
        String text = """
                = Title
                
                [source,Java]
                ----
                public class Foo {}
                ----
                """;
        List<DocParser.CodeBlock> blocks = parser.parseCodeBlocks(text);
        assertThat(blocks).hasSize(1);
        assertThat(blocks.get(0).language()).isEqualTo("java");
    }

    @Test
    void parseCodeBlocksNormalizesUppercaseLanguage() {
        String text = """
                = Title
                
                [source,XML]
                ----
                <root/>
                ----
                """;
        List<DocParser.CodeBlock> blocks = parser.parseCodeBlocks(text);
        assertThat(blocks).hasSize(1);
        assertThat(blocks.get(0).language()).isEqualTo("xml");
    }

    @Test
    void parseCodeBlocksLanguageWithSpaceIsTrimmedAndLowercased() {
        String text = """
                = Title
                
                [source, Java]
                ----
                code
                ----
                """;
        List<DocParser.CodeBlock> blocks = parser.parseCodeBlocks(text);
        assertThat(blocks).hasSize(1);
        assertThat(blocks.get(0).language()).isEqualTo("java");
    }

    @Test
    void parseCodeBlocksEmptySourceCommaStoresEmptyLanguage() {
        String text = """
                = Title
                
                [source,]
                ----
                code
                ----
                """;
        List<DocParser.CodeBlock> blocks = parser.parseCodeBlocks(text);
        assertThat(blocks).hasSize(1);
        assertThat(blocks.get(0).language()).isEmpty();
    }

    @Test
    void docsPrefixWithVersionReturnsVersionedPath() {
        assertThat(parser.docsPrefix("3.27")).isEqualTo("_versions/3.27/guides/");
        assertThat(parser.docsPrefix("3.21")).isEqualTo("_versions/3.21/guides/");
        assertThat(parser.docsPrefix("main")).isEqualTo("_versions/main/guides/");
    }

    @Test
    @SuppressWarnings("deprecation")
    void docsPrefixNoArgReturnsMainVersionPath() {
        assertThat(parser.docsPrefix()).isEqualTo("_versions/main/guides/");
    }

    @Test
    void docsPrefixDefaultMethodDelegatesToVersionedMethod() {
        // Verify the DocParser default method delegates to docsPrefix("main")
        DocParser docParser = parser;
        assertThat(docParser.docsPrefix()).isEqualTo(docParser.docsPrefix("main"));
    }

    @Test
    void extractKeywordsStripsAsciiDocMarkupNoise() {
        String text = """
                include::_includes/prerequisites.adoc[]
                :description: Guide to security
                
                This guide covers security configuration.
                
                NOTE: Use OIDC for authentication.
                
                image::architecture.png[Architecture diagram]
                
                See xref:security-oidc.adoc[OIDC guide] for details.
                
                icon:lock[] Secured endpoint
                
                [.role-name]
                Paragraph with role.
                
                // This is a comment
                <1> First callout
                
                |===
                | Feature | Status
                |===
                
                {quarkus-version} placeholder text
                <<overview>> reference
                [[anchor-id]]
                [#shorthand-id]
                """;
        Map<String, Integer> keywords = parser.extractKeywords(text);
        // Noise tokens from markup directives should NOT be present
        assertThat(keywords).doesNotContainKey("includ");
        assertThat(keywords).doesNotContainKey("adoc");
        assertThat(keywords).doesNotContainKey("prerequisit");
        assertThat(keywords).doesNotContainKey("xref");
        assertThat(keywords).doesNotContainKey("image");
        assertThat(keywords).doesNotContainKey("icon");
        assertThat(keywords).doesNotContainKey("lock");
        assertThat(keywords).doesNotContainKey("note");
        assertThat(keywords).doesNotContainKey("descript");
        assertThat(keywords).doesNotContainKey("architectur");
        assertThat(keywords).doesNotContainKey("quarkus-version");
        assertThat(keywords).doesNotContainKey("overview");
        assertThat(keywords).doesNotContainKey("anchor-id");
        assertThat(keywords).doesNotContainKey("shorthand-id");
        // Actual content should be preserved
        assertThat(keywords).containsKey("secur");
        assertThat(keywords).containsKey("configur");
        assertThat(keywords).containsKey("oidc");
        assertThat(keywords).containsKey("guide");
        assertThat(keywords).containsKey("authentic");
    }
}
