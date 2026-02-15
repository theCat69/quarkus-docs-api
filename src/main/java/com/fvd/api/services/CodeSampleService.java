package com.fvd.api.services;

import com.fvd.api.dto.CodeSampleResult;
import com.fvd.api.dto.CodeSampleSearchResponse;
import com.fvd.common.utils.DocumentTitleExtractor;
import com.fvd.docs.stores.DocStore;
import com.fvd.indexs.indexers.CodeSampleIndex;
import com.fvd.indexs.stores.CodeSampleIndexStore;
import com.fvd.search.services.MatchedKeyword;
import com.fvd.search.services.CodeSampleSearchResult;
import com.fvd.search.services.PaginatedResult;
import com.fvd.search.services.SearchService;
import com.fvd.subject.services.SubjectDeriver;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for code sample search operations.
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class CodeSampleService {

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
                version, keywords, null, null, extension, subject, language, limit, offset);

        List<CodeSampleResult> results = new ArrayList<>();

        for (CodeSampleSearchResult csResult : searchResult.items()) {
            String derivedSubject = subjectDeriver.deriveSubject(csResult.path);

            // Get document title
            String docTitle = docStore.read(version, csResult.path)
                    .map(DocumentTitleExtractor::extractTitle)
                    .orElse("");

            List<String> matchedKws = csResult.matchedKeywords.stream()
                    .map(MatchedKeyword::originalKeyword)
                    .toList();

            results.add(new CodeSampleResult(
                    csResult.language,
                    csResult.content,
                    csResult.sectionTitle,
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

        return CodeSampleSearchResponse.builder()
                .results(results)
                .totalCount(searchResult.total())
                .returnedCount(results.size())
                .build();
    }
}
