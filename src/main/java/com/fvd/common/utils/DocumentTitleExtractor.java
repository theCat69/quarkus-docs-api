package com.fvd.common.utils;

import lombok.experimental.UtilityClass;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility for extracting the title from AsciiDoc content.
 */
@UtilityClass
public class DocumentTitleExtractor {

    private static final Pattern TITLE_PATTERN =
            Pattern.compile("^=\\s+(.+)$", Pattern.MULTILINE);

    public static String extractTitle(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        Matcher matcher = TITLE_PATTERN.matcher(content);
        return matcher.find() ? matcher.group(1).trim() : "";
    }
}
