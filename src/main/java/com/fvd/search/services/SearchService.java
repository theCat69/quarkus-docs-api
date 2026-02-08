package com.fvd.search.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fvd.asciidocs.parser.AsciidocParser;
import com.fvd.docs.exceptions.DocNotFoundException;
import com.fvd.docs.stores.DocStore;
import com.fvd.github.services.ZipDownloadService;
import com.fvd.indexs.indexers.*;
import com.fvd.indexs.stores.KeywordIndexStore;
import jakarta.annotation.Nonnull;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class SearchService {

    private static final int MAX_FILE_RESULTS = 10;
    private static final int MAX_SECTION_RESULTS = 5;
    private static final double MULTI_KEYWORD_BOOST = 1.5;

    private final KeywordIndexStore keywordIndexStore;
    private final ObjectMapper objectMapper;
    private final ZipDownloadService zipDownloadService;
    private final KeywordIndexer keywordIndexer;
    private final DocStore docStore;
    private final AsciidocParser asciidocParser;

    public List<FileSearchResult> searchFiles(String version, List<String> keywords) {
        ensureIndex(version);
        KeywordIndex index = loadIndex(version);
        if (index == null) {
            return List.of();
        }

        Set<String> keywordSet = new HashSet<>(keywords.stream()
                .map(String::toLowerCase).toList());
        Map<String, Double> scores = getScores(index, keywordSet);

        return scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(MAX_FILE_RESULTS)
                .map(e -> new FileSearchResult(e.getKey(), e.getValue()))
                .toList();
    }

    @Nonnull
    private static Map<String, Double> getScores(KeywordIndex index, Set<String> keywordSet) {
        Map<String, Double> scores = new HashMap<>();

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
            }
        }
        return scores;
    }

    public List<SectionSearchResult> searchSections(String version, List<String> keywords,
                                                    List<String> filePaths) {
        ensureIndex(version);
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

    public SectionContentResult getSectionContent(String version, String filePath, String sectionTitle) {
        Optional<String> docContent = docStore.read(version, filePath);
        if (docContent.isEmpty()) {
            throw new DocNotFoundException("Document not found: " + filePath + " for version: " + version);
        }

        String content = docContent.get();
        List<AsciidocParser.Section> sections = asciidocParser.parseSections(content);
        String[] lines = content.split("\n", -1);

        for (AsciidocParser.Section section : sections) {
            if (section.title().equalsIgnoreCase(sectionTitle)) {
                int startIdx = Math.max(0, section.startLine() - 1);
                int endIdx = Math.min(lines.length, section.endLine());
                String sectionContent = String.join("\n",
                        Arrays.copyOfRange(lines, startIdx, endIdx));
                return new SectionContentResult(
                        filePath, section.title(), section.startLine(), section.endLine(), sectionContent);
            }
        }

        throw new DocNotFoundException(
                "Section not found: '" + sectionTitle + "' in " + filePath + " for version: " + version);
    }

    private void ensureIndex(String version) {
        Optional<String> existing = keywordIndexStore.read(version);
        if (existing.isPresent()) {
            return;
        }
        if (zipDownloadService == null || keywordIndexer == null || docStore == null) {
            return;
        }
        try {
            List<String> files;
            if (docStore.docsExist(version)) {
                files = docStore.listDocFiles(version);
            } else {
                files = zipDownloadService.streamAndExtract(version);
            }
            keywordIndexer.build(version, files);
        } catch (Exception e) {
            log.warn("Failed to lazily build keyword index for version {}", version, e);
        }
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
