package com.fvd.search.services;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Represents a matched keyword with its source location and weighted score.
 * Used in search results to indicate where keywords matched and their contribution
 * to the overall score.
 *
 * @param keyword the matched keyword (stemmed form)
 * @param originalKeyword the original (unstemmed, lowercased) keyword as entered by the user
 * @param source the source location (filename, title, section, subtitle, body)
 * @param weight the weighted score contribution of this match
 */
@RegisterForReflection
public record MatchedKeyword(
        String keyword,
        String originalKeyword,
        String source,
        double weight
) {
    /**
     * Backward-compatible constructor defaulting originalKeyword to keyword.
     */
    public MatchedKeyword(String keyword, String source, double weight) {
        this(keyword, keyword, source, weight);
    }

    /**
     * Backward-compatible constructor for integer weight.
     */
    public MatchedKeyword(String keyword, String source, int weight) {
        this(keyword, keyword, source, (double) weight);
    }
}
