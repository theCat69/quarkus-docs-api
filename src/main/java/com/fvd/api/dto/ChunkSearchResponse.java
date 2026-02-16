package com.fvd.api.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

/**
 * Paginated response for chunk-based search results.
 */
@RegisterForReflection
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChunkSearchResponse {

    @Schema(description = "List of chunk results matching the search query")
    public List<ChunkResult> results;

    @Schema(description = "Total number of chunks matching the query")
    public int total;

    @Schema(description = "Maximum number of results returned in this response")
    public int limit;

    @Schema(description = "Offset into the total result set for pagination")
    public int offset;

}
