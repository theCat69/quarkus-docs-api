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
     * A code block extracted from a document with its language, content, containing section, and line range.
     */
    record CodeBlock(String language, String content, String sectionTitle, int startLine, int endLine) {
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
}
