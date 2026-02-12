package com.fvd.api.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

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

    public static <T> PaginatedResponse<T> of(List<T> results, int total) {
        return PaginatedResponse.<T>builder()
                .results(results)
                .totalCount(total)
                .returnedCount(results.size())
                .build();
    }
}
