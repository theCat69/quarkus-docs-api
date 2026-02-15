package com.fvd.api.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

/**
 * Request body for batch document retrieval.
 */
@RegisterForReflection
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request body for batch document retrieval")
public class BatchDocumentRequest {

    @Schema(description = "List of document paths to retrieve (max 10)",
            required = true,
            example = "[\"security-overview.adoc\", \"security-oidc-code-flow.adoc\"]")
    public List<String> paths;

    @Schema(description = "Quarkus version branch or tag. Defaults to 'main' if omitted.",
            defaultValue = "main",
            example = "main")
    public String version;

    @Schema(description = "When true, returns only metadata (title, description, path, subject, " +
            "extension) without full sections and codeBlocks.",
            defaultValue = "false")
    public Boolean brief;
}
