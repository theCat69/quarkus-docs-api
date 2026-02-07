package com.fvd.search.resources;

import com.fvd.common.validators.InputValidator;
import com.fvd.search.services.FileSearchResult;
import com.fvd.search.services.SearchService;
import com.fvd.search.services.SectionSearchResult;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.List;

@Path("/api/search")
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class SearchResource {
    private final SearchService searchService;

    @GET
    @Path("/files")
    public SearchResponse<FileSearchResult> searchFiles(@QueryParam("version") String version,
                                                        @QueryParam("keywords") String keywords) {
        InputValidator.validateVersion(version);
        InputValidator.validateKeywords(keywords);
        List<String> keywordList = Arrays.asList(keywords.split(","));
        List<FileSearchResult> results = searchService.searchFiles(version, keywordList);
        return new SearchResponse<>(results);
    }

    @GET
    @Path("/sections")
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
