package com.fvd.api.resources;

import java.util.List;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
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

import com.fvd.api.dto.ChunkResult;
import com.fvd.api.dto.ChunkSearchResponse;
import com.fvd.api.dto.SearchRequest;
import com.fvd.cache.services.CacheService;
import com.fvd.common.SearchConstants;
import com.fvd.common.exceptions.InvalidInputException;
import com.fvd.common.resources.ProblemDetail;
import com.fvd.common.validators.InputValidator;
import com.fvd.search.services.DocChunkSearchService;
import com.fvd.search.services.PaginatedChunkResult;

/**
 * REST endpoint for chunk-based documentation search.
 */
@Path("/api/search")
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
@Tag(name = "Search", description = "Chunk-based documentation search returning scored results with metadata")
public class SearchResource {

    private final DocChunkSearchService docChunkSearchService;
    private final CacheService cacheService;

    @GET
    @Operation(
            summary = "Search documentation chunks",
            description = "Searches indexed documentation chunks by query string. " +
                    "Returns scored results with title, section, summary, and metadata. " +
                    "Supports filtering by version and extension. " +
                    "Results are sorted by relevance score descending."
    )
    @APIResponse(
            responseCode = "200",
            description = "Search results returned successfully",
            content = @Content(schema = @Schema(implementation = ChunkSearchResponse.class))
    )
    @APIResponse(
            responseCode = "400",
            description = "Invalid input parameters or query not provided",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class))
    )
    public ChunkSearchResponse search(
            @Parameter(
                    description = "Search query string. Required.",
                    required = true,
                    example = "reactive rest endpoint"
            )
            @QueryParam("q") String q,

            @Parameter(
                    description = "Quarkus version branch or tag. Defaults to 'main' if omitted.",
                    required = false,
                    example = "main",
                    schema = @Schema(defaultValue = "main")
            )
            @QueryParam("version") String version,

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

        InputValidator.requireNonEmpty(q, "q");
        String resolvedVersion = InputValidator.resolveVersion(version);
        InputValidator.validateVersionExists(resolvedVersion, cacheService.listCachedVersions());
        int validLimit = InputValidator.validateLimit(limit, SearchConstants.DEFAULT_LIMIT, SearchConstants.MAX_LIMIT);
        int validOffset = InputValidator.validateOffset(offset);
        String normalizedExtension = (extension == null || extension.isBlank()) ? null : extension.trim();

        PaginatedChunkResult result = docChunkSearchService.search(q, resolvedVersion, normalizedExtension,
                validLimit, validOffset);
        return toChunkSearchResponse(result);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(
            summary = "Search documentation chunks (POST)",
            description = "Same as GET /api/search but accepts parameters as a JSON body. " +
                    "Useful for complex queries or when URL length limits are a concern. " +
                    "Returns the same ChunkSearchResponse."
    )
    @APIResponse(
            responseCode = "200",
            description = "Search results returned successfully",
            content = @Content(schema = @Schema(implementation = ChunkSearchResponse.class))
    )
    @APIResponse(
            responseCode = "400",
            description = "Invalid input parameters or query not provided",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class))
    )
    public ChunkSearchResponse searchPost(SearchRequest request) {
        if (request == null) {
            throw new InvalidInputException("Request body is required");
        }

        InputValidator.requireNonEmpty(request.q, "q");
        String resolvedVersion = InputValidator.resolveVersion(request.version);
        InputValidator.validateVersionExists(resolvedVersion, cacheService.listCachedVersions());
        int validLimit = InputValidator.validateLimit(request.limit, SearchConstants.DEFAULT_LIMIT,
                SearchConstants.MAX_LIMIT);
        int validOffset = InputValidator.validateOffset(request.offset);
        String normalizedExtension = (request.extension == null || request.extension.isBlank())
                ? null : request.extension.trim();

        PaginatedChunkResult result = docChunkSearchService.search(request.q, resolvedVersion,
                normalizedExtension, validLimit, validOffset);
        return toChunkSearchResponse(result);
    }

    private ChunkSearchResponse toChunkSearchResponse(PaginatedChunkResult result) {
        List<ChunkResult> chunkResults = result.results().stream()
                .map(r -> ChunkResult.builder()
                        .id(r.id())
                        .page(r.page())
                        .title(r.title())
                        .section(r.section())
                        .summary(r.summary())
                        .extensions(r.extensions())
                        .topics(r.topics())
                        .score(r.score())
                        .url(r.url())
                        .build())
                .toList();
        return ChunkSearchResponse.builder()
                .results(chunkResults)
                .total(result.total())
                .limit(result.limit())
                .offset(result.offset())
                .build();
    }
}
