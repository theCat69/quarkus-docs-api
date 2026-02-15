package com.fvd.api.resources;

import com.fvd.api.dto.MetaResponse;
import com.fvd.api.services.MetaService;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.eclipse.microprofile.openapi.annotations.Operation;

/**
 * REST endpoint for API meta/capabilities self-discovery.
 * If you modify parameters on any endpoint, update MetaService accordingly.
 */
@Path("/api/meta")
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
@Tag(name = "Meta", description = "API capabilities and self-discovery for AI agents")
public class MetaResource {

    private final MetaService metaService;

    @GET
    @Operation(
            summary = "API capabilities and self-discovery",
            description = "Returns a machine-readable description of all API endpoints, " +
                    "parameters, constraints, search syntax, and available filter values. " +
                    "Designed for AI agents to self-discover API capabilities on first connection."
    )
    @APIResponse(
            responseCode = "200",
            description = "API capabilities returned successfully",
            content = @Content(schema = @Schema(implementation = MetaResponse.class))
    )
    public Response getMeta() {
        MetaResponse meta = metaService.getCapabilities();
        return Response.ok(meta)
                .header("Cache-Control", "public, max-age=3600")
                .build();
    }
}
