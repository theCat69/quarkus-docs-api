package com.fvd.search.services;

import com.fvd.indexs.indexers.KeywordScore;
import com.fvd.search.SearchConfig;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
@RequiredArgsConstructor
public class SqliteSearchScorer implements SearchScorer {

    private final SearchConfig searchConfig;

    @Override
    public MatchResult computeScore(List<KeywordScore> indexedKeywords, Set<String> queryKeywords) {
        double prefixMultiplier = searchConfig.boost().prefixMatchMultiplier();
        double totalScore = 0;
        Map<String, MatchedKeyword> matchedByQuery = new HashMap<>();

        for (KeywordScore ks : indexedKeywords) {
            double bestScore = 0;
            String bestQueryKeyword = null;

            for (String query : queryKeywords) {
                if (ks.word.equals(query)) {
                    // Exact match — full score, takes precedence
                    bestScore = ks.score;
                    bestQueryKeyword = query;
                    break;
                } else if (ks.word.startsWith(query)) {
                    // Prefix match — discounted score
                    double prefixScore = ks.score * prefixMultiplier;
                    if (prefixScore > bestScore) {
                        bestScore = prefixScore;
                        bestQueryKeyword = query;
                    }
                }
            }

            if (bestQueryKeyword != null) {
                totalScore += bestScore;
                // Track the matched keyword with source and weight
                // If the same query keyword matches multiple index keywords, use highest weight
                String source = ks.source != null ? ks.source : "body";
                MatchedKeyword existing = matchedByQuery.get(bestQueryKeyword);
                if (existing == null || bestScore > existing.weight()) {
                    matchedByQuery.put(bestQueryKeyword,
                            new MatchedKeyword(bestQueryKeyword, source, bestScore));
                }
            }
        }

        return new MatchResult(totalScore, matchedByQuery.size(), List.copyOf(matchedByQuery.values()));
    }
}
