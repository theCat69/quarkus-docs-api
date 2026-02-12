package com.fvd.api.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Paginated response for quick search endpoint.
 */
@SuperBuilder
@NoArgsConstructor
@RegisterForReflection
public class QuickSearchResponse extends PaginatedResponse<SearchResultRef> {
}
