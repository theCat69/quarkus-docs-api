package com.fvd.api.resources;

import com.fvd.api.dto.QuickSearchResponse;
import com.fvd.api.services.QuickSearchService;
import com.fvd.common.resources.ErrorResponse;
import com.fvd.common.validators.InputValidator;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

/**
 * REST endpoint for quick discovery search.
 */
@Path("/api/search")
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
@Tag(name = "Search", description = "Quick discovery search returning lightweight references")
public class SearchResource {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final QuickSearchService quickSearchService;

    @GET
    @Operation(
            summary = "Quick discovery search",
            description = "Returns lightweight document references without full content. " +
                    "Useful for quick discovery and navigation. " +
                    "Includes path, title, score, matched keywords, and a contextual snippet. " +
                    "Results are sorted by score descending."
    )
    @APIResponse(
            responseCode = "200",
            description = "Search results returned successfully",
            content = @Content(schema = @Schema(implementation = QuickSearchResponse.class))
    )
    @APIResponse(
            responseCode = "400",
            description = "Invalid input parameters or keywords not provided",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    )
    public QuickSearchResponse search(
            @Parameter(
                    description = "Quarkus version branch or tag. Defaults to 'main' if omitted.",
                    required = false,
                    example = "3.27",
                    schema = @Schema(defaultValue = "main")
            )
            @QueryParam("version") String version,

            @Parameter(
                    description = "Space-separated search keywords. Required.",
                    required = true,
                    example = "security authentication"
            )
            @QueryParam("keywords") String keywords,

            @Parameter(
                    description = "Subject filter (e.g., 'security', 'rest-apis')",
                    required = false,
                    example = "security"
            )
            @QueryParam("subject") String subject,

            @Parameter(
                    description = "Extension filter (e.g., 'quarkus-core', 'quarkus-resteasy-reactive')",
                    required = false,
                    example = "quarkus-core"
            )
            @QueryParam("extension") String extension,

            @Parameter(
                    description = "Maximum number of results to return (default 20, max 100)",
                    required = false,
                    example = "20"
            )
            @QueryParam("limit") Integer limit,

            @Parameter(
                    description = "Number of results to skip for pagination (default 0)",
                    required = false,
                    example = "0"
            )
            @QueryParam("offset") Integer offset) {

        version = InputValidator.resolveVersion(version);
        List<String> keywordList = InputValidator.parseKeywords(keywords);
        int validLimit = InputValidator.validateLimit(limit, DEFAULT_LIMIT, MAX_LIMIT);
        int validOffset = InputValidator.validateOffset(offset);

        return quickSearchService.search(version, keywordList, subject, extension, validLimit, validOffset);
    }
}
