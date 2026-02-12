package com.fvd.search;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.List;

/**
 * Configuration for hierarchical keyword scoring.
 * Keywords are weighted based on their structural location within documents.
 */
@ConfigMapping(prefix = "app.scoring")
public interface KeywordScoringConfig {

    /**
     * Weight multiplier for keywords extracted from filename.
     */
    @WithDefault("10.0")
    double filenameWeight();

    /**
     * Weight multiplier for keywords from document title (H1 heading).
     */
    @WithDefault("8.0")
    double titleWeight();

    /**
     * Weight multiplier for keywords from section titles (H2 headings).
     */
    @WithDefault("5.0")
    double sectionWeight();

    /**
     * Weight multiplier for keywords from subtitles (H3+ headings).
     */
    @WithDefault("2.0")
    double subtitleWeight();

    /**
     * Weight multiplier for keywords from body text.
     */
    @WithDefault("1.0")
    double bodyWeight();

    /**
     * Stopwords to filter from filename keyword extraction.
     */
    @WithDefault("guide,tutorial,doc,adoc,md,reference,overview")
    List<String> filenameStopwords();
}
