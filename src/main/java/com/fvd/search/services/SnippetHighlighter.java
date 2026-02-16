package com.fvd.search.services;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.experimental.UtilityClass;

/**
 * Stateless utility for highlighting keyword occurrences in search snippets.
 * Wraps matched keywords with ** markers for bold display.
 */
@UtilityClass
public class SnippetHighlighter {

    /**
     * Wraps occurrences of any keyword in the snippet with ** markers.
     * Case-insensitive matching, preserves original casing.
     * Longest keywords matched first.
     * Word-boundary aware.
     * No double-wrapping.
     */
    public static String highlight(String snippet, Collection<String> keywords) {
        if (snippet == null || snippet.isEmpty() || keywords == null || keywords.isEmpty()) {
            return snippet;
        }
        // Sort keywords longest first
        List<String> sorted = keywords.stream()
                .filter(k -> k != null && !k.isEmpty())
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();
        if (sorted.isEmpty()) {
            return snippet;
        }

        // Build regex alternation: \b(keyword1|keyword2|...)\b
        // Escape each keyword for regex safety
        StringBuilder regex = new StringBuilder("\\b(");
        for (int i = 0; i < sorted.size(); i++) {
            if (i > 0) {
                regex.append("|");
            }
            regex.append(Pattern.quote(sorted.get(i)));
        }
        regex.append(")\\b");

        Pattern pattern = Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(snippet);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            // Check not already wrapped (no ** immediately before/after)
            int start = matcher.start();
            int end = matcher.end();
            boolean alreadyWrapped = (start >= 2 && snippet.substring(start - 2, start).equals("**"))
                    || (end + 2 <= snippet.length() && snippet.substring(end, end + 2).equals("**"));
            if (alreadyWrapped) {
                matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group()));
            } else {
                matcher.appendReplacement(result, "**" + Matcher.quoteReplacement(matcher.group()) + "**");
            }
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
