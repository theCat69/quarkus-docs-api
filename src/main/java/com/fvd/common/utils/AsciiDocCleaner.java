package com.fvd.common.utils;

import java.util.regex.Pattern;

import lombok.experimental.UtilityClass;

@UtilityClass
public class AsciiDocCleaner {

    // Comment blocks: ////...////
    private static final Pattern COMMENT_BLOCK = Pattern.compile("^////.*?^////", Pattern.MULTILINE | Pattern.DOTALL);

    // Include directives: include::...[]
    private static final Pattern INCLUDE_DIRECTIVE = Pattern.compile("include::[^\\[]*\\[[^\\]]*\\]");

    // Preprocessor directives: ifdef::...[], ifndef::...[], endif::...[]
    private static final Pattern PREPROCESSOR = Pattern.compile("(?:ifdef|ifndef|endif)::[^\\[]*\\[[^\\]]*\\]");

    // Attribute declarations: :key: value (full line)
    private static final Pattern ATTRIBUTE_DECL = Pattern.compile("^:\\w[\\w-]*:.*$", Pattern.MULTILINE);

    // Block attributes: [id="..."], [.role], [source,java]
    private static final Pattern BLOCK_ATTRIBUTE = Pattern.compile("^\\[(?:id=|\\.|source)[^\\]]*\\]\\s*$", Pattern.MULTILINE);

    // Source/listing/literal block attributes: [source,lang], [listing], [literal], etc.
    private static final Pattern SOURCE_ATTRIBUTE = Pattern.compile(
            "^\\[(?:source|listing|literal|verse|quote|sidebar|example|passthrough)(?:,[^\\]]*)?\\]\\s*$",
            Pattern.MULTILINE);

    // Admonition block markers: [NOTE], [TIP], [WARNING], [IMPORTANT], [CAUTION]
    private static final Pattern ADMONITION_BLOCK = Pattern.compile(
            "^\\[(?:NOTE|TIP|WARNING|IMPORTANT|CAUTION)\\]\\s*$", Pattern.MULTILINE);

    // Role attributes: [.role-name] on its own line
    private static final Pattern ROLE_ATTRIBUTE = Pattern.compile("^\\[\\.[\\w-]+\\]\\s*$", Pattern.MULTILINE);

    // Block anchor IDs: [[anchor-id]]
    private static final Pattern BLOCK_ANCHOR = Pattern.compile("^\\[\\[[\\w-]+\\]\\]\\s*$", Pattern.MULTILINE);

    // Shorthand anchor IDs: [#anchor-id] on its own line
    private static final Pattern SHORTHAND_ANCHOR = Pattern.compile("^\\[#[\\w-]+\\]\\s*$", Pattern.MULTILINE);

    // Xref: xref:path[text] → text
    private static final Pattern XREF = Pattern.compile("xref:[^\\[]*\\[([^\\]]*)\\]");

    // Link: link:url[text] → text
    private static final Pattern LINK = Pattern.compile("link:[^\\[]*\\[([^\\]]*)\\]");

    // Inline xref: <<anchor,text>> → text
    private static final Pattern INLINE_XREF = Pattern.compile("<<[^,>]+,([^>]+)>>");

    // Inline anchor references: <<anchor>> (without link text) → removed
    private static final Pattern INLINE_ANCHOR_REF = Pattern.compile("<<[^,>]+>>");

    // Image macros: image::path[alt] or image:path[alt] → removed
    private static final Pattern IMAGE_MACRO = Pattern.compile("image::?[^\\[]*\\[[^\\]]*\\]");

    // Icon macros: icon:name[opts] → removed
    private static final Pattern ICON_MACRO = Pattern.compile("icon:[^\\[]*\\[[^\\]]*\\]");

    // Attribute references: {attr-name} → removed
    private static final Pattern ATTRIBUTE_REF = Pattern.compile("\\{[\\w-]+\\}");

    // Callout markers: <1>, <2>, <.> → removed
    private static final Pattern CALLOUT_MARKER = Pattern.compile("<\\d+>|<\\.>");

    // Admonition markers: NOTE:, TIP:, WARNING:, IMPORTANT:, CAUTION: at line start → removed (keep following text)
    private static final Pattern ADMONITION_MARKER = Pattern.compile(
            "^(NOTE|TIP|WARNING|IMPORTANT|CAUTION):\\s*", Pattern.MULTILINE);

    // Table delimiters: |===
    private static final Pattern TABLE_DELIMITER = Pattern.compile("^\\|={3,}\\s*$", Pattern.MULTILINE);

    // Table cell separators: | followed by whitespace at start of line → strip leading "| " but keep text
    private static final Pattern TABLE_CELL = Pattern.compile("^\\|\\s+", Pattern.MULTILINE);

    // Single-line comments: // comment (double slash + whitespace) → removed
    private static final Pattern SINGLE_LINE_COMMENT = Pattern.compile("^//\\s+.*$", Pattern.MULTILINE);

    // Inline anchor: anchor:id[text] → removed
    private static final Pattern INLINE_ANCHOR = Pattern.compile("anchor:[^\\[]+\\[[^\\]]*\\]");

