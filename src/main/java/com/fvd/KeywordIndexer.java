package com.fvd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class KeywordIndexer {

    private static final int FILENAME_BOOST = 10;
    private static final int TITLE_BOOST = 5;

    private final DocStore docStore;
    private final KeywordIndexStore keywordIndexStore;
    private final AsciidocParser parser;
    private final ObjectMapper objectMapper;

    @Inject
    public KeywordIndexer(DocStore docStore, KeywordIndexStore keywordIndexStore,
                          AsciidocParser parser, ObjectMapper objectMapper) {
        this.docStore = docStore;
        this.keywordIndexStore = keywordIndexStore;
        this.parser = parser;
        this.objectMapper = objectMapper;
    }

    public KeywordIndex build(String version, List<String> filePaths) {
        List<FileKeywordEntry> fileEntries = new ArrayList<>();

        for (String filePath : filePaths) {
            Optional<String> content = docStore.read(version, filePath);
            if (content.isEmpty()) {
                continue;
            }
            FileKeywordEntry entry = buildFileEntry(filePath, content.get());
            fileEntries.add(entry);
        }

        KeywordIndex index = new KeywordIndex(fileEntries);
        persist(version, index);
        return index;
    }

    FileKeywordEntry buildFileEntry(String filePath, String content) {
        // File-level keywords with filename boost
        Map<String, Integer> fileKeywords = parser.extractKeywords(content);
        applyFilenameBoost(filePath, fileKeywords);
        List<KeywordScore> fileScores = toSortedScores(fileKeywords);

        // Section-level keywords with title boost
        List<AsciidocParser.Section> sections = parser.parseSections(content);
        List<SectionKeywordEntry> sectionEntries = new ArrayList<>();
        for (AsciidocParser.Section section : sections) {
            Map<String, Integer> sectionKeywords = new HashMap<>(section.keywords());
            applyTitleBoost(section.title(), sectionKeywords);
            List<KeywordScore> sectionScores = toSortedScores(sectionKeywords);
            sectionEntries.add(new SectionKeywordEntry(
                    section.title(), section.startLine(), section.endLine(), sectionScores));
        }

        return new FileKeywordEntry(filePath, fileScores, sectionEntries);
    }

    private void applyFilenameBoost(String filePath, Map<String, Integer> keywords) {
        String filename = filePath;
        int lastSlash = filePath.lastIndexOf('/');
        if (lastSlash >= 0) {
            filename = filePath.substring(lastSlash + 1);
        }
        // Remove .adoc extension
        if (filename.endsWith(".adoc")) {
            filename = filename.substring(0, filename.length() - 5);
        }
        List<String> filenameTokens = parser.tokenize(filename.replace("-", " ").replace("_", " "));
        for (String token : filenameTokens) {
            keywords.merge(token, FILENAME_BOOST, Integer::sum);
        }
    }

    private void applyTitleBoost(String title, Map<String, Integer> keywords) {
        if (title == null || title.isBlank()) {
            return;
        }
        List<String> titleTokens = parser.tokenize(title);
        for (String token : titleTokens) {
            keywords.merge(token, TITLE_BOOST, Integer::sum);
        }
    }

    private List<KeywordScore> toSortedScores(Map<String, Integer> keywords) {
        return keywords.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .map(e -> new KeywordScore(e.getKey(), e.getValue()))
                .toList();
    }

    private void persist(String version, KeywordIndex index) {
        try {
            String json = objectMapper.writeValueAsString(index);
            keywordIndexStore.write(version, json);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize keyword index", e);
        }
    }
}
