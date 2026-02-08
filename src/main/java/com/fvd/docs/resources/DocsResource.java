package com.fvd.docs.resources;

import com.fvd.common.resources.ErrorResponse;
import com.fvd.common.validators.InputValidator;
import com.fvd.docs.services.DocService;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;

@Path("/api/doc")
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class DocsResource {

    private final DocService docService;

    @GET
    @Operation(
            summary = "Get document content",
            description = "Retrieves the raw content of a Quarkus documentation file for a specific version. Returns cached content or fetches from GitHub if not cached."
    )
    @APIResponse(
            responseCode = "200",
            description = "Document content returned successfully",
            content = @Content(schema = @Schema(implementation = DocResponse.class))
    )
    @APIResponse(
            responseCode = "400",
            description = "Invalid input parameters (missing or malformed version/path)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    )
    @APIResponse(
            responseCode = "404",
            description = "Document not found for the given version and path",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    )
    @APIResponse(
            responseCode = "502",
            description = "Upstream GitHub API error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    )
    public DocResponse getDoc(@QueryParam("version") String version,
                              @QueryParam("path") String path) {
        InputValidator.validateVersion(version);
        InputValidator.validatePath(path);
        String content = docService.getOrFetchDoc(version, path);
        return new DocResponse(path, content, "asciidoc");
    }

}
