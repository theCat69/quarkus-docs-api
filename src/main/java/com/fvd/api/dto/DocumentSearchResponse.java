package com.fvd.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Search response wrapper for document keyword search results.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@SuperBuilder
@NoArgsConstructor
@RegisterForReflection
public class DocumentSearchResponse extends PaginatedResponse<DocumentResponse> {

    @Schema(description = "Warning message when results are limited due to performance constraints")
    public String warning;
}
