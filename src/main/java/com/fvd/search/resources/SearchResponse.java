package com.fvd.search.resources;

import lombok.NoArgsConstructor;

import java.util.List;

@NoArgsConstructor
public class SearchResponse<T> {

    public List<T> results;
    public int total;
    public int limit;
    public int offset;
    public List<String> queriedKeywords;
    public long searchTimeMs;

    /**
     * Convenience constructor for non-paginated responses (e.g., listVersions).
     */
    public SearchResponse(List<T> results) {
        this.results = results;
        this.total = results != null ? results.size() : 0;
        this.limit = this.total;
        this.offset = 0;
    }

    /**
     * Paginated response without metadata (backward compat).
     */
    public SearchResponse(List<T> results, int total, int limit, int offset) {
        this.results = results;
        this.total = total;
        this.limit = limit;
        this.offset = offset;
    }

    /**
     * Full constructor with query metadata.
     */
    public SearchResponse(List<T> results, int total, int limit, int offset,
                          List<String> queriedKeywords, long searchTimeMs) {
        this.results = results;
        this.total = total;
        this.limit = limit;
        this.offset = offset;
        this.queriedKeywords = queriedKeywords;
        this.searchTimeMs = searchTimeMs;
    }

}
