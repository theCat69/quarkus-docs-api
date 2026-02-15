package com.fvd.search.services;

import com.fvd.common.Stemmer;
import lombok.experimental.UtilityClass;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Utility for preparing search keywords by applying stemming and lowercasing.
 */
@UtilityClass
public class SearchKeywords {

    public Set<String> prepare(List<String> keywords) {
        return new HashSet<>(keywords.stream()
                .map(k -> Stemmer.stem(k.toLowerCase()))
                .toList());
    }

    /**
     * Prepares keywords by stemming and lowercasing, returning a mapping from
     * stemmed form to the original (lowercased) keyword. When multiple keywords
     * stem to the same form, the first original is kept.
     *
     * @param keywords the raw search keywords
     * @return a map of stemmed keyword to original (lowercased) keyword
     */
    public Map<String, String> prepareWithOriginals(List<String> keywords) {
        Map<String, String> stemmedToOriginal = new LinkedHashMap<>();
        for (String keyword : keywords) {
            String lower = keyword.toLowerCase();
            String stem = Stemmer.stem(lower);
            stemmedToOriginal.putIfAbsent(stem, lower);
        }
        return stemmedToOriginal;
    }
}
