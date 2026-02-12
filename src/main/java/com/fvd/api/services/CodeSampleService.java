package com.fvd.api.services;

import com.fvd.api.dto.CodeSampleResult;
import com.fvd.api.dto.CodeSampleSearchResponse;
import com.fvd.docs.stores.DocStore;
import com.fvd.indexs.indexers.CodeSampleEntry;
import com.fvd.indexs.indexers.CodeSampleIndex;
import com.fvd.indexs.stores.CodeSampleIndexStore;
import com.fvd.repository.domain.MatchedKeyword;
import com.fvd.search.services.CodeSampleSearchResult;
import com.fvd.search.services.PaginatedResult;
import com.fvd.search.services.SearchService;
import com.fvd.subject.services.SubjectDeriver;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for code sample search operations.
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class CodeSampleService {

    private static final Pattern TITLE_PATTERN = Pattern.compile("^=\\s+(.+)$", Pattern.MULTILINE);

    private final SearchService searchService;
    private final CodeSampleIndexStore codeSampleIndexStore;
    private final DocStore docStore;
    private final SubjectDeriver subjectDeriver;

    /**
     * Searches code samples by keywords with optional filters.
     *
     * @param version the documentation version
     * @param keywords search keywords
     * @param language optional language filter
     * @param subject optional subject filter
     * @param extension optional extension filter
     * @param limit max results
     * @param offset pagination offset
     * @return search response with matching code samples
     */
    public CodeSampleSearchResponse searchCodeSamples(String version, List<String> keywords,
                                                      String language, String subject,
                                                      String extension, int limit, int offset) {
        // Use existing search service for keyword-based code sample search
        PaginatedResult<CodeSampleSearchResult> searchResult = searchService.searchCodeSamples(
                version, keywords, null, null, extension, limit + offset, 0);

        List<CodeSampleResult> results = new ArrayList<>();
        int skipped = 0;

        for (CodeSampleSearchResult csResult : searchResult.items()) {
            // Apply language filter
            if (language != null && !language.isBlank() && !language.equalsIgnoreCase(csResult.language)) {
                continue;
            }

            // Apply subject filter
            String derivedSubject = subjectDeriver.deriveSubject(csResult.path);
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

            // Get document title
            String docTitle = getDocumentTitle(version, csResult.path);

            List<String> matchedKws = csResult.matchedKeywords.stream()
                    .map(MatchedKeyword::keyword)
                    .toList();

            results.add(new CodeSampleResult(
                    csResult.language,
                    csResult.content,
                    csResult.sectionTitle, // context is the section title
                    csResult.path,
                    docTitle,
                    derivedSubject,
                    csResult.extension,
                    matchedKws,
                    csResult.score,
                    csResult.startLine,
                    csResult.endLine
            ));
        }

        return new CodeSampleSearchResponse(results, searchResult.total(), results.size());
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
}
