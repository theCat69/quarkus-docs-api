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

    // Xref: xref:path[text] → text
    private static final Pattern XREF = Pattern.compile("xref:[^\\[]*\\[([^\\]]*)\\]");

    // Link: link:url[text] → text
    private static final Pattern LINK = Pattern.compile("link:[^\\[]*\\[([^\\]]*)\\]");

    // Inline xref: <<anchor,text>> → text
    private static final Pattern INLINE_XREF = Pattern.compile("<<[^,>]+,([^>]+)>>");

    // Multiple blank lines → single blank line
    private static final Pattern MULTI_BLANK = Pattern.compile("\\n{3,}");

    public static String clean(String text) {
        if (text == null) {
            return "";
        }

        String result = text;
        result = COMMENT_BLOCK.matcher(result).replaceAll("");
        result = INCLUDE_DIRECTIVE.matcher(result).replaceAll("");
        result = PREPROCESSOR.matcher(result).replaceAll("");
        result = ATTRIBUTE_DECL.matcher(result).replaceAll("");
        result = BLOCK_ATTRIBUTE.matcher(result).replaceAll("");
        result = XREF.matcher(result).replaceAll("$1");
        result = LINK.matcher(result).replaceAll("$1");
        result = INLINE_XREF.matcher(result).replaceAll("$1");
        result = MULTI_BLANK.matcher(result).replaceAll("\n\n");
        return result.trim();
    }
}
