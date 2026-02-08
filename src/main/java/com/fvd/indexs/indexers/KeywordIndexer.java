package com.fvd.indexs.indexers;

import com.fvd.docs.parser.DocParser;
import com.fvd.docs.stores.DocStore;
import com.fvd.indexs.stores.KeywordIndexStore;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.*;

@ApplicationScoped
@RequiredArgsConstructor
public class KeywordIndexer {

    public static final Set<String> WORD_INDEX_BLACK_LIST = Set.of(
            "a",
            "an",
            "and",
            "the",
            "how",
            "does",
            "do",
            "is",
            "are",
            "was",
            "were",
            "what",
            "which",
            "who",
            "when",
            "where",
            "why",
            "in",
            "on",
            "at",
            "to",
            "for",
            "with",
            "from",
            "by",
            "of",
            "about",
            "explain",
            "show",
            "me",
            "work",
            "works",
            "working",
            "please",
            "your"
    );

    private static final int FILENAME_BOOST = 10;
    private static final int TITLE_BOOST = 5;

    private final DocStore docStore;
    private final KeywordIndexStore keywordIndexStore;
    private final DocParser parser;

    @ConfigProperty(name = "keywords.file.minimal.score", defaultValue = "2")
    Integer fileEntryKeywordMinimalScore;

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

    private Map<String, Integer> filterFileEntries(Map<String, Integer> originalFileKeywords) {
        Map<String, Integer> filteredFileKeywords = new HashMap<>();
        for (Map.Entry<String, Integer> fileEntry : originalFileKeywords.entrySet()) {
            if (fileEntry.getValue() >= fileEntryKeywordMinimalScore) {
                filteredFileKeywords.put(fileEntry.getKey(), fileEntry.getValue());
            }
        }
        return filteredFileKeywords;
    }

    FileKeywordEntry buildFileEntry(String filePath, String content) {
        // File-level keywords with filename boost
        Map<String, Integer> fileKeywords = parser.extractKeywords(content);
        applyFilenameBoost(filePath, fileKeywords);
        List<KeywordScore> fileScores = toSortedScores(filterFileEntries(fileKeywords));

        // Section-level keywords with title boost
        List<DocParser.Section> sections = parser.parseSections(content);
        List<SectionKeywordEntry> sectionEntries = new ArrayList<>();
        for (DocParser.Section section : sections) {
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
        // Remove file extension
        if (filename.endsWith(parser.fileSuffix())) {
            filename = filename.substring(0, filename.length() - parser.fileSuffix().length());
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
        keywordIndexStore.write(version, index);
    }
}
