package com.fvd.api.resources;

import com.fvd.api.dto.CodeSampleSearchResponse;
import com.fvd.api.dto.SearchParams;
import com.fvd.api.services.CodeSampleService;
import com.fvd.common.resources.ProblemDetail;
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

/**
 * REST endpoint for code sample search.
 */
@Path("/api/code-samples")
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
@Tag(name = "Code Samples", description = "Search code examples by keywords")
public class CodeSampleResource {

    private final CodeSampleService codeSampleService;

    @GET
    @Operation(
            summary = "Search code samples by keywords",
            description = "Searches for code examples matching the given keywords. " +
                    "Returns code samples with full content, language, context, and relevance scores. " +
                    "Results are sorted by score descending."
    )
    @APIResponse(
            responseCode = "200",
            description = "Code samples returned successfully",
            content = @Content(schema = @Schema(implementation = CodeSampleSearchResponse.class))
    )
    @APIResponse(
            responseCode = "400",
            description = "Invalid input parameters or keywords not provided",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class))
    )
    public CodeSampleSearchResponse searchCodeSamples(
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
                    example = "rest endpoint"
            )
            @QueryParam("keywords") String keywords,

            @Parameter(
                    description = "Programming language filter (e.g., 'java', 'properties', 'yaml')",
                    required = false,
                    example = "java"
            )
            @QueryParam("language") String language,

            @Parameter(
                    description = "Subject filter (e.g., 'security', 'rest-apis')",
                    required = false,
                    example = "rest-apis"
            )
            @QueryParam("subject") String subject,

            @Parameter(
                    description = "Extension filter (e.g., 'quarkus-core', 'quarkus-resteasy-reactive')",
                    required = false,
                    example = "quarkus-resteasy-reactive"
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

        SearchParams params = SearchParams.fromRaw(version, keywords, subject, extension, limit, offset);

        return codeSampleService.searchCodeSamples(params.version(), params.keywords(), language,
                params.subject(), params.extension(), params.limit(), params.offset());
    }
}
