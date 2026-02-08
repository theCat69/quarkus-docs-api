package com.fvd.docs.parser;

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
     * Splits raw text into lowercase tokens suitable for indexing.
     */
    List<String> tokenize(String text);

    /**
     * Extracts keyword frequencies from document content, excluding stop words.
     */
    Map<String, Integer> extractKeywords(String text);

    /**
     * Parses the document into sections based on heading markers.
     */
    List<Section> parseSections(String text);

    /**
     * Returns the path prefix used to locate documents within the repository zip archive.
     * For example, AsciiDoc files in Quarkus live under "docs/src/main/asciidoc/".
     */
    String docsPrefix();

    /**
     * Returns the file suffix for this document type (e.g. ".adoc" for AsciiDoc).
     */
    String fileSuffix();
}
