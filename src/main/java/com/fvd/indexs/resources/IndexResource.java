package com.fvd.indexs.resources;

import com.fvd.common.resources.ErrorResponse;
import com.fvd.common.validators.InputValidator;
import com.fvd.github.clients.GithubApiIndex;
import com.fvd.indexs.services.IndexService;
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

import java.util.List;

@Path("/api/index")
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class IndexResource {
    private final IndexService indexService;

    @GET
    @Operation(
            summary = "Get file index",
            description = "Returns the file index for a Quarkus documentation version. Lists all available documentation files with their paths and SHA hashes."
    )
    @APIResponse(
            responseCode = "200",
            description = "File index returned successfully",
            content = @Content(schema = @Schema(implementation = GithubApiIndex[].class))
    )
    @APIResponse(
            responseCode = "400",
            description = "Invalid version parameter",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    )
    @APIResponse(
            responseCode = "502",
            description = "Upstream GitHub API error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    )
    public List<GithubApiIndex> getIndex(@QueryParam("version") String version) {
        InputValidator.validateVersion(version);
        return indexService.getOrFetchIndex(version);
    }
}
