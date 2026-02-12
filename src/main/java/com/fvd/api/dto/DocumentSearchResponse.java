package com.fvd.api.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Generic paginated search response wrapper.
 */
@RegisterForReflection
@NoArgsConstructor
@AllArgsConstructor
public class DocumentSearchResponse {

    public List<DocumentResponse> results;
    public int totalCount;
    public int returnedCount;

}
