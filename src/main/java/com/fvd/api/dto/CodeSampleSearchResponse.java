package com.fvd.api.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Paginated response for code sample search.
 */
@SuperBuilder
@NoArgsConstructor
@RegisterForReflection
public class CodeSampleSearchResponse extends PaginatedResponse<CodeSampleResult> {
}
