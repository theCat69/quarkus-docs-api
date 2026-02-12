package com.fvd.repository.api;

import com.fvd.repository.domain.CodeSampleMatch;
import com.fvd.repository.domain.CodeSampleSearchQuery;
import com.fvd.repository.domain.FileMatch;
import com.fvd.repository.domain.FileSearchQuery;
import com.fvd.repository.domain.SearchResult;
import com.fvd.repository.domain.SectionMatch;
import com.fvd.repository.domain.SectionSearchQuery;

/**
 * Repository interface for search operations across the documentation indexes.
 * <p>
 * Provides search functionality for files, sections, and code samples
 * based on keyword matching with support for pagination and filtering.
 * </p>
 */
public interface SearchRepository {

    /**
     * Searches for files matching the given query criteria.
     *
     * @param query the file search query parameters
     * @return a search result containing matching files and total count
     */
    SearchResult<FileMatch> searchFiles(FileSearchQuery query);

    /**
     * Searches for sections matching the given query criteria.
     *
     * @param query the section search query parameters
     * @return a search result containing matching sections and total count
     */
    SearchResult<SectionMatch> searchSections(SectionSearchQuery query);

    /**
     * Searches for code samples matching the given query criteria.
     *
     * @param query the code sample search query parameters
     * @return a search result containing matching code samples and total count
     */
    SearchResult<CodeSampleMatch> searchCodeSamples(CodeSampleSearchQuery query);
}
