package com.fvd.api.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonFilter;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Response for batch document retrieval with partial failure support.
 */
@JsonFilter("fieldSelector")
@RegisterForReflection
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Batch document retrieval response")
public class BatchDocumentResponse {

    @Schema(description = "Successfully retrieved documents")
    public List<DocumentResponse> documents;

    @Schema(description = "Errors for paths that could not be retrieved")
    public List<BatchDocumentError> errors;

    @Schema(description = "Total number of paths requested")
    public int requestedCount;

    @Schema(description = "Number of documents successfully retrieved")
    public int retrievedCount;

    @Schema(description = "Number of paths that failed")
    public int errorCount;
}
