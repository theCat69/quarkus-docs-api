package com.fvd.repository.domain;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

/**
 * Query parameters for code sample search.
 *
 * @param version the documentation version to search
 * @param keywords the list of stemmed keywords to match
 * @param filePath optional file path filter (null for all files)
 * @param sectionTitle optional section title filter for fuzzy matching (null for all sections)
 * @param extension optional extension filter (null for all extensions)
 * @param limit maximum number of results to return
 * @param offset number of results to skip (for pagination)
 */
@RegisterForReflection
public record CodeSampleSearchQuery(
        String version,
        List<String> keywords,
        String filePath,
        String sectionTitle,
        String extension,
        int limit,
        int offset
) {
    /**
     * Creates a query with minimal parameters.
     */
    public CodeSampleSearchQuery(String version, List<String> keywords, int limit, int offset) {
        this(version, keywords, null, null, null, limit, offset);
    }
}
