package com.fvd.repository.domain;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

/**
 * Query parameters for file-level search.
 *
 * @param version the documentation version to search
 * @param keywords the list of stemmed keywords to match
 * @param extension optional extension filter (null for all extensions)
 * @param limit maximum number of results to return
 * @param offset number of results to skip (for pagination)
 */
@RegisterForReflection
public record FileSearchQuery(
        String version,
        List<String> keywords,
        String extension,
        int limit,
        int offset
) {
    /**
     * Creates a query without extension filter.
     */
    public FileSearchQuery(String version, List<String> keywords, int limit, int offset) {
        this(version, keywords, null, limit, offset);
    }
}
