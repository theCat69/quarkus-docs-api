package com.fvd.api.resources;

import com.fvd.api.dto.CatalogResponse;
import com.fvd.api.services.CatalogService;
import com.fvd.cache.services.CacheService;
import com.fvd.common.resources.ProblemDetail;
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

/**
 * REST endpoint for catalog information.
 */
@Path("/api/catalog")
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
@Tag(name = "Catalog", description = "Catalog of available subjects, extensions, and versions")
public class CatalogResource {

    private final CatalogService catalogService;
    private final CacheService cacheService;

    @GET
    @Operation(
            summary = "List catalog information",
            description = "Returns lists of available subjects, extensions, and versions. " +
                    "Subjects are documentation categories derived from file paths. " +
                    "Extensions include quarkus-core and quarkiverse extensions. " +
                    "Results are cached per version."
    )
    @APIResponse(
            responseCode = "200",
            description = "Catalog information returned successfully",
            content = @Content(schema = @Schema(implementation = CatalogResponse.class))
    )
    @APIResponse(
            responseCode = "400",
            description = "Invalid version parameter",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class))
    )
    public CatalogResponse getCatalog(
            @Parameter(
                    description = "Quarkus version branch or tag. Defaults to 'main' if omitted.",
                    required = false,
                    example = "main",
                    schema = @Schema(defaultValue = "main")
            )
            @QueryParam("version") String version,

            @Parameter(
                    description = "Comma-separated list of fields to include in the response. " +
                            "When omitted, all fields are returned. " +
                            "Invalid field names return 400 with the list of available fields. " +
                            "Example: 'subjects,versions'",
                    required = false,
                    example = "subjects,versions"
            )
            @QueryParam("fields") String fields) {
        version = InputValidator.resolveVersion(version);
        InputValidator.validateVersionExists(version, cacheService.listCachedVersions());
        return catalogService.getCatalog(version);
    }
}
