package com.fvd.docs.parser;

import com.fvd.asciidocs.model.DocumentMetadata;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Abstraction for document parsing that supports multiple formats (AsciiDoc, Markdown, etc.).
 * Implementations provide format-specific tokenization, keyword extraction, and section parsing.
 */
public interface DocParser {

    /**
     * A parsed section of a document with its title, line range, and keyword frequencies.
     */
    record Section(String title, int startLine, int endLine, Map<String, Integer> keywords) {
    }

    /**
     * A code block extracted from a document with its language, content, containing section, and line range.
     */
    record CodeBlock(String language, String content, String sectionTitle, int startLine, int endLine) {
    }

    /**
     * An extracted keyword carrying both its stemmed and original (un-stemmed) forms,
     * along with the frequency count.
     */
    record ExtractedKeyword(String stemmed, String original, int frequency) {
    }

    /**
     * Splits raw text into lowercase tokens suitable for indexing.
     */
    List<String> tokenize(String text);

    /**
     * Extracts keyword frequencies from document content, excluding stop words.
     */
    Map<String, Integer> extractKeywords(String text);

    /**
     * Extracts keywords with their original (un-stemmed) forms from document content.
     * When multiple tokens stem to the same form, the longest original token is kept
     * as it is typically the most descriptive form.
     *
     * @param text the document content
     * @return map of stemmed keyword to ExtractedKeyword containing original form and frequency
     */
    default Map<String, ExtractedKeyword> extractKeywordsWithOriginals(String text) {
        Map<String, Integer> stemmed = extractKeywords(text);
        Map<String, ExtractedKeyword> result = new HashMap<>();
        for (Map.Entry<String, Integer> entry : stemmed.entrySet()) {
            result.put(entry.getKey(),
                    new ExtractedKeyword(entry.getKey(), entry.getKey(), entry.getValue()));
        }
        return result;
    }

    /**
     * Parses the document into sections based on heading markers.
     */
    List<Section> parseSections(String text);

    /**
     * Returns the path prefix used to locate documents within the repository zip archive
     * for a specific version.
     * For example, docs from the website repo live under "_versions/3.27/guides/".
     */
    String docsPrefix(String version);

    /**
     * Returns the path prefix for the default version ("main").
     * Delegates to {@link #docsPrefix(String)} with "main".
     */
    default String docsPrefix() {
        return docsPrefix("main");
    }

    /**
     * Returns the file suffix for this document type (e.g. ".adoc" for AsciiDoc).
     */
    String fileSuffix();

    /**
     * Extracts code blocks from the document content with their language, content,
     * containing section title, and line range.
     */
    List<CodeBlock> parseCodeBlocks(String text);

    /**
     * Extracts document metadata from document header attributes.
     * Default implementation returns empty metadata.
     *
     * @param content the full document content
     * @return extracted metadata (never null)
     */
    default DocumentMetadata extractMetadata(String content) {
        return DocumentMetadata.empty();
    }
}
