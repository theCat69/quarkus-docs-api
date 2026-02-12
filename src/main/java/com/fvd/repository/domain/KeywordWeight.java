package com.fvd.repository.domain;

import com.fvd.search.services.KeywordScorer;
import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Represents a keyword with its associated weight/score in an index,
 * including source location and frequency information for hierarchical scoring.
 *
 * @param keyword the original keyword
 * @param stemmed the stemmed version of the keyword
 * @param source the source location (filename, title, section, subtitle, body)
 * @param weight the weighted score for this keyword
 * @param frequency the number of occurrences
 * @param lineNumber the line number where the keyword first appears (optional)
 */
@RegisterForReflection
public record KeywordWeight(
        String keyword,
        String stemmed,
        String source,
        double weight,
        int frequency,
        int lineNumber
) {
    /**
     * Backward-compatible constructor for simple (word, weight) usage.
     */
    public KeywordWeight(String word, int weight) {
        this(word, word, KeywordScorer.SOURCE_BODY, (double) weight, 1, 0);
    }

    /**
     * Legacy accessor for backward compatibility.
     */
    public String word() {
        return stemmed;
    }
}
