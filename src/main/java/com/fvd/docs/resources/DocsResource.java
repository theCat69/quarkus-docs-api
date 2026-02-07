package com.fvd.docs.resources;

import com.fvd.common.validators.InputValidator;
import com.fvd.docs.services.DocService;
import com.fvd.indexs.services.IndexService;
import com.fvd.search.resources.SearchResponse;
import com.fvd.search.services.FileSearchResult;
import com.fvd.search.services.SearchService;
import com.fvd.search.services.SectionSearchResult;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.Arrays;
import java.util.List;

@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
public class DocsResource {

    private final IndexService indexService;
    private final DocService docService;
    private final SearchService searchService;

    @Inject
    public DocsResource(IndexService indexService, DocService docService, SearchService searchService) {
        this.indexService = indexService;
        this.docService = docService;
        this.searchService = searchService;
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
        List<String> keywordList = Arrays.asList(keywords.split(","));
        List<FileSearchResult> results = searchService.searchFiles(version, keywordList);
        return new SearchResponse<>(results);
    }

    @GET
    @Path("/search/sections")
    public SearchResponse<SectionSearchResult> searchSections(@QueryParam("version") String version,
                                                              @QueryParam("keywords") String keywords,
                                                              @QueryParam("filePaths") String filePaths) {
        InputValidator.validateVersion(version);
        InputValidator.validateKeywords(keywords);
        InputValidator.validateFilePaths(filePaths);
        List<String> keywordList = Arrays.asList(keywords.split(","));
        List<String> filePathList = Arrays.asList(filePaths.split(","));
        List<SectionSearchResult> results = searchService.searchSections(version, keywordList, filePathList);
        return new SearchResponse<>(results);
    }
}
