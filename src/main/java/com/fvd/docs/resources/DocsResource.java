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
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;

@Path("/api/doc")
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class DocsResource {

    private final DocService docService;

    @GET
    @Operation(
            summary = "Get document content",
            description = "Retrieves the raw content of a Quarkus documentation file for a specific version. "
                    + "Returns cached content or fetches from GitHub if not cached. "
                    + "Sources docs from the quarkusio.github.io website repository. "
                    + "For quarkiverse extensions (version 'main' only), use the quarkiverse path pattern."
    )
    @APIResponse(
            responseCode = "200",
            description = "Document content returned successfully. Response includes path, content, format, and extension fields.",
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
    public DocResponse getDoc(
            @Parameter(
                    description = "Quarkus version branch or tag. Defaults to 'main' if omitted. When using 'main', results may include quarkiverse extension docs.",
                    required = false,
                    example = "3.27",
                    schema = @Schema(defaultValue = "main")
            )
            @QueryParam("version") String version,
            @Parameter(
                    description = "File path relative to the docs directory (e.g. security-overview.adoc). "
                            + "For quarkiverse extensions, use quarkiverse/<ext-name>/<file>.adoc",
                    required = true,
                    example = "security-overview.adoc"
            )
            @QueryParam("path") String path,
            @Parameter(
                    description = "Optional extension name to filter results (e.g. quarkus-openapi-generator for quarkiverse, or quarkus-core for core docs)",
                    required = false,
                    example = "quarkus-core"
            )
            @QueryParam("extension") String extension) {
        version = InputValidator.resolveVersion(version);
        InputValidator.validatePath(path);
        String content = docService.getOrFetchDoc(version, path);
        String ext = (extension != null && !extension.isBlank()) ? extension : "quarkus-core";
        return new DocResponse(path, content, "asciidoc", ext);
    }

}
