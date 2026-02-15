package com.fvd.common.utils;

import com.fvd.asciidocs.model.DocumentMetadata;
import lombok.experimental.UtilityClass;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility for extracting clean descriptions from AsciiDoc content.
 *
 * <p>Priority chain:
 * <ol>
 *   <li>{@code :summary:} from {@link DocumentMetadata} (cleanest source)</li>
 *   <li>{@code :description:} attribute from content</li>
 *   <li>First meaningful paragraph after title (fallback)</li>
 * </ol>
 *
 * <p>All extracted text is cleaned via {@link AsciiDocCleaner#cleanDescription(String)}
 * and truncated to {@value #MAX_DESCRIPTION_LENGTH} characters at a word boundary.
 */
@UtilityClass
public class DescriptionExtractor {

    private static final Pattern DESCRIPTION_PATTERN = Pattern.compile(
            "^:description:\\s*(.+)$", Pattern.MULTILINE);

    private static final Pattern BLOCK_DELIMITER = Pattern.compile("^(={4,}|-{4,})$");

    private static final Pattern SECTION_HEADER = Pattern.compile("^={2,6} .+");

    static final int MAX_DESCRIPTION_LENGTH = 300;

    /**
     * Extracts a clean description from document content and optional metadata.
     *
     * @param content  raw AsciiDoc content
     * @param metadata optional document metadata (may be null)
     * @return clean, truncated description
     */
    public static String extract(String content, DocumentMetadata metadata) {
        // 1. Use :summary: from metadata (cleanest source)
        if (metadata != null && metadata.hasSummary()) {
            return truncate(metadata.getSummary(), MAX_DESCRIPTION_LENGTH);
        }

        if (content == null || content.isBlank()) {
            return "";
        }

        // 2. Fall back to :description: attribute
        Matcher matcher = DESCRIPTION_PATTERN.matcher(content);
        if (matcher.find()) {
            return truncate(AsciiDocCleaner.cleanDescription(matcher.group(1)),
                    MAX_DESCRIPTION_LENGTH);
        }

        // 3. Fall back to first paragraph after title
        return truncate(extractFirstParagraph(content), MAX_DESCRIPTION_LENGTH);
    }

    /**
     * Overload for callers without metadata (backward compatibility).
     *
     * @param content raw AsciiDoc content
     * @return clean, truncated description
     */
    public static String extract(String content) {
        return extract(content, null);
    }

    /**
     * Extracts the first meaningful paragraph from AsciiDoc content,
     * skipping header attributes, directives, admonition labels, and block delimiters.
     */
    private static String extractFirstParagraph(String content) {
        String[] lines = content.split("\n");
        StringBuilder desc = new StringBuilder();
        boolean foundTitle = false;
        boolean pastHeader = false;
        int ifdefDepth = 0;
        String blockDelimiter = null;

        for (String line : lines) {
            String trimmed = line.trim();

            // Skip title line
            if (trimmed.startsWith("= ") && !foundTitle) {
                foundTitle = true;
                continue;
            }

            if (!foundTitle) {
                continue;
            }

            // Track delimited blocks (====, ----) and skip their content
            if (BLOCK_DELIMITER.matcher(trimmed).matches()) {
                if (blockDelimiter == null) {
                    blockDelimiter = trimmed;
                    continue;
                }
                if (trimmed.charAt(0) == blockDelimiter.charAt(0)) {
                    blockDelimiter = null;
                    continue;
                }
            }
            if (blockDelimiter != null) {
                continue;
            }

            // Track ifdef/ifndef blocks and skip their content
            if (trimmed.startsWith("ifdef::") || trimmed.startsWith("ifndef::")) {
                ifdefDepth++;
                continue;
            }
            if (trimmed.startsWith("endif::")) {
                if (ifdefDepth > 0) {
                    ifdefDepth--;
                }
                continue;
            }
            if (ifdefDepth > 0) {
                continue;
            }

            // Skip header attribute lines and directives
            if (trimmed.startsWith(":") || trimmed.startsWith("include::") ||
                    trimmed.startsWith("[")) {
                continue;
            }

            // Skip blank lines before content starts
            if (!pastHeader && trimmed.isEmpty()) {
                continue;
            }

            // Stop at next section header (== followed by space, not block delimiters like ====)
            if (SECTION_HEADER.matcher(trimmed).matches()) {
                break;
            }

            // Stop at blank line after content has started (end of first paragraph)
            if (pastHeader && trimmed.isEmpty()) {
                break;
            }

            pastHeader = true;
            if (!desc.isEmpty()) {
                desc.append(" ");
            }
            desc.append(trimmed);
        }

        return AsciiDocCleaner.cleanDescription(desc.toString());
    }

    /**
     * Truncates text at a word boundary, appending ellipsis if truncated.
     *
     * @param text      the text to truncate
     * @param maxLength the maximum allowed length
     * @return truncated text with ellipsis, or original if within limit
     */
    static String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        // Truncate at word boundary
        int lastSpace = text.lastIndexOf(' ', maxLength);
        if (lastSpace > maxLength * 0.7) {
            return text.substring(0, lastSpace) + "…";
        }
        return text.substring(0, maxLength) + "…";
    }
}
