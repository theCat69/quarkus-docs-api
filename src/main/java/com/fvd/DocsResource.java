package com.fvd;

import java.util.List;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
public class DocsResource {

    @GET
    @Path("/index")
    public String getIndex(@QueryParam("version") String version) {
        InputValidator.validateVersion(version);
        return "[]";
    }

    @GET
    @Path("/doc")
    public DocResponse getDoc(@QueryParam("version") String version,
                              @QueryParam("path") String path) {
        InputValidator.validateVersion(version);
        InputValidator.validatePath(path);
        throw new DocNotFoundException("Document not found: " + path);
    }

    @GET
    @Path("/search/files")
    public SearchResponse<FileSearchResult> searchFiles(@QueryParam("version") String version,
                                                        @QueryParam("keywords") String keywords) {
        InputValidator.validateVersion(version);
        InputValidator.validateKeywords(keywords);
        return new SearchResponse<>(List.of());
    }

    @GET
    @Path("/search/sections")
    public SearchResponse<SectionSearchResult> searchSections(@QueryParam("version") String version,
                                                              @QueryParam("keywords") String keywords,
                                                              @QueryParam("filePaths") String filePaths) {
        InputValidator.validateVersion(version);
        InputValidator.validateKeywords(keywords);
        InputValidator.validateFilePaths(filePaths);
        return new SearchResponse<>(List.of());
    }

    @GET
    @Path("/health")
    public HealthResponse health() {
        return new HealthResponse("UP");
    }
}
