package com.fvd.search.services;

import com.fvd.cache.services.CacheService;
import com.fvd.common.Stemmer;
import com.fvd.common.matchers.FuzzyMatcher;
import com.fvd.docs.exceptions.DocNotFoundException;
import com.fvd.docs.parser.DocParser;
import com.fvd.docs.stores.DocStore;
import com.fvd.indexs.indexers.CodeSampleEntry;
import com.fvd.indexs.indexers.CodeSampleIndex;
import com.fvd.indexs.indexers.FileKeywordEntry;
import com.fvd.indexs.indexers.KeywordIndex;
import com.fvd.indexs.indexers.KeywordScore;
import com.fvd.indexs.indexers.SectionKeywordEntry;
import com.fvd.indexs.stores.CodeSampleIndexStore;
import com.fvd.indexs.stores.KeywordIndexStore;
import com.fvd.repository.domain.MatchedKeyword;
import com.fvd.search.SearchConfig;
import jakarta.annotation.Nonnull;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class SearchService {

    private final KeywordIndexStore keywordIndexStore;
    private final CodeSampleIndexStore codeSampleIndexStore;
    private final DocStore docStore;
    private final DocParser docParser;
    private final CacheService cacheService;
    private final SearchConfig searchConfig;
    private final FuzzyMatcher fuzzyMatcher;

    private final Map<String, KeywordIndex> indexCache = new ConcurrentHashMap<>();
    private final Map<String, CodeSampleIndex> codeSampleIndexCache = new ConcurrentHashMap<>();

    public List<String> listVersions() {
        return cacheService.listCachedVersions();
    }

    public PaginatedResult<FileSearchResult> searchFiles(String version, List<String> keywords,
                                                         String extension, int limit, int offset) {
        KeywordIndex index = getOrBuildIndex(version);
        if (index == null) {
            return new PaginatedResult<>(List.of(), 0);
        }

        Set<String> keywordSet = new HashSet<>(keywords.stream()
                .map(k -> Stemmer.stem(k.toLowerCase())).toList());
        List<FileSearchResult> all = getFileResults(index, keywordSet, extension);

        all.sort(Comparator.comparingDouble((FileSearchResult r) -> r.score).reversed());
        return paginate(all, limit, offset);
    }

    @Nonnull
    private List<FileSearchResult> getFileResults(KeywordIndex index, Set<String> keywordSet, String extension) {
        double multiKeywordBoost = searchConfig.boost().multiKeywordBoost();
        List<FileSearchResult> results = new ArrayList<>();

        for (FileKeywordEntry file : index.files) {
            if (extension != null && !extension.isBlank() && !extension.equals(file.extension)) {
                continue;
            }
            MatchAccumulator acc = computeMatchingScore(file.keywords, keywordSet);
            if (acc.score > 0) {
                double finalScore = acc.score;
                if (acc.matchedCount > 1) {
                    finalScore *= multiKeywordBoost;
                }
                results.add(new FileSearchResult(file.path, finalScore,
                        acc.matchedKeywords, file.extension));
            }
        }
        return results;
    }

    public PaginatedResult<SectionSearchResult> searchSections(String version, List<String> keywords,
                                                    List<String> filePaths, String sectionTitle,
                                                    String extension, int limit, int offset) {
        KeywordIndex index = getOrBuildIndex(version);
        if (index == null) {
            return new PaginatedResult<>(List.of(), 0);
        }

        Set<String> keywordSet = new HashSet<>(keywords.stream()
                .map(k -> Stemmer.stem(k.toLowerCase())).toList());
        Set<String> originalKeywords = new HashSet<>(keywords.stream()
                .map(String::toLowerCase).toList());
        Set<String> filePathSet = (filePaths == null || filePaths.isEmpty())
                ? null : new HashSet<>(filePaths);
        double multiKeywordBoost = searchConfig.boost().multiKeywordBoost();
        List<SectionSearchResult> results = new ArrayList<>();

        for (FileKeywordEntry file : index.files) {
            if (filePathSet != null && !filePathSet.contains(file.path)) {
                continue;
            }
            if (extension != null && !extension.isBlank() && !extension.equals(file.extension)) {
                continue;
            }
            for (SectionKeywordEntry section : file.sections) {
                MatchAccumulator acc = computeMatchingScore(section.keywords, keywordSet);
                if (acc.score > 0) {
                    double finalScore = acc.score;
                    if (acc.matchedCount > 1) {
                        finalScore *= multiKeywordBoost;
                    }
                    results.add(new SectionSearchResult(
                            file.path, section.title, section.start, section.end, finalScore,
                            acc.matchedKeywords, file.extension));
                }
            }
        }

        // Apply sectionTitle fuzzy filter if provided
        if (sectionTitle != null && !sectionTitle.isBlank()) {
            List<String> uniqueTitles = results.stream()
                    .map(r -> r.section)
                    .distinct()
                    .toList();
            Optional<FuzzyMatcher.MatchResult> fuzzyResult = fuzzyMatcher.bestMatch(sectionTitle, uniqueTitles);
            if (fuzzyResult.isPresent()) {
                String finalMatchedTitle = fuzzyResult.get().value();
                double finalMatchScore = fuzzyResult.get().score();
                results = results.stream()
                        .filter(r -> r.section.equals(finalMatchedTitle))
                        .collect(java.util.stream.Collectors.toList());
                for (SectionSearchResult r : results) {
                    r.matchedSectionTitle = finalMatchedTitle;
                    r.sectionMatchScore = finalMatchScore;
                }
            } else {
                return new PaginatedResult<>(List.of(), 0);
            }
        }

        results.sort(Comparator.comparingDouble((SectionSearchResult r) -> r.score).reversed());
        PaginatedResult<SectionSearchResult> paginated = paginate(results, limit, offset);

        // Generate snippets for paginated results only
        for (SectionSearchResult result : paginated.items()) {
            generateSectionSnippet(version, result, originalKeywords, keywordSet);
        }

        return paginated;
    }

    private void generateSectionSnippet(String version, SectionSearchResult result,
                                        Set<String> originalKeywords, Set<String> stemmedKeywords) {
        if (docStore == null) {
            return;
        }
        Optional<String> docContent = docStore.read(version, result.path);
        if (docContent.isEmpty()) {
            return;
        }

        String content = docContent.get();
        String[] lines = content.split("\n", -1);
        int startIdx = Math.max(0, result.start - 1);
        int endIdx = Math.min(lines.length, result.end);
        String sectionContent = String.join("\n", Arrays.copyOfRange(lines, startIdx, endIdx));

        if (sectionContent.isEmpty()) {
            return;
        }

        // Find first keyword occurrence (case-insensitive) in section content.
        // Try original (unstemmed) keywords first, then fall back to stemmed keywords.
        String lowerSection = sectionContent.toLowerCase();
        int bestOffset = findFirstKeywordOffset(lowerSection, originalKeywords);
        if (bestOffset < 0) {
            bestOffset = findFirstKeywordOffset(lowerSection, stemmedKeywords);
        }

        if (bestOffset >= 0) {
            result.snippet = generateSnippet(sectionContent, bestOffset);
        } else {
            int len = Math.min(100, sectionContent.length());
            result.snippet = sectionContent.substring(0, len).replaceAll("\\s+", " ").trim();
            if (sectionContent.length() > 100) {
                result.snippet = result.snippet + "...";
            }
        }
    }

    private int findFirstKeywordOffset(String lowerContent, Set<String> keywords) {
        int bestOffset = -1;
        for (String keyword : keywords) {
            int idx = lowerContent.indexOf(keyword.toLowerCase());
            if (idx >= 0 && (bestOffset < 0 || idx < bestOffset)) {
                bestOffset = idx;
            }
        }
        return bestOffset;
    }

    public SectionContentResult getSectionContent(String version, String filePath, String sectionTitle) {
        Optional<String> docContent = docStore.read(version, filePath);
        if (docContent.isEmpty()) {
            throw new DocNotFoundException("Document not found: " + filePath + " for version: " + version);
        }

        String content = docContent.get();
        List<DocParser.Section> sections = docParser.parseSections(content);
        String[] lines = content.split("\n", -1);

        // Try exact match first (case-insensitive)
        for (DocParser.Section section : sections) {
            if (section.title().equalsIgnoreCase(sectionTitle)) {
                return buildSectionResult(filePath, section, lines, section.title(), 1.0, "exact");
            }
        }

        // Fall back to fuzzy matching
        List<String> titles = sections.stream()
                .map(DocParser.Section::title)
                .filter(t -> !t.isEmpty())
                .toList();

        Optional<FuzzyMatcher.MatchResult> fuzzyMatch = fuzzyMatcher.bestMatch(sectionTitle, titles);
        if (fuzzyMatch.isPresent()) {
            FuzzyMatcher.MatchResult match = fuzzyMatch.get();
            for (DocParser.Section section : sections) {
                if (section.title().equals(match.value())) {
                    return buildSectionResult(filePath, section, lines,
                            match.value(), match.score(), match.matchType());
                }
            }
        }

        throw new DocNotFoundException(
                "Section not found: '" + sectionTitle + "' in " + filePath + " for version: " + version);
    }

    private SectionContentResult buildSectionResult(String filePath, DocParser.Section section,
                                                     String[] lines, String matchedTitle,
                                                     double matchScore, String matchType) {
        int startIdx = Math.max(0, section.startLine() - 1);
        int endIdx = Math.min(lines.length, section.endLine());
        String sectionContent = String.join("\n",
                Arrays.copyOfRange(lines, startIdx, endIdx));
        return new SectionContentResult(
                filePath, section.title(), section.startLine(), section.endLine(),
                sectionContent, matchedTitle, matchScore, matchType);
    }

    public PaginatedResult<CodeSampleSearchResult> searchCodeSamples(String version, List<String> keywords,
                                                          String filePath, String sectionTitle,
                                                          String extension, int limit, int offset) {
        CodeSampleIndex index = getOrLoadCodeSampleIndex(version);
        if (index == null) {
            return new PaginatedResult<>(List.of(), 0);
        }

        Set<String> keywordSet = new HashSet<>(keywords.stream()
                .map(k -> Stemmer.stem(k.toLowerCase())).toList());
        double multiKeywordBoost = searchConfig.boost().multiKeywordBoost();

        // Resolve fuzzy section title match if sectionTitle filter is provided
        String matchedTitle = null;
        double matchScore = 0.0;
        if (sectionTitle != null && !sectionTitle.isBlank()) {
            // Collect unique section titles from candidate samples (after filePath filtering)
            List<String> uniqueTitles = index.samples.stream()
                    .filter(s -> filePath == null || filePath.isBlank() || s.filePath.equals(filePath))
                    .map(s -> s.sectionTitle)
                    .distinct()
                    .toList();
            Optional<FuzzyMatcher.MatchResult> fuzzyResult = fuzzyMatcher.bestMatch(sectionTitle, uniqueTitles);
            if (fuzzyResult.isPresent()) {
                matchedTitle = fuzzyResult.get().value();
                matchScore = fuzzyResult.get().score();
            } else {
                // No section title matched above threshold — return empty
                return new PaginatedResult<>(List.of(), 0);
            }
        }

        List<CodeSampleSearchResult> results = new ArrayList<>();
        for (CodeSampleEntry sample : index.samples) {
            if (filePath != null && !filePath.isBlank() && !sample.filePath.equals(filePath)) {
                continue;
            }
            if (matchedTitle != null && !sample.sectionTitle.equals(matchedTitle)) {
                continue;
            }
            if (extension != null && !extension.isBlank() && !extension.equals(sample.extension)) {
                continue;
            }

            MatchAccumulator acc = computeMatchingScore(sample.keywords, keywordSet);
            if (acc.score > 0) {
                double finalScore = acc.score;
                if (acc.matchedCount > 1) {
                    finalScore *= multiKeywordBoost;
                }
                results.add(new CodeSampleSearchResult(
                        sample.filePath, sample.sectionTitle, matchedTitle, matchScore,
                        sample.language, sample.content, sample.startLine, sample.endLine, finalScore,
                        acc.matchedKeywords, sample.extension));
            }
        }

        results.sort(Comparator.comparingDouble((CodeSampleSearchResult r) -> r.score).reversed());
        return paginate(results, limit, offset);
    }

    /**
     * Invalidates the in-memory cache for a specific version.
     * Should be called after the keyword index is rebuilt (e.g., during cache refresh).
     */
    public void invalidateCache(String version) {
        indexCache.remove(version);
        codeSampleIndexCache.remove(version);
    }

    private <T> PaginatedResult<T> paginate(List<T> all, int limit, int offset) {
        int total = all.size();
        if (offset >= total) {
            return new PaginatedResult<>(List.of(), total);
        }
        int end = Math.min(offset + limit, total);
        return new PaginatedResult<>(all.subList(offset, end), total);
    }

    record MatchAccumulator(double score, int matchedCount, List<MatchedKeyword> matchedKeywords) {}

    /**
     * Computes the total matching score for a list of indexed keywords against a set of query keywords.
     * Supports both exact matches (full score) and prefix matches (discounted by PREFIX_MATCH_MULTIPLIER).
     * Exact matches take precedence over prefix matches for the same indexed keyword.
     * Returns MatchedKeyword objects with source and weight information.
     */
    MatchAccumulator computeMatchingScore(List<KeywordScore> indexedKeywords, Set<String> queryKeywords) {
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
                MatchedKeyword existing = matchedByQuery.get(bestQueryKeyword);
                String source = ks.source != null ? ks.source : "body";
                if (existing == null || bestScore > existing.weight()) {
                    matchedByQuery.put(bestQueryKeyword, new MatchedKeyword(bestQueryKeyword, source, bestScore));
                }
            }
        }

        return new MatchAccumulator(totalScore, matchedByQuery.size(), List.copyOf(matchedByQuery.values()));
    }

    int computeLineNumber(String text, int charOffset) {
        int line = 1;
        for (int i = 0; i < charOffset && i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    String generateSnippet(String text, int matchOffset) {
        int contextSize = searchConfig.snippet().contextSize();
        int start = Math.max(0, matchOffset - contextSize);
        int end = Math.min(text.length(), matchOffset + contextSize);
        String snippet = text.substring(start, end).replaceAll("\\s+", " ").trim();
        if (start > 0) {
            snippet = "..." + snippet;
        }
        if (end < text.length()) {
            snippet = snippet + "...";
        }
        return snippet;
    }

    private KeywordIndex getOrBuildIndex(String version) {
        KeywordIndex cached = indexCache.get(version);
        if (cached != null) {
            return cached;
        }

        // Load from SQLite and cache
        Optional<KeywordIndex> index = keywordIndexStore.read(version);
        if (index.isPresent()) {
            indexCache.put(version, index.get());
            return index.get();
        }
        return null;
    }

    private CodeSampleIndex getOrLoadCodeSampleIndex(String version) {
        CodeSampleIndex cached = codeSampleIndexCache.get(version);
        if (cached != null) {
            return cached;
        }

        Optional<CodeSampleIndex> index = codeSampleIndexStore.read(version);
        if (index.isPresent()) {
            codeSampleIndexCache.put(version, index.get());
            return index.get();
        }
        return null;
    }

}
