package com.fvd.search;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "search")
public interface SearchConfig {

    Boost boost();
    Fuzzy fuzzy();
    Index index();
    Snippet snippet();

    interface Boost {
        @WithDefault("10")
        int filenameBoost();

        @WithDefault("5")
        int titleBoost();

        @WithDefault("5")
        int importBoost();

        @WithDefault("5")
        int sectionTitleBoost();

        @WithDefault("1.5")
        double multiKeywordBoost();

        @WithDefault("0.8")
        double prefixMatchMultiplier();
    }

    interface Fuzzy {
        @WithDefault("0.4")
        double levenshteinWeight();

        @WithDefault("0.35")
        double containmentWeight();

        @WithDefault("0.25")
        double wordOverlapWeight();

        @WithDefault("0.3")
        double defaultThreshold();

        @WithDefault("0.5")
        double containmentPartialThreshold();

        @WithDefault("0.3")
        double wordOverlapKeywordThreshold();
    }

    interface Index {
        @WithDefault("2")
        int minKeywordScore();

        @WithDefault("3")
        int minTokenLength();
    }

    interface Snippet {
        @WithDefault("100")
        int contextSize();
    }
}
