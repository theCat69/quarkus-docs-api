package com.fvd.api.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Request body for POST /api/search.
 */
@RegisterForReflection
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Search request body for POST /api/search")
public class SearchRequest {

    @Schema(description = "Space-separated search keywords", required = true,
            example = "security authentication oidc")
    public String keywords;

    @Schema(description = "Quarkus version branch or tag. Defaults to 'main' if omitted.",
            defaultValue = "main", example = "3.27")
    public String version;

    @Schema(description = "Subject filter", example = "security")
    public String subject;

    @Schema(description = "Extension filter", example = "quarkus-oidc")
    public String extension;

    @Schema(description = "Maximum number of results (default 20, max 100)",
            defaultValue = "20", example = "20")
    public Integer limit;

    @Schema(description = "Pagination offset (default 0)",
            defaultValue = "0", example = "0")
    public Integer offset;
}
