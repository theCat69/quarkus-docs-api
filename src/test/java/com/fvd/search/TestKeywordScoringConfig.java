package com.fvd.search;

import java.util.List;

/**
 * Test helper implementing KeywordScoringConfig with default values matching
 * the @WithDefault annotations. Used in unit tests that construct services
 * manually without CDI.
 */
public class TestKeywordScoringConfig implements KeywordScoringConfig {

    @Override
    public double filenameWeight() {
        return 10.0;
    }

    @Override
    public double titleWeight() {
        return 8.0;
    }

    @Override
    public double sectionWeight() {
        return 5.0;
    }

    @Override
    public double subtitleWeight() {
        return 2.0;
    }

    @Override
    public double bodyWeight() {
        return 1.0;
    }

    @Override
    public List<String> filenameStopwords() {
        return List.of("guide", "tutorial", "doc", "adoc", "md", "reference", "overview");
    }
}
