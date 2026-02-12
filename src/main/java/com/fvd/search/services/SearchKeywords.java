package com.fvd.search.services;

import com.fvd.common.Stemmer;
import lombok.experimental.UtilityClass;

import java.util.HashSet;
import java.util.List;
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
}
