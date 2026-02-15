package com.fvd.api.resources;

import com.fvd.api.dto.SearchSyntaxResponse;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * REST endpoint returning machine-readable search syntax documentation.
 */
@Path("/api/search/syntax")
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
@Tag(name = "Search", description = "Quick discovery search returning lightweight references")
public class SearchSyntaxResource {

    @GET
    @Operation(
            summary = "Search syntax documentation",
            description = "Returns machine-readable documentation of search query syntax, " +
                    "supported features, scoring behavior, and examples. " +
                    "AI agents should call this endpoint to understand how to construct effective queries."
    )
    @APIResponse(
            responseCode = "200",
            description = "Search syntax documentation",
            content = @Content(schema = @Schema(implementation = SearchSyntaxResponse.class))
    )
    public SearchSyntaxResponse getSyntax() {
        return SearchSyntaxResponse.INSTANCE;
    }
}
