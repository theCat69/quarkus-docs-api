package com.fvd.search.services;

import com.fvd.cache.services.CacheService;
import com.fvd.common.matchers.FuzzyMatcher;
import com.fvd.docs.exceptions.DocNotFoundException;
import com.fvd.docs.parser.DocParser;
import com.fvd.docs.stores.DocStore;
import com.fvd.github.services.ZipDownloadService;
import com.fvd.indexs.indexers.*;
import com.fvd.indexs.stores.CodeSampleIndexStore;
import com.fvd.indexs.stores.KeywordIndexStore;
import com.fvd.search.SearchConfig;
import jakarta.annotation.Nonnull;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class SearchService {

    private final KeywordIndexStore keywordIndexStore;
    private final CodeSampleIndexStore codeSampleIndexStore;
    private final ZipDownloadService zipDownloadService;
    private final KeywordIndexer keywordIndexer;
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
                                                         int limit, int offset) {
        KeywordIndex index = getOrBuildIndex(version);
        if (index == null) {
            return new PaginatedResult<>(List.of(), 0);
        }

        Set<String> keywordSet = new HashSet<>(keywords.stream()
                .map(String::toLowerCase).toList());
        Map<String, Double> scores = getScores(index, keywordSet);

        List<FileSearchResult> all = scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .map(e -> new FileSearchResult(e.getKey(), e.getValue()))
                .toList();

        return paginate(all, limit, offset);
    }

    @Nonnull
    private Map<String, Double> getScores(KeywordIndex index, Set<String> keywordSet) {
        double multiKeywordBoost = searchConfig.boost().multiKeywordBoost();
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
                    score *= multiKeywordBoost;
                }
                scores.put(file.path, score);
            }
        }
        return scores;
    }

    public PaginatedResult<SectionSearchResult> searchSections(String version, List<String> keywords,
                                                    List<String> filePaths, int limit, int offset) {
        KeywordIndex index = getOrBuildIndex(version);
        if (index == null) {
            return new PaginatedResult<>(List.of(), 0);
        }

        Set<String> keywordSet = new HashSet<>(keywords.stream()
                .map(String::toLowerCase).toList());
        Set<String> filePathSet = (filePaths == null || filePaths.isEmpty())
                ? null : new HashSet<>(filePaths);
        List<SectionSearchResult> results = new ArrayList<>();

        for (FileKeywordEntry file : index.files) {
            if (filePathSet != null && !filePathSet.contains(file.path)) {
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
                .map(String::toLowerCase).toList());
        double multiKeywordBoost = searchConfig.boost().multiKeywordBoost();

        List<CodeSampleSearchResult> results = new ArrayList<>();
        for (CodeSampleEntry sample : index.samples) {
            if (filePath != null && !filePath.isBlank() && !sample.filePath.equals(filePath)) {
                continue;
            }
            if (sectionTitle != null && !sectionTitle.isBlank()
                    && !sample.sectionTitle.equalsIgnoreCase(sectionTitle)) {
                continue;
            }

            double score = 0;
            int matchedCount = 0;
            for (KeywordScore ks : sample.keywords) {
                if (keywordSet.contains(ks.word)) {
                    score += ks.score;
                    matchedCount++;
                }
            }
            if (score > 0) {
                if (matchedCount > 1) {
                    score *= multiKeywordBoost;
                }
                results.add(new CodeSampleSearchResult(
                        sample.filePath, sample.sectionTitle, sample.language,
                        sample.content, sample.startLine, sample.endLine, score));
            }
        }

        results.sort(Comparator.comparingDouble((CodeSampleSearchResult r) -> r.score).reversed());
        return paginate(results, limit, offset);
    }

    public PaginatedResult<ContentSearchResult> searchContent(String version, List<String> keywords,
                                                               int limit, int offset) {
        List<String> files = docStore.listDocFiles(version);
        if (files.isEmpty()) {
            return new PaginatedResult<>(List.of(), 0);
        }

        Set<String> keywordSet = new HashSet<>(keywords.stream()
                .map(String::toLowerCase).toList());
        double multiKeywordBoost = searchConfig.boost().multiKeywordBoost();

        List<ContentSearchResult> results = new ArrayList<>();

        for (String filePath : files) {
            Optional<String> content = docStore.read(version, filePath);
            if (content.isEmpty()) {
                continue;
            }
            String text = content.get();
            String lowerText = text.toLowerCase();
            String[] lines = text.split("\n", -1);

            double fileScore = 0;
            int firstMatchOffset = -1;
            int firstMatchLine = -1;

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
                fileScore += matchCount;
            }

            if (fileScore > 0 && firstMatchOffset >= 0) {
                if (keywordSet.size() > 1) {
                    fileScore *= multiKeywordBoost;
                }
                firstMatchLine = computeLineNumber(text, firstMatchOffset);
                String snippet = generateSnippet(text, firstMatchOffset);
                results.add(new ContentSearchResult(filePath, snippet, firstMatchOffset, firstMatchLine, fileScore));
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
    }

    private <T> PaginatedResult<T> paginate(List<T> all, int limit, int offset) {
        int total = all.size();
        if (offset >= total) {
            return new PaginatedResult<>(List.of(), total);
        }
        int end = Math.min(offset + limit, total);
        return new PaginatedResult<>(all.subList(offset, end), total);
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

        // Check if index exists in SQLite without loading it fully
        if (!keywordIndexStore.exists(version)) {
            buildIndex(version);
        }

        // Load from SQLite and cache
        Optional<KeywordIndex> index = keywordIndexStore.read(version);
        if (index.isPresent()) {
            indexCache.put(version, index.get());
            return index.get();
        }
        return null;
    }

    private void buildIndex(String version) {
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
