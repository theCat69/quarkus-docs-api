package com.fvd.api.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Paginated response wrapper for related document results.
 */
@SuperBuilder
@NoArgsConstructor
@RegisterForReflection
public class RelatedDocumentResponse extends PaginatedResponse<RelatedDocumentRef> {
}
