# Pattern: JAX-RS Resource (thin router with OpenAPI annotations)
# Demonstrates: @Path, @RequiredArgsConstructor, @Tag, @Operation, @APIResponse, @Parameter,
# @Schema, @QueryParam, and delegation to service with validation.

```java
package com.fvd.api.resources;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Consumes;
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

import com.fvd.api.dto.ChunkSearchResponse;
import com.fvd.api.dto.SearchParams;
import com.fvd.api.dto.SearchRequest;
import com.fvd.cache.services.CacheService;
import com.fvd.common.exceptions.InvalidInputException;
import com.fvd.common.resources.ProblemDetail;
import com.fvd.common.validators.InputValidator;
import com.fvd.search.services.DocChunkSearchService;
import com.fvd.search.services.PaginatedChunkResult;

// @RequiredArgsConstructor generates constructor injection for all final fields
@Path("/api/search")
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor                         // CDI constructor injection (no @Inject on fields)
@Tag(name = "Search", description = "Chunk-based documentation search")
public class SearchResource {

    // Final fields = constructor-injected CDI beans
    private final DocChunkSearchService docChunkSearchService;
    private final CacheService cacheService;

    @GET
    @Operation(
            summary = "Search documentation chunks",
            description = "Searches indexed chunks by query. Returns scored results sorted by relevance."
    )
    @APIResponse(                                // One @APIResponse per HTTP status
            responseCode = "200",
            description = "Search results returned successfully",
            content = @Content(schema = @Schema(implementation = ChunkSearchResponse.class))
    )
    @APIResponse(
            responseCode = "400",
            description = "Invalid input parameters",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class))
    )
    public ChunkSearchResponse search(
            // Every query param gets @Parameter with description, required, example, and schema
            @Parameter(description = "Search query string. Required.",
                    required = true, example = "reactive rest endpoint")
            @QueryParam("q") String q,

            @Parameter(description = "Quarkus version branch or tag. Defaults to 'main' if omitted.",
                    required = false, example = "main",
                    schema = @Schema(defaultValue = "main"))  // <-- always document default
            @QueryParam("version") String version,

            @Parameter(description = "Maximum results to return (default 20, max 100)",
                    required = false, example = "20")
            @QueryParam("limit") Integer limit,

            @Parameter(description = "Results to skip for pagination (default 0)",
                    required = false, example = "0")
            @QueryParam("offset") Integer offset) {

        // Thin router: validate → delegate → return. No business logic here.
        SearchParams params = SearchParams.fromRaw(version, q, null, limit, offset);
        InputValidator.validateVersionExists(params.version(), cacheService.listCachedVersions());
        PaginatedChunkResult result = docChunkSearchService.search(
                params.q(), params.version(), null, params.limit(), params.offset());
        return toChunkSearchResponse(result);
    }

    // Private mapping method is acceptable in a resource class
    private ChunkSearchResponse toChunkSearchResponse(PaginatedChunkResult result) {
        // ... map result fields to DTO
        return ChunkSearchResponse.builder()
                .results(/* mapped list */)
                .total(result.total())
                .limit(result.limit())
                .offset(result.offset())
                .build();
    }
}
```
