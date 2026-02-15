package com.fvd.asciidocs.parser;

import com.fvd.docs.parser.DocParser;
import com.fvd.search.TestSearchConfig;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AsciidocParserOriginalWordTest {

    private final AsciidocParser parser = new AsciidocParser(new TestSearchConfig());

    @Test
    void extractKeywordsWithOriginalsReturnsStemmedAndOriginal() {
        String text = "security configuration provider";
        Map<String, DocParser.ExtractedKeyword> keywords = parser.extractKeywordsWithOriginals(text);

        assertThat(keywords).containsKey("secur");
        assertThat(keywords.get("secur").original()).isEqualTo("security");
        assertThat(keywords.get("secur").stemmed()).isEqualTo("secur");
        assertThat(keywords.get("secur").frequency()).isEqualTo(1);

        assertThat(keywords).containsKey("configur");
        assertThat(keywords.get("configur").original()).isEqualTo("configuration");

        assertThat(keywords).containsKey("provid");
        assertThat(keywords.get("provid").original()).isEqualTo("provider");
    }

    @Test
    void extractKeywordsWithOriginalsKeepsLongestOriginal() {
        // "configuration" (13 chars) and "configuring" (11 chars) both stem to "configur"
        // "configuration" is longer and should win
        // Note: "configure" stems to "configure" (different stem), not "configur"
        String text = "configuring the configuration settings";
        Map<String, DocParser.ExtractedKeyword> keywords = parser.extractKeywordsWithOriginals(text);

        assertThat(keywords).containsKey("configur");
        assertThat(keywords.get("configur").original()).isEqualTo("configuration");
        assertThat(keywords.get("configur").frequency()).isEqualTo(2);
    }

    @Test
    void extractKeywordsWithOriginalsCountsFrequency() {
        String text = "security security security oidc oidc";
        Map<String, DocParser.ExtractedKeyword> keywords = parser.extractKeywordsWithOriginals(text);

        assertThat(keywords.get("secur").frequency()).isEqualTo(3);
        assertThat(keywords.get("oidc").frequency()).isEqualTo(2);
    }

    @Test
    void extractKeywordsWithOriginalsExcludesCodeBlocks() {
        String text = """
                Real content here about security.
                
                [source,java]
                ----
                public class SecurityFilter {
                    private String ignored;
                }
                ----
                
                More content about oidc.
                """;
        Map<String, DocParser.ExtractedKeyword> keywords = parser.extractKeywordsWithOriginals(text);

        assertThat(keywords).containsKey("secur");
        assertThat(keywords.get("secur").original()).isEqualTo("security");
        assertThat(keywords).containsKey("oidc");
        assertThat(keywords).doesNotContainKey("class");
        assertThat(keywords).doesNotContainKey("securityfilter");
    }

    @Test
    void extractKeywordsWithOriginalsExcludesStopWords() {
        String text = "security configuration important";
        Map<String, DocParser.ExtractedKeyword> keywords = parser.extractKeywordsWithOriginals(text);

        assertThat(keywords).containsKey("secur");
        assertThat(keywords).containsKey("configur");
        assertThat(keywords).containsKey("important");
    }

    @Test
    void extractKeywordsWithOriginalsPreservesUnstemmedWords() {
        // Words that don't get stemmed should have original == stemmed
        String text = "oidc rest database";
        Map<String, DocParser.ExtractedKeyword> keywords = parser.extractKeywordsWithOriginals(text);

        assertThat(keywords).containsKey("oidc");
        assertThat(keywords.get("oidc").original()).isEqualTo("oidc");
        assertThat(keywords).containsKey("rest");
        assertThat(keywords.get("rest").original()).isEqualTo("rest");
        assertThat(keywords).containsKey("database");
        assertThat(keywords.get("database").original()).isEqualTo("database");
    }

    @Test
    void extractKeywordsWithOriginalsHandlesEmptyInput() {
        Map<String, DocParser.ExtractedKeyword> keywords = parser.extractKeywordsWithOriginals("");
        assertThat(keywords).isEmpty();
    }

    @Test
    void extractKeywordsWithOriginalsHandlesMultipleFormsOfSameStem() {
        // "security" and "secured" both stem to "secur"
        // "security" (8 chars) is longer than "secured" (7 chars)
        String text = "security secured";
        Map<String, DocParser.ExtractedKeyword> keywords = parser.extractKeywordsWithOriginals(text);

        assertThat(keywords).containsKey("secur");
        assertThat(keywords.get("secur").original()).isEqualTo("security");
        assertThat(keywords.get("secur").frequency()).isEqualTo(2);
    }
}
