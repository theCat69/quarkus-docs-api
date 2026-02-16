package com.fvd.api.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

/**
 * Base paginated response DTO for all search endpoints.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@RegisterForReflection
public class PaginatedResponse<T> {

    protected List<T> results;
    protected int totalCount;
    protected int returnedCount;

    @Schema(description = "Offset used for this page of results (0-indexed)", examples = {"0"})
    protected int offset;

    @Schema(description = "Maximum number of results requested for this page", examples = {"20"})
    protected int limit;

    @Schema(description = "True if more results exist beyond this page. " +
            "When true, use offset + limit as the offset for the next page request.",
            examples = {"true"})
    protected boolean hasMore;

    public static <T> PaginatedResponse<T> of(List<T> results, int total, int offset, int limit) {
        return PaginatedResponse.<T>builder()
                .results(results)
                .totalCount(total)
                .returnedCount(results.size())
                .offset(offset)
                .limit(limit)
                .hasMore((offset + results.size()) < total)
                .build();
    }

    public static <T> PaginatedResponse<T> of(List<T> results, int total) {
        return of(results, total, 0, results.size());
    }
}
