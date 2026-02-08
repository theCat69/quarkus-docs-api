package com.fvd.search.resources;

import com.fvd.common.resources.ErrorResponse;
import com.fvd.common.validators.InputValidator;
import com.fvd.search.services.*;
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

import java.util.Arrays;
import java.util.List;

@Path("/api/search")
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class SearchResource {
    private final SearchService searchService;

    @GET
    @Path("/files")
    @Operation(
            summary = "Search files by keywords",
            description = "Searches the keyword index for files matching the given keywords. Returns files ranked by relevance score."
    )
    @APIResponse(
            responseCode = "200",
            description = "Search results returned successfully",
            content = @Content(schema = @Schema(implementation = SearchResponse.class))
    )
    @APIResponse(
            responseCode = "400",
            description = "Invalid input parameters (missing or malformed version/keywords)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    )
    public SearchResponse<FileSearchResult> searchFiles(
            @Parameter(description = "Quarkus version branch or tag", required = true, example = "3.21")
            @QueryParam("version") String version,
            @Parameter(description = "Comma-separated list of search keywords", required = true, example = "security,oidc")
            @QueryParam("keywords") String keywords) {
        InputValidator.validateVersion(version);
        InputValidator.validateKeywords(keywords);
        List<String> keywordList = Arrays.asList(keywords.split(","));
        List<FileSearchResult> results = searchService.searchFiles(version, keywordList);
        return new SearchResponse<>(results);
    }

    @GET
    @Path("/sections")
    @Operation(
            summary = "Search sections by keywords",
            description = "Searches within specific files for sections matching the given keywords. Returns sections ranked by relevance score."
    )
    @APIResponse(
            responseCode = "200",
            description = "Section search results returned successfully",
            content = @Content(schema = @Schema(implementation = SearchResponse.class))
    )
    @APIResponse(
            responseCode = "400",
            description = "Invalid input parameters (missing or malformed version/keywords/filePaths)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    )
    public SearchResponse<SectionSearchResult> searchSections(
            @Parameter(description = "Quarkus version branch or tag", required = true, example = "3.21")
            @QueryParam("version") String version,
            @Parameter(description = "Comma-separated list of search keywords", required = true, example = "security,oidc")
            @QueryParam("keywords") String keywords,
            @Parameter(description = "Comma-separated list of file paths relative to the docs directory", required = true, example = "security-overview.adoc,config.adoc")
            @QueryParam("filePaths") String filePaths) {
        InputValidator.validateVersion(version);
        InputValidator.validateKeywords(keywords);
        InputValidator.validateFilePaths(filePaths);
        List<String> keywordList = Arrays.asList(keywords.split(","));
        List<String> filePathList = Arrays.asList(filePaths.split(","));
        List<SectionSearchResult> results = searchService.searchSections(version, keywordList, filePathList);
        return new SearchResponse<>(results);
    }

    @GET
    @Path("/section-content")
    @Operation(
            summary = "Get section content",
            description = "Returns the raw AsciiDoc content and metadata for a specific section in a document."
    )
    @APIResponse(
            responseCode = "200",
            description = "Section content retrieved successfully",
            content = @Content(schema = @Schema(implementation = SectionContentResult.class))
    )
    @APIResponse(
            responseCode = "400",
            description = "Invalid input parameters",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    )
    @APIResponse(
            responseCode = "404",
            description = "Document or section not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    )
    public SectionContentResult getSectionContent(
            @Parameter(description = "Quarkus version branch or tag", required = true, example = "3.21")
            @QueryParam("version") String version,
            @Parameter(description = "File path relative to the docs directory", required = true, example = "security-overview.adoc")
            @QueryParam("filePath") String filePath,
            @Parameter(description = "Title of the section to retrieve", required = true, example = "Getting Started")
            @QueryParam("sectionTitle") String sectionTitle) {
        InputValidator.validateVersion(version);
        InputValidator.validatePath(filePath);
        InputValidator.validateSectionTitle(sectionTitle);
        return searchService.getSectionContent(version, filePath, sectionTitle);
    }

    @GET
    @Path("/code-samples")
    @Operation(
            summary = "Search code samples by keywords",
            description = "Searches the code sample index for code blocks matching the given keywords. "
                    + "Returns code samples ranked by relevance score. Optionally filter by file path or section title."
    )
    @APIResponse(
            responseCode = "200",
            description = "Code sample search results returned successfully",
            content = @Content(schema = @Schema(implementation = SearchResponse.class))
    )
    @APIResponse(
            responseCode = "400",
            description = "Invalid input parameters (missing or malformed version/keywords)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    )
    public SearchResponse<CodeSampleSearchResult> searchCodeSamples(
            @Parameter(description = "Quarkus version branch or tag", required = true, example = "3.21")
            @QueryParam("version") String version,
            @Parameter(description = "Comma-separated list of search keywords", required = true, example = "security,oidc")
            @QueryParam("keywords") String keywords,
            @Parameter(description = "Optional file path to filter results", example = "security-overview.adoc")
            @QueryParam("filePath") String filePath,
            @Parameter(description = "Optional section title to filter results", example = "Authentication")
            @QueryParam("sectionTitle") String sectionTitle) {
        InputValidator.validateVersion(version);
        InputValidator.validateKeywords(keywords);
        if (filePath != null && !filePath.isBlank()) {
            InputValidator.validatePath(filePath);
        }
        if (sectionTitle != null && !sectionTitle.isBlank()) {
            InputValidator.validateSectionTitle(sectionTitle);
        }
        List<String> keywordList = Arrays.asList(keywords.split(","));
        List<CodeSampleSearchResult> results = searchService.searchCodeSamples(
                version, keywordList, filePath, sectionTitle);
        return new SearchResponse<>(results);
    }
}
