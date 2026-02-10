package com.fvd.search.services;

import com.fvd.cache.services.CacheService;
import com.fvd.common.Stemmer;
import com.fvd.common.matchers.FuzzyMatcher;
import com.fvd.docs.exceptions.DocNotFoundException;
import com.fvd.docs.parser.DocParser;
import com.fvd.docs.stores.DocStore;
import com.fvd.indexs.indexers.CodeSampleEntry;
import com.fvd.indexs.indexers.CodeSampleIndex;
import com.fvd.indexs.indexers.ContentIndex;
import com.fvd.indexs.indexers.ContentOccurrence;
import com.fvd.indexs.indexers.FileKeywordEntry;
import com.fvd.indexs.indexers.KeywordIndex;
import com.fvd.indexs.indexers.KeywordScore;
import com.fvd.indexs.indexers.SectionKeywordEntry;
import com.fvd.indexs.stores.CodeSampleIndexStore;
import com.fvd.indexs.stores.ContentIndexStore;
import com.fvd.indexs.stores.KeywordIndexStore;
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
    private final ContentIndexStore contentIndexStore;
    private final DocStore docStore;
    private final DocParser docParser;
    private final CacheService cacheService;
    private final SearchConfig searchConfig;
    private final FuzzyMatcher fuzzyMatcher;

    private final Map<String, KeywordIndex> indexCache = new ConcurrentHashMap<>();
    private final Map<String, CodeSampleIndex> codeSampleIndexCache = new ConcurrentHashMap<>();
    private final Map<String, ContentIndex> contentIndexCache = new ConcurrentHashMap<>();

    public List<String> listVersions() {
        return cacheService.listCachedVersions();
    }

    public PaginatedResult<FileSearchResult> searchFiles(String version, List<String> keywords,
                                                         int limit, int offset) {
        KeywordIndex index = getOrBuildIndex(version);
        if (index == null) {
            return new PaginatedResult<>(List.of(), 0);
        }

        Set<String> keywordSet = new HashSet<>(keywords.stream()
                .map(k -> Stemmer.stem(k.toLowerCase())).toList());
        List<FileSearchResult> all = getFileResults(index, keywordSet);

        all.sort(Comparator.comparingDouble((FileSearchResult r) -> r.score).reversed());
        return paginate(all, limit, offset);
    }

    @Nonnull
    private List<FileSearchResult> getFileResults(KeywordIndex index, Set<String> keywordSet) {
        double multiKeywordBoost = searchConfig.boost().multiKeywordBoost();
        List<FileSearchResult> results = new ArrayList<>();

        for (FileKeywordEntry file : index.files) {
            MatchAccumulator acc = computeMatchingScore(file.keywords, keywordSet);
            if (acc.score > 0) {
                double finalScore = acc.score;
                if (acc.matchedCount > 1) {
                    finalScore *= multiKeywordBoost;
                }
                results.add(new FileSearchResult(file.path, finalScore,
                        List.copyOf(acc.matchedKeywords), file.extension));
            }
        }
        return results;
    }

    public PaginatedResult<SectionSearchResult> searchSections(String version, List<String> keywords,
                                                    List<String> filePaths, int limit, int offset) {
        KeywordIndex index = getOrBuildIndex(version);
        if (index == null) {
            return new PaginatedResult<>(List.of(), 0);
        }

        Set<String> keywordSet = new HashSet<>(keywords.stream()
                .map(k -> Stemmer.stem(k.toLowerCase())).toList());
        Set<String> filePathSet = (filePaths == null || filePaths.isEmpty())
                ? null : new HashSet<>(filePaths);
        double multiKeywordBoost = searchConfig.boost().multiKeywordBoost();
        List<SectionSearchResult> results = new ArrayList<>();

        for (FileKeywordEntry file : index.files) {
            if (filePathSet != null && !filePathSet.contains(file.path)) {
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
                            List.copyOf(acc.matchedKeywords), file.extension));
                }
            }
        }

        results.sort(Comparator.comparingDouble((SectionSearchResult r) -> r.score).reversed());
        return paginate(results, limit, offset);
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
                                                          int limit, int offset) {
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

            MatchAccumulator acc = computeMatchingScore(sample.keywords, keywordSet);
            if (acc.score > 0) {
                double finalScore = acc.score;
                if (acc.matchedCount > 1) {
                    finalScore *= multiKeywordBoost;
                }
                results.add(new CodeSampleSearchResult(
                        sample.filePath, sample.sectionTitle, matchedTitle, matchScore,
                        sample.language, sample.content, sample.startLine, sample.endLine, finalScore,
                        List.copyOf(acc.matchedKeywords), sample.extension));
            }
        }

        results.sort(Comparator.comparingDouble((CodeSampleSearchResult r) -> r.score).reversed());
        return paginate(results, limit, offset);
    }

    public PaginatedResult<ContentSearchResult> searchContent(String version, List<String> keywords,
                                                               List<String> filePaths,
                                                               int limit, int offset) {
        ContentIndex contentIndex = getOrLoadContentIndex(version);
        if (contentIndex == null) {
            log.warn("Content index not available for version {}, falling back to brute-force scan", version);
            return searchContentBruteForce(version, keywords, filePaths, limit, offset);
        }

        Set<String> keywordSet = new HashSet<>(keywords.stream()
                .map(String::toLowerCase).toList());
        Set<String> filePathSet = (filePaths == null || filePaths.isEmpty())
                ? null : new HashSet<>(filePaths);
        double multiKeywordBoost = searchConfig.boost().multiKeywordBoost();

        // Aggregate scores per file from the inverted index
        Map<String, Double> fileScores = new HashMap<>();
        Map<String, Set<String>> fileMatchedKeywords = new HashMap<>();
        Map<String, Integer> fileFirstMatchOffset = new HashMap<>();
        Map<String, Integer> fileTotalMatchCount = new HashMap<>();

        for (String keyword : keywordSet) {
            List<ContentOccurrence> occurrences = contentIndex.wordOccurrences.get(keyword);
            if (occurrences == null || occurrences.isEmpty()) {
                continue;
            }

            // Group occurrences by file
            Map<String, List<ContentOccurrence>> byFile = new HashMap<>();
            for (ContentOccurrence occ : occurrences) {
                if (filePathSet != null && !filePathSet.contains(occ.filePath)) {
                    continue;
                }
                byFile.computeIfAbsent(occ.filePath, k -> new ArrayList<>()).add(occ);
            }

            for (Map.Entry<String, List<ContentOccurrence>> entry : byFile.entrySet()) {
                String filePath = entry.getKey();
                int matchCount = entry.getValue().size();
                fileScores.merge(filePath, (double) matchCount, Double::sum);
                fileMatchedKeywords.computeIfAbsent(filePath, k -> new HashSet<>()).add(keyword);
                fileTotalMatchCount.merge(filePath, matchCount, Integer::sum);

                // Track earliest match offset per file
                int earliestOffset = entry.getValue().stream()
                        .mapToInt(o -> o.charOffset)
                        .min().orElse(Integer.MAX_VALUE);
                fileFirstMatchOffset.merge(filePath, earliestOffset, Math::min);
            }
        }

        List<ContentSearchResult> results = new ArrayList<>();
        for (Map.Entry<String, Double> entry : fileScores.entrySet()) {
            String filePath = entry.getKey();
            double score = entry.getValue();
            Set<String> matched = fileMatchedKeywords.get(filePath);
            int matchedCount = matched.size();
            if (matchedCount > 1) {
                score *= multiKeywordBoost;
            }

            int firstOffset = fileFirstMatchOffset.get(filePath);
            Optional<String> content = docStore.read(version, filePath);
            if (content.isEmpty()) {
                continue;
            }
            int matchLine = computeLineNumber(content.get(), firstOffset);
            String snippet = generateSnippet(content.get(), firstOffset);
            results.add(new ContentSearchResult(filePath, snippet, firstOffset, matchLine, score,
                    List.copyOf(matched), fileTotalMatchCount.get(filePath), "quarkus-core"));
        }

        results.sort(Comparator.comparingDouble((ContentSearchResult r) -> r.score).reversed());
        return paginate(results, limit, offset);
    }

    private PaginatedResult<ContentSearchResult> searchContentBruteForce(String version, List<String> keywords,
                                                                          List<String> filePaths,
                                                                          int limit, int offset) {
        List<String> files = docStore.listDocFiles(version);
        if (files.isEmpty()) {
            return new PaginatedResult<>(List.of(), 0);
        }

        Set<String> keywordSet = new HashSet<>(keywords.stream()
                .map(String::toLowerCase).toList());
        Set<String> filePathSet = (filePaths == null || filePaths.isEmpty())
                ? null : new HashSet<>(filePaths);
        double multiKeywordBoost = searchConfig.boost().multiKeywordBoost();

        List<ContentSearchResult> results = new ArrayList<>();

        for (String filePath : files) {
            if (filePathSet != null && !filePathSet.contains(filePath)) {
                continue;
            }
            Optional<String> content = docStore.read(version, filePath);
            if (content.isEmpty()) {
                continue;
            }
            String text = content.get();
            String lowerText = text.toLowerCase();

            double fileScore = 0;
            int firstMatchOffset = -1;
            int totalMatchCount = 0;
            Set<String> matchedKws = new HashSet<>();

            for (String keyword : keywordSet) {
                int idx = 0;
                int matchCount = 0;
                while ((idx = lowerText.indexOf(keyword, idx)) >= 0) {
                    matchCount++;
                    if (firstMatchOffset < 0 || idx < firstMatchOffset) {
                        firstMatchOffset = idx;
                    }
                    idx += keyword.length();
                }
                if (matchCount > 0) {
                    matchedKws.add(keyword);
                    totalMatchCount += matchCount;
                }
                fileScore += matchCount;
            }

            if (fileScore > 0 && firstMatchOffset >= 0) {
                if (matchedKws.size() > 1) {
                    fileScore *= multiKeywordBoost;
                }
                int firstMatchLine = computeLineNumber(text, firstMatchOffset);
                String snippet = generateSnippet(text, firstMatchOffset);
                results.add(new ContentSearchResult(filePath, snippet, firstMatchOffset, firstMatchLine, fileScore,
                        List.copyOf(matchedKws), totalMatchCount, "quarkus-core"));
            }
        }

        results.sort(Comparator.comparingDouble((ContentSearchResult r) -> r.score).reversed());
        return paginate(results, limit, offset);
    }

    /**
     * Invalidates the in-memory cache for a specific version.
     * Should be called after the keyword index is rebuilt (e.g., during cache refresh).
     */
    public void invalidateCache(String version) {
        indexCache.remove(version);
        codeSampleIndexCache.remove(version);
        contentIndexCache.remove(version);
    }

    private <T> PaginatedResult<T> paginate(List<T> all, int limit, int offset) {
        int total = all.size();
        if (offset >= total) {
            return new PaginatedResult<>(List.of(), total);
        }
        int end = Math.min(offset + limit, total);
        return new PaginatedResult<>(all.subList(offset, end), total);
    }

    record MatchAccumulator(double score, int matchedCount, Set<String> matchedKeywords) {}

    /**
     * Computes the total matching score for a list of indexed keywords against a set of query keywords.
     * Supports both exact matches (full score) and prefix matches (discounted by PREFIX_MATCH_MULTIPLIER).
     * Exact matches take precedence over prefix matches for the same indexed keyword.
     */
    MatchAccumulator computeMatchingScore(List<KeywordScore> indexedKeywords, Set<String> queryKeywords) {
        double prefixMultiplier = searchConfig.boost().prefixMatchMultiplier();
        double totalScore = 0;
        Set<String> matchedQueryKeywords = new HashSet<>();

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
                matchedQueryKeywords.add(bestQueryKeyword);
            }
        }

        return new MatchAccumulator(totalScore, matchedQueryKeywords.size(), matchedQueryKeywords);
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

    private ContentIndex getOrLoadContentIndex(String version) {
        if (contentIndexStore == null) {
            return null;
        }
        ContentIndex cached = contentIndexCache.get(version);
        if (cached != null) {
            return cached;
        }

        Optional<ContentIndex> index = contentIndexStore.read(version);
        if (index.isPresent()) {
            contentIndexCache.put(version, index.get());
            return index.get();
        }
        return null;
    }
}
