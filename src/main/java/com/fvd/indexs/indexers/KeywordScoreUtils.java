package com.fvd.indexs.indexers;

import lombok.experimental.UtilityClass;

import java.util.List;
import java.util.Map;

/**
 * Utility for converting keyword score maps to sorted lists.
 */
@UtilityClass
public class KeywordScoreUtils {

    public List<KeywordScore> toSortedScores(Map<String, Integer> keywords) {
        return keywords.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .map(e -> new KeywordScore(e.getKey(), e.getValue()))
                .toList();
    }
}
