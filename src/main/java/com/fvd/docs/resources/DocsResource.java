package com.fvd.docs.resources;

import com.fvd.common.validators.InputValidator;
import com.fvd.docs.services.DocService;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;

@Path("/api/doc")
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class DocsResource {

    private final DocService docService;

    @GET
    public DocResponse getDoc(@QueryParam("version") String version,
                              @QueryParam("path") String path) {
        InputValidator.validateVersion(version);
        InputValidator.validatePath(path);
        String content = docService.getOrFetchDoc(version, path);
        return new DocResponse(path, content, "asciidoc");
    }

}
