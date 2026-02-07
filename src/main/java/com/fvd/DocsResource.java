package com.fvd;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
public class DocsResource {

    private final IndexService indexService;
    private final DocService docService;

    @Inject
    public DocsResource(IndexService indexService, DocService docService) {
        this.indexService = indexService;
        this.docService = docService;
    }

    @GET
    @Path("/index")
    public String getIndex(@QueryParam("version") String version) {
        InputValidator.validateVersion(version);
        return indexService.getOrFetchIndex(version);
    }

    @GET
    @Path("/doc")
    public DocResponse getDoc(@QueryParam("version") String version,
                              @QueryParam("path") String path) {
        InputValidator.validateVersion(version);
        InputValidator.validatePath(path);
        String content = docService.getOrFetchDoc(version, path);
        return new DocResponse(path, content, "asciidoc");
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
