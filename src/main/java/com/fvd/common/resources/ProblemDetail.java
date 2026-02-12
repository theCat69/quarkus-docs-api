package com.fvd.common.resources;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.Instant;

/**
 * RFC 7807 Problem Details for HTTP APIs.
 * See: https://www.rfc-editor.org/rfc/rfc7807
 */
@RegisterForReflection
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "RFC 7807 Problem Details error response")
public class ProblemDetail {

    @Schema(description = "A short, human-readable summary of the problem type", example = "Not Found")
    public String title;

    @Schema(description = "The HTTP status code", example = "404")
    public int status;

    @Schema(description = "A human-readable explanation specific to this occurrence of the problem",
            example = "Document not found: _guides/nonexistent.adoc")
    public String detail;

    @Schema(description = "A URI reference that identifies the specific occurrence of the problem",
            example = "/api/documents")
    public String instance;

    @Schema(description = "ISO-8601 timestamp of when the error occurred",
            example = "2024-01-15T10:30:00Z")
    public String timestamp;

    /**
     * Factory method to create a ProblemDetail with current timestamp.
     *
     * @param status   HTTP status code
     * @param title    Short summary of the problem type
     * @param detail   Human-readable explanation
     * @param instance URI reference identifying the occurrence
     * @return new ProblemDetail instance
     */
    public static ProblemDetail of(int status, String title, String detail, String instance) {
        return new ProblemDetail(title, status, detail, instance, Instant.now().toString());
    }
}
