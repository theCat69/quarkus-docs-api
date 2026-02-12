package com.fvd.api.services;

import com.fvd.api.dto.QuickSearchResponse;
import com.fvd.api.dto.SearchResultRef;
import com.fvd.docs.stores.DocStore;
import com.fvd.repository.domain.MatchedKeyword;
import com.fvd.search.services.FileSearchResult;
import com.fvd.search.services.PaginatedResult;
import com.fvd.search.services.SearchService;
import com.fvd.subject.services.SubjectDeriver;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for quick discovery search operations.
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class QuickSearchService {

    private static final Pattern TITLE_PATTERN = Pattern.compile("^=\\s+(.+)$", Pattern.MULTILINE);
    private static final int SNIPPET_CONTEXT_SIZE = 80;

    private final SearchService searchService;
    private final DocStore docStore;
    private final SubjectDeriver subjectDeriver;

    /**
     * Performs a quick discovery search returning lightweight references.
     *
     * @param version the documentation version
     * @param keywords search keywords
     * @param subject optional subject filter
     * @param extension optional extension filter
     * @param limit max results
     * @param offset pagination offset
     * @return search response with matching references
     */
    public QuickSearchResponse search(String version, List<String> keywords,
                                      String subject, String extension,
                                      int limit, int offset) {
        // Use existing search service for keyword matching
        PaginatedResult<FileSearchResult> searchResult = searchService.searchFiles(
                version, keywords, extension, limit + offset, 0);

        List<SearchResultRef> results = new ArrayList<>();
        int skipped = 0;
        Set<String> keywordSet = Set.copyOf(keywords);

        for (FileSearchResult fileResult : searchResult.items()) {
            // Apply subject filter if specified
            String derivedSubject = subjectDeriver.deriveSubject(fileResult.path);
            if (subject != null && !subject.isBlank() && !subject.equals(derivedSubject)) {
                continue;
            }

            // Handle pagination
            if (skipped < offset) {
                skipped++;
                continue;
            }

            if (results.size() >= limit) {
                break;
            }

            // Get document info
            String title = getDocumentTitle(version, fileResult.path);
            String snippet = generateSnippet(version, fileResult.path, keywordSet);

            List<String> matchedKws = fileResult.matchedKeywords.stream()
                    .map(MatchedKeyword::keyword)
                    .toList();

            results.add(new SearchResultRef(
                    fileResult.path,
                    title,
                    derivedSubject,
                    fileResult.extension,
                    fileResult.score,
                    matchedKws,
                    snippet
            ));
        }

        return new QuickSearchResponse(results, searchResult.total(), results.size());
    }

    private String getDocumentTitle(String version, String path) {
        Optional<String> contentOpt = docStore.read(version, path);
        if (contentOpt.isEmpty()) {
            return "";
        }

        Matcher matcher = TITLE_PATTERN.matcher(contentOpt.get());
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return "";
    }

    private String generateSnippet(String version, String path, Set<String> keywords) {
        Optional<String> contentOpt = docStore.read(version, path);
        if (contentOpt.isEmpty()) {
            return "";
        }

        String content = contentOpt.get();
        String lowerContent = content.toLowerCase();

        // Find first keyword occurrence
        int bestOffset = -1;
        for (String keyword : keywords) {
            int idx = lowerContent.indexOf(keyword.toLowerCase());
            if (idx >= 0 && (bestOffset < 0 || idx < bestOffset)) {
                bestOffset = idx;
            }
        }

        if (bestOffset >= 0) {
            int start = Math.max(0, bestOffset - SNIPPET_CONTEXT_SIZE);
            int end = Math.min(content.length(), bestOffset + SNIPPET_CONTEXT_SIZE);
            String snippet = content.substring(start, end).replaceAll("\\s+", " ").trim();
            if (start > 0) {
                snippet = "..." + snippet;
            }
            if (end < content.length()) {
                snippet = snippet + "...";
            }
            return snippet;
        }

        // Fall back to first 150 chars
        int len = Math.min(150, content.length());
        String snippet = content.substring(0, len).replaceAll("\\s+", " ").trim();
        if (content.length() > 150) {
            snippet = snippet + "...";
        }
        return snippet;
    }
}
