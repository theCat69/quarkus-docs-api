package com.fvd.api.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Paginated response for quick search endpoint.
 */
@RegisterForReflection
@NoArgsConstructor
@AllArgsConstructor
public class QuickSearchResponse {

    public List<SearchResultRef> results;
    public int totalCount;
    public int returnedCount;

}
