package com.fvd.search.resources;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
public class SearchResponse<T> {

    public List<T> results;
    public int total;
    public int limit;
    public int offset;

    /**
     * Convenience constructor for non-paginated responses (e.g., listVersions).
     */
    public SearchResponse(List<T> results) {
        this.results = results;
        this.total = results != null ? results.size() : 0;
        this.limit = this.total;
        this.offset = 0;
    }

}
