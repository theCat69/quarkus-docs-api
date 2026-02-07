package com.fvd;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class SearchService {

    private static final int MAX_FILE_RESULTS = 10;
    private static final int MAX_SECTION_RESULTS = 5;
    private static final double MULTI_KEYWORD_BOOST = 1.5;

    private final KeywordIndexStore keywordIndexStore;
    private final ObjectMapper objectMapper;

    @Inject
    public SearchService(KeywordIndexStore keywordIndexStore, ObjectMapper objectMapper) {
        this.keywordIndexStore = keywordIndexStore;
        this.objectMapper = objectMapper;
    }

    public List<FileSearchResult> searchFiles(String version, List<String> keywords) {
        KeywordIndex index = loadIndex(version);
        if (index == null) {
            return List.of();
        }

        Set<String> keywordSet = new HashSet<>(keywords.stream()
                .map(String::toLowerCase).toList());
        Map<String, Double> scores = new HashMap<>();
        Map<String, Integer> matchedKeywordCounts = new HashMap<>();

        for (FileKeywordEntry file : index.files) {
            double score = 0;
            int matchedCount = 0;
            for (KeywordScore ks : file.keywords) {
                if (keywordSet.contains(ks.word)) {
                    score += ks.score;
                    matchedCount++;
                }
            }
            if (score > 0) {
                if (matchedCount > 1) {
                    score *= MULTI_KEYWORD_BOOST;
                }
                scores.put(file.path, score);
                matchedKeywordCounts.put(file.path, matchedCount);
            }
        }

        return scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(MAX_FILE_RESULTS)
                .map(e -> new FileSearchResult(e.getKey(), e.getValue()))
                .toList();
    }

    public List<SectionSearchResult> searchSections(String version, List<String> keywords,
                                                     List<String> filePaths) {
        KeywordIndex index = loadIndex(version);
        if (index == null) {
            return List.of();
        }

        Set<String> keywordSet = new HashSet<>(keywords.stream()
                .map(String::toLowerCase).toList());
        Set<String> filePathSet = new HashSet<>(filePaths);
        List<SectionSearchResult> results = new ArrayList<>();

        for (FileKeywordEntry file : index.files) {
            if (!filePathSet.contains(file.path)) {
                continue;
            }
            for (SectionKeywordEntry section : file.sections) {
                double score = 0;
                for (KeywordScore ks : section.keywords) {
                    if (keywordSet.contains(ks.word)) {
                        score += ks.score;
                    }
                }
                if (score > 0) {
                    results.add(new SectionSearchResult(
                            file.path, section.title, section.start, section.end, score));
                }
            }
        }

        results.sort(Comparator.comparingDouble((SectionSearchResult r) -> r.score).reversed());
        if (results.size() > MAX_SECTION_RESULTS) {
            return results.subList(0, MAX_SECTION_RESULTS);
        }
        return results;
    }

    private KeywordIndex loadIndex(String version) {
        Optional<String> json = keywordIndexStore.read(version);
        if (json.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(json.get(), KeywordIndex.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse keyword index for version: " + version, e);
        }
    }
}
