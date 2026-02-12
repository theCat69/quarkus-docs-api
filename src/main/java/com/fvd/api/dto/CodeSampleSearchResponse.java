package com.fvd.api.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Paginated response for code sample search.
 */
@RegisterForReflection
@NoArgsConstructor
@AllArgsConstructor
public class CodeSampleSearchResponse {

    public List<CodeSampleResult> results;
    public int totalCount;
    public int returnedCount;

}
