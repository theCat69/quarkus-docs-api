package com.fvd.api.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Generic paginated search response wrapper.
 */
@SuperBuilder
@NoArgsConstructor
@RegisterForReflection
public class DocumentSearchResponse extends PaginatedResponse<DocumentResponse> {
}
