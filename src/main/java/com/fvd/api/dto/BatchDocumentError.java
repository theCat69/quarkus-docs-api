package com.fvd.api.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Error detail for a single document in a batch request.
 */
@RegisterForReflection
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Error detail for a single path in a batch request")
public class BatchDocumentError {

    @Schema(description = "The document path that failed", examples = {"nonexistent.adoc"})
    public String path;

    @Schema(description = "Error reason", examples = {"Document not found"})
    public String reason;
}
