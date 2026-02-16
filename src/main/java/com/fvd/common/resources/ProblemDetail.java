package com.fvd.common.resources;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.Instant;

/**
 * RFC 7807 Problem Details for HTTP APIs.
 * See: <a href="https://www.rfc-editor.org/rfc/rfc7807">...</a>
 */
@RegisterForReflection
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "RFC 7807 Problem Details error response")
public class ProblemDetail {

    @Schema(description = "A URI reference identifying the problem type (about:blank for HTTP status-based errors)",
            examples = {"about:blank"})
    public String type;

    @Schema(description = "A short, human-readable summary of the problem type", examples = {"Not Found"})
    public String title;

    @Schema(description = "The HTTP status code", examples = {"404"})
    public int status;

    @Schema(description = "A human-readable explanation specific to this occurrence of the problem",
            examples = {"Document not found: _guides/nonexistent.adoc"})
    public String detail;

    @Schema(description = "A URI reference that identifies the specific occurrence of the problem",
            examples = {"/api/documents"})
    public String instance;

    @Schema(description = "ISO-8601 timestamp of when the error occurred",
            examples = {"2024-01-15T10:30:00Z"})
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
        return new ProblemDetail("about:blank", title, status, detail, instance, Instant.now().toString());
    }
}
