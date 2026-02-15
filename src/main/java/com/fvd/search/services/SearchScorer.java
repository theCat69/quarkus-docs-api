package com.fvd.search.services;

import com.fvd.indexs.indexers.KeywordScore;

import java.util.List;
import java.util.Map;

/**
 * Abstracts search scoring to support multiple backend implementations.
 * - SQLite: Custom exact/prefix matching with configurable multipliers.
 * - PostgreSQL: Delegates to ts_rank() via native queries.
 */
public interface SearchScorer {

    MatchResult computeScore(List<KeywordScore> indexedKeywords, Map<String, String> stemmedToOriginal);

    record MatchResult(double score, int matchedCount, List<MatchedKeyword> matchedKeywords) {
        public static final MatchResult EMPTY = new MatchResult(0.0, 0, List.of());

        public boolean hasMatches() {
            return matchedCount > 0;
        }
    }
}
