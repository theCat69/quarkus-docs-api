package com.fvd.repository.domain;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Represents a matched keyword with its source location and weighted score.
 * Used in search results to indicate where keywords matched and their contribution
 * to the overall score.
 *
 * @param keyword the matched keyword (stemmed or original)
 * @param source the source location (filename, title, section, subtitle, body)
 * @param weight the weighted score contribution of this match
 */
@RegisterForReflection
public record MatchedKeyword(
        String keyword,
        String source,
        double weight
) {
    /**
     * Backward-compatible constructor for integer weight.
     */
    public MatchedKeyword(String keyword, String source, int weight) {
        this(keyword, source, (double) weight);
    }
}
