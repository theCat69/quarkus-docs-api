package com.fvd.api.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Search response wrapper for document keyword search results.
 */
@SuperBuilder
@NoArgsConstructor
@RegisterForReflection
public class DocumentSearchResponse extends PaginatedResponse<DocumentResponse> {

    @Schema(description = "Warning message when results are limited due to performance constraints")
    public String warning;
}
