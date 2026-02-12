package com.fvd.indexs.indexers;

import com.fvd.common.Stemmer;
import com.fvd.common.StopWords;
import com.fvd.docs.parser.DocParser;
import com.fvd.docs.stores.DocStore;
import com.fvd.indexs.stores.KeywordIndexStore;
import com.fvd.search.KeywordScoringConfig;
import com.fvd.search.SearchConfig;
import com.fvd.search.services.KeywordScorer;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Indexes keywords from documents with hierarchical scoring based on source location.
 * Keywords are weighted by their structural position: filename, title, section, subtitle, or body.
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class KeywordIndexer {

    public static final Set<String> WORD_INDEX_BLACK_LIST = StopWords.DEFAULT;

    private static final Pattern SECTION_HEADER = Pattern.compile("^(={1,5})\\s+(.+)$");

    private final DocStore docStore;
    private final KeywordIndexStore keywordIndexStore;
    private final DocParser parser;
    private final SearchConfig searchConfig;
    private final KeywordScorer keywordScorer;

    public KeywordIndex build(String version, List<String> filePaths) {
        return build(version, filePaths, "quarkus-core");
    }

    public KeywordIndex build(String version, List<String> filePaths, String extension) {
        List<FileKeywordEntry> fileEntries = new ArrayList<>();

        for (String filePath : filePaths) {
            Optional<String> content = docStore.read(version, filePath);
            if (content.isEmpty()) {
                continue;
            }
            FileKeywordEntry entry = buildFileEntry(filePath, content.get());
            entry.extension = extension;
            fileEntries.add(entry);
        }

        KeywordIndex index = new KeywordIndex(fileEntries);
        persist(version, index);
        return index;
    }

    public KeywordIndex build(String version, Map<String, List<String>> filePathsByExtension) {
        List<FileKeywordEntry> fileEntries = new ArrayList<>();

        for (Map.Entry<String, List<String>> entry : filePathsByExtension.entrySet()) {
            String extension = entry.getKey();
            for (String filePath : entry.getValue()) {
                Optional<String> content = docStore.read(version, filePath);
                if (content.isEmpty()) {
                    continue;
                }
                FileKeywordEntry fileEntry = buildFileEntry(filePath, content.get());
                fileEntry.extension = extension;
                fileEntries.add(fileEntry);
            }
        }

        KeywordIndex index = new KeywordIndex(fileEntries);
        persist(version, index);
        return index;
    }

    private Map<String, Integer> filterFileEntries(Map<String, Integer> originalFileKeywords) {
        int minScore = searchConfig.index().minKeywordScore();
        Map<String, Integer> filteredFileKeywords = new HashMap<>();
        for (Map.Entry<String, Integer> fileEntry : originalFileKeywords.entrySet()) {
            if (fileEntry.getValue() >= minScore) {
                filteredFileKeywords.put(fileEntry.getKey(), fileEntry.getValue());
            }
        }
        return filteredFileKeywords;
    }

    /**
     * Filter keyword scores by minimum score threshold.
     */
    private Map<String, KeywordWithSource> filterKeywordScores(Map<String, KeywordWithSource> keywords) {
        int minScore = searchConfig.index().minKeywordScore();
        Map<String, KeywordWithSource> filtered = new HashMap<>();
        for (Map.Entry<String, KeywordWithSource> entry : keywords.entrySet()) {
            if (entry.getValue().score >= minScore) {
                filtered.put(entry.getKey(), entry.getValue());
            }
        }
        return filtered;
    }

    FileKeywordEntry buildFileEntry(String filePath, String content) {
        // Extract keywords with source tracking
        Map<String, KeywordWithSource> keywordsWithSource = extractKeywordsWithSource(filePath, content);
        List<KeywordScore> fileScores = toSortedScoresWithSource(filterKeywordScores(keywordsWithSource));

        // Section-level keywords with source tracking
        List<DocParser.Section> sections = parser.parseSections(content);
        List<SectionKeywordEntry> sectionEntries = new ArrayList<>();
        
        for (DocParser.Section section : sections) {
            Map<String, KeywordWithSource> sectionKeywords = extractSectionKeywordsWithSource(section);
            List<KeywordScore> sectionScores = toSortedScoresWithSource(sectionKeywords);
            sectionEntries.add(new SectionKeywordEntry(
                    section.title(), section.startLine(), section.endLine(), sectionScores));
        }

        return new FileKeywordEntry(filePath, fileScores, sectionEntries);
    }

    /**
     * Extract keywords from document content with source location tracking.
     * Keywords are weighted based on where they appear: filename, title, section, subtitle, body.
     */
    Map<String, KeywordWithSource> extractKeywordsWithSource(String filePath, String content) {
        Map<String, KeywordWithSource> keywords = new HashMap<>();
        
        // 1. Extract body keywords (base score)
        Map<String, Integer> bodyKeywords = parser.extractKeywords(content);
        for (Map.Entry<String, Integer> entry : bodyKeywords.entrySet()) {
            String stemmed = entry.getKey();
            int frequency = entry.getValue();
            double score = keywordScorer.calculateScore(KeywordScorer.SOURCE_BODY, frequency);
            keywords.put(stemmed, new KeywordWithSource(stemmed, (int) Math.round(score), KeywordScorer.SOURCE_BODY, frequency));
        }

        // 2. Apply filename boost (highest priority)
        applyFilenameBoostWithSource(filePath, keywords);
        
        // 3. Extract title and section keywords from content
        applyHeadingBoostsWithSource(content, keywords);

        return keywords;
    }

    /**
     * Extract section keywords with source tracking.
     */
    Map<String, KeywordWithSource> extractSectionKeywordsWithSource(DocParser.Section section) {
        Map<String, KeywordWithSource> keywords = new HashMap<>();
        
        // Add section body keywords
        for (Map.Entry<String, Integer> entry : section.keywords().entrySet()) {
            String stemmed = entry.getKey();
            int frequency = entry.getValue();
            double score = keywordScorer.calculateScore(KeywordScorer.SOURCE_BODY, frequency);
            keywords.put(stemmed, new KeywordWithSource(stemmed, (int) Math.round(score), KeywordScorer.SOURCE_BODY, frequency));
        }
        
        // Apply section title boost
        if (section.title() != null && !section.title().isBlank()) {
            int headingLevel = detectHeadingLevel(section.title());
            String source = keywordScorer.getSourceFromHeadingLevel(Math.max(headingLevel, 2));
            
            List<String> titleTokens = parser.tokenize(section.title());
            for (String token : titleTokens) {
                String stemmed = Stemmer.stem(token);
                KeywordWithSource existing = keywords.get(stemmed);
                int frequency = existing != null ? existing.frequency + 1 : 1;
                double newScore = keywordScorer.calculateScore(source, frequency);
                
                // Use higher score if keyword exists with lower score
                if (existing == null || newScore > existing.score) {
                    keywords.put(stemmed, new KeywordWithSource(stemmed, (int) Math.round(newScore), source, frequency));
                }
            }
        }
        
        return keywords;
    }

    /**
     * Apply filename keyword boost with source tracking.
     */
    private void applyFilenameBoostWithSource(String filePath, Map<String, KeywordWithSource> keywords) {
        List<String> filenameKeywords = keywordScorer.extractStemmedFilenameKeywords(filePath);
        
        for (String stemmed : filenameKeywords) {
            KeywordWithSource existing = keywords.get(stemmed);
            int frequency = existing != null ? existing.frequency + 1 : 1;
            double newScore = keywordScorer.calculateScore(KeywordScorer.SOURCE_FILENAME, frequency);
            
            // Filename has highest priority, always use it
            if (existing == null || newScore > existing.score) {
                keywords.put(stemmed, new KeywordWithSource(stemmed, (int) Math.round(newScore), KeywordScorer.SOURCE_FILENAME, frequency));
            }
        }
    }

    /**
     * Apply heading-based boosts (title, section, subtitle) with source tracking.
     */
    private void applyHeadingBoostsWithSource(String content, Map<String, KeywordWithSource> keywords) {
        if (content == null || content.isBlank()) {
            return;
        }
        
        String[] lines = content.split("\n", -1);
        boolean foundTitle = false;
        
        for (String line : lines) {
            Matcher matcher = SECTION_HEADER.matcher(line.trim());
            if (matcher.matches()) {
                int headingLevel = matcher.group(1).length();
                String headingText = matcher.group(2).trim();
                
                String source;
                if (headingLevel == 1 && !foundTitle) {
                    source = KeywordScorer.SOURCE_TITLE;
                    foundTitle = true;
                } else if (headingLevel == 2) {
                    source = KeywordScorer.SOURCE_SECTION;
                } else {
                    source = KeywordScorer.SOURCE_SUBTITLE;
                }
                
                List<String> tokens = parser.tokenize(headingText);
                for (String token : tokens) {
                    String stemmed = Stemmer.stem(token);
                    KeywordWithSource existing = keywords.get(stemmed);
                    int frequency = existing != null ? existing.frequency + 1 : 1;
                    double newScore = keywordScorer.calculateScore(source, frequency);
                    
                    // Use higher-priority source if keyword already exists
                    if (existing == null || 
                        keywordScorer.getMultiplier(source) > keywordScorer.getMultiplier(existing.source)) {
                        keywords.put(stemmed, new KeywordWithSource(stemmed, (int) Math.round(newScore), source, frequency));
                    } else if (existing != null && source.equals(existing.source)) {
                        // Same source, update score with new frequency
                        keywords.put(stemmed, new KeywordWithSource(stemmed, (int) Math.round(newScore), source, frequency));
                    }
                }
            }
        }
    }

    /**
     * Detect heading level from section title (used for sections parsed by DocParser).
     */
    private int detectHeadingLevel(String title) {
        // Default to section level for parsed sections
        return 2;
    }

    // Legacy methods for backward compatibility
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
        int boost = searchConfig.boost().filenameBoost();
        List<String> filenameTokens = parser.tokenize(filename.replace("-", " ").replace("_", " "));
        for (String token : filenameTokens) {
            keywords.merge(Stemmer.stem(token), boost, Integer::sum);
        }
    }

    private void applyTitleBoost(String title, Map<String, Integer> keywords) {
        if (title == null || title.isBlank()) {
            return;
        }
        int boost = searchConfig.boost().titleBoost();
        List<String> titleTokens = parser.tokenize(title);
        for (String token : titleTokens) {
            keywords.merge(Stemmer.stem(token), boost, Integer::sum);
        }
    }

    private List<KeywordScore> toSortedScores(Map<String, Integer> keywords) {
        return KeywordScoreUtils.toSortedScores(keywords);
    }

    /**
     * Convert keyword map with source info to sorted list of KeywordScore.
     */
    private List<KeywordScore> toSortedScoresWithSource(Map<String, KeywordWithSource> keywords) {
        return keywords.values().stream()
                .sorted((a, b) -> Integer.compare(b.score, a.score))
                .map(kws -> new KeywordScore(kws.word, kws.score, kws.source, kws.frequency))
                .toList();
    }

    private void persist(String version, KeywordIndex index) {
        keywordIndexStore.write(version, index);
    }

    /**
     * Internal record for tracking keyword source during extraction.
     */
    record KeywordWithSource(String word, int score, String source, int frequency) {
    }
}
