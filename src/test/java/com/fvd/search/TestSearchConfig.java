package com.fvd.search;

/**
 * Test helper implementing SearchConfig with hardcoded defaults matching
 * the @WithDefault annotations. Used in unit tests that construct services
 * manually without CDI.
 */
public class TestSearchConfig implements SearchConfig {

    @Override
    public Boost boost() {
        return new TestBoost();
    }

    @Override
    public Fuzzy fuzzy() {
        return new TestFuzzy();
    }

    @Override
    public Index index() {
        return new TestIndex();
    }

    @Override
    public Snippet snippet() {
        return new TestSnippet();
    }

    @Override
    public Related related() {
        return new TestRelated();
    }

    public static class TestBoost implements Boost {
        @Override
        public int filenameBoost() {
            return 10;
        }

        @Override
        public int titleBoost() {
            return 5;
        }

        @Override
        public int importBoost() {
            return 5;
        }

        @Override
        public int sectionTitleBoost() {
            return 5;
        }

        @Override
        public double multiKeywordBoost() {
            return 1.5;
        }

        @Override
        public double prefixMatchMultiplier() {
            return 0.8;
        }

        @Override
        public int annotationBoost() {
            return 10;
        }

        @Override
        public String annotationPackages() {
            return "io.quarkus,jakarta,org.eclipse.microprofile,javax";
        }
    }

    public static class TestFuzzy implements Fuzzy {
        @Override
        public double levenshteinWeight() {
            return 0.4;
        }

        @Override
        public double containmentWeight() {
            return 0.35;
        }

        @Override
        public double wordOverlapWeight() {
            return 0.25;
        }

        @Override
        public double defaultThreshold() {
            return 0.3;
        }

        @Override
        public double containmentPartialThreshold() {
            return 0.5;
        }

        @Override
        public double wordOverlapKeywordThreshold() {
            return 0.3;
        }
    }

    public static class TestIndex implements Index {
        @Override
        public int minKeywordScore() {
            return 2;
        }

        @Override
        public int minTokenLength() {
            return 3;
        }
    }

    public static class TestSnippet implements Snippet {
        @Override
        public int contextSize() {
            return 100;
        }
    }

    public static class TestRelated implements Related {
        @Override
        public int defaultLimit() {
            return 5;
        }

        @Override
        public int maxLimit() {
            return 20;
        }

        @Override
        public double minSimilarity() {
            return 0.05;
        }

        @Override
        public int maxSharedKeywords() {
            return 10;
        }
    }
}