    // Multiple blank lines → single blank line
    private static final Pattern MULTI_BLANK = Pattern.compile("\\n{3,}");

    // --- Description-specific patterns ---

    // Admonition block delimiters: ====...==== on its own line
    private static final Pattern DESC_ADMONITION_BLOCK_DELIM = Pattern.compile(
            "^={4,}\\s*$", Pattern.MULTILINE);

    // Inline admonition prefixes: WARNING: text, TIP: text, etc. at line start
    private static final Pattern DESC_INLINE_ADMONITION = Pattern.compile(
            "^(WARNING|TIP|NOTE|IMPORTANT|CAUTION):\\s*", Pattern.MULTILINE);

    // Passthrough macros: pass:[content], stem:[content]
    private static final Pattern DESC_PASSTHROUGH_MACRO = Pattern.compile(
            "(?:pass|stem):\\[[^\\]]*\\]");

    // Bold: *text* or **text**
    private static final Pattern DESC_BOLD = Pattern.compile(
            "\\*{1,2}([^*]+)\\*{1,2}");

    // Italic: _text_ or __text__
    private static final Pattern DESC_ITALIC = Pattern.compile(
            "_{1,2}([^_]+)_{1,2}");

    // Monospace: `text`
    private static final Pattern DESC_MONOSPACE = Pattern.compile(
            "`([^`]+)`");

    // Whitespace normalization: multiple spaces/newlines → single space
    private static final Pattern DESC_WHITESPACE = Pattern.compile("\\s+");

    public static String clean(String text) {
        if (text == null) {
            return "";
        }

        String result = text;

        // 1. Multi-line blocks first — removes large chunks
        result = COMMENT_BLOCK.matcher(result).replaceAll("");

        // 2. Whole-line directives
        result = INCLUDE_DIRECTIVE.matcher(result).replaceAll("");
        result = PREPROCESSOR.matcher(result).replaceAll("");
        result = ATTRIBUTE_DECL.matcher(result).replaceAll("");

        // 3. Block attributes and markers
        result = BLOCK_ATTRIBUTE.matcher(result).replaceAll("");
        result = SOURCE_ATTRIBUTE.matcher(result).replaceAll("");
        result = ADMONITION_BLOCK.matcher(result).replaceAll("");
        result = ROLE_ATTRIBUTE.matcher(result).replaceAll("");

        // 4. Block anchors
        result = BLOCK_ANCHOR.matcher(result).replaceAll("");
        result = SHORTHAND_ANCHOR.matcher(result).replaceAll("");

        // 5. Xref/link (with text) — replace with display text
        result = XREF.matcher(result).replaceAll("$1");
        result = LINK.matcher(result).replaceAll("$1");
        result = INLINE_XREF.matcher(result).replaceAll("$1");

        // 6. Inline anchor references (no text) — remove
        result = INLINE_ANCHOR_REF.matcher(result).replaceAll("");

        // 7. Image/icon macros
        result = IMAGE_MACRO.matcher(result).replaceAll("");
        result = ICON_MACRO.matcher(result).replaceAll("");

        // 8. Inline anchor macro
        result = INLINE_ANCHOR.matcher(result).replaceAll("");

        // 9. Attribute references
        result = ATTRIBUTE_REF.matcher(result).replaceAll("");

        // 10. Callout markers
        result = CALLOUT_MARKER.matcher(result).replaceAll("");

        // 11. Admonition markers (keep following text)
        result = ADMONITION_MARKER.matcher(result).replaceAll("");

        // 12. Table structure
        result = TABLE_DELIMITER.matcher(result).replaceAll("");
        result = TABLE_CELL.matcher(result).replaceAll("");

        // 13. Single-line comments
        result = SINGLE_LINE_COMMENT.matcher(result).replaceAll("");

        // 14. Collapse multiple blank lines (cleanup — always last)
        result = MULTI_BLANK.matcher(result).replaceAll("\n\n");
        return result.trim();
    }

    /**
     * Cleans AsciiDoc markup from description text.
     * Applies all standard cleanup via {@link #clean(String)} plus additional
     * description-specific patterns: admonition block delimiters, passthrough macros,
     * inline formatting, and whitespace normalization.
     *
     * @param text the raw description text
     * @return cleaned plain text suitable for API responses
     */
    public static String cleanDescription(String text) {
        if (text == null) {
            return "";
        }
        String result = clean(text);
        result = DESC_ADMONITION_BLOCK_DELIM.matcher(result).replaceAll("");
        result = DESC_INLINE_ADMONITION.matcher(result).replaceAll("");
        result = DESC_PASSTHROUGH_MACRO.matcher(result).replaceAll("");
        result = DESC_BOLD.matcher(result).replaceAll("$1");
        result = DESC_ITALIC.matcher(result).replaceAll("$1");
        result = DESC_MONOSPACE.matcher(result).replaceAll("$1");
        result = DESC_WHITESPACE.matcher(result).replaceAll(" ");
        return result.trim();
    }
}
