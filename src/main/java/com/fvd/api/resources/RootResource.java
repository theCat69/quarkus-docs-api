package com.fvd.api.resources;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.Map;

/**
 * API entry point. Returns a welcome message with links to documentation endpoints.
 */
@Path("/")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Root", description = "API entry point")
public class RootResource {

    @GET
    @Operation(
            summary = "API entry point",
            description = "Returns a welcome message with links to API documentation endpoints. " +
                    "Start here to discover the API."
    )
    public Map<String, String> root() {
        return Map.of(
                "message", "Quarkus Docs API",
                "documentation", "/api/meta",
                "openapi", "/q/openapi"
        );
    }
}
