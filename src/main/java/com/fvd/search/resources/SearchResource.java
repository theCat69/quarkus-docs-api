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

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 100;

    private final SearchService searchService;

    @GET
    @Path("/versions")
    @Operation(
            summary = "List available versions",
            description = "Returns a list of all cached versions that are available for searching."
    )
    @APIResponse(
            responseCode = "200",
            description = "List of available versions returned successfully",
            content = @Content(schema = @Schema(implementation = SearchResponse.class))
    )
    public SearchResponse<String> listVersions() {
        return new SearchResponse<>(searchService.listVersions());
    }

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
            @Parameter(description = "Quarkus version branch or tag", required = true, example = "3.27")
            @QueryParam("version") String version,
            @Parameter(description = "Comma-separated list of search keywords (case-insensitive, matched in lowercase)", required = true, example = "security,oidc")
            @QueryParam("keywords") String keywords,
            @Parameter(description = "Maximum number of results to return (default 10, max 100)", example = "10")
            @QueryParam("limit") Integer limit,
            @Parameter(description = "Number of results to skip (default 0)", example = "0")
            @QueryParam("offset") Integer offset) {
        InputValidator.validateVersion(version);
        InputValidator.validateKeywords(keywords);
        int validLimit = InputValidator.validateLimit(limit, DEFAULT_LIMIT, MAX_LIMIT);
        int validOffset = InputValidator.validateOffset(offset);
        List<String> keywordList = Arrays.asList(keywords.split(","));
        PaginatedResult<FileSearchResult> result = searchService.searchFiles(version, keywordList,
                validLimit, validOffset);
        return new SearchResponse<>(result.items(), result.total(), validLimit, validOffset);
    }

    @GET
    @Path("/sections")
    @Operation(
            summary = "Search sections by keywords",
            description = "Searches for sections matching the given keywords. "
                    + "Optionally filter by file paths. When filePaths is omitted, all files are searched."
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
            @Parameter(description = "Quarkus version branch or tag", required = true, example = "3.27")
            @QueryParam("version") String version,
            @Parameter(description = "Comma-separated list of search keywords (case-insensitive, matched in lowercase)", required = true, example = "security,oidc")
            @QueryParam("keywords") String keywords,
            @Parameter(description = "Comma-separated list of file paths relative to the docs directory (optional)", example = "security-overview.adoc,config.adoc")
            @QueryParam("filePaths") String filePaths,
            @Parameter(description = "Maximum number of results to return (default 10, max 100)", example = "10")
            @QueryParam("limit") Integer limit,
            @Parameter(description = "Number of results to skip (default 0)", example = "0")
            @QueryParam("offset") Integer offset) {
        InputValidator.validateVersion(version);
        InputValidator.validateKeywords(keywords);
        int validLimit = InputValidator.validateLimit(limit, DEFAULT_LIMIT, MAX_LIMIT);
        int validOffset = InputValidator.validateOffset(offset);
        List<String> keywordList = Arrays.asList(keywords.split(","));
        List<String> filePathList = null;
        if (filePaths != null && !filePaths.isBlank()) {
            InputValidator.validateFilePaths(filePaths);
            filePathList = Arrays.asList(filePaths.split(","));
        }
        PaginatedResult<SectionSearchResult> result = searchService.searchSections(version, keywordList,
                filePathList, validLimit, validOffset);
        return new SearchResponse<>(result.items(), result.total(), validLimit, validOffset);
    }

    @GET
    @Path("/section-content")
    @Operation(
            summary = "Get section content",
            description = "Returns the raw AsciiDoc content and metadata for a specific section in a document. "
                    + "Supports fuzzy matching: if no exact title match is found, the best fuzzy match is returned "
                    + "based on Levenshtein similarity, substring containment, and word overlap. "
                    + "The response includes matchedTitle, matchScore (0.0-1.0), and matchType (exact/partial/keyword) "
                    + "to indicate how the section was matched."
    )
    @APIResponse(
            responseCode = "200",
            description = "Section content retrieved successfully. Check matchType to see if match was exact or fuzzy.",
            content = @Content(schema = @Schema(implementation = SectionContentResult.class))
    )
    @APIResponse(
            responseCode = "400",
            description = "Invalid input parameters",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    )
    @APIResponse(
            responseCode = "404",
            description = "Document not found, or no section matched above the similarity threshold",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    )
    public SectionContentResult getSectionContent(
            @Parameter(description = "Quarkus version branch or tag", required = true, example = "3.27")
            @QueryParam("version") String version,
            @Parameter(description = "File path relative to the docs directory", required = true, example = "security-overview.adoc")
            @QueryParam("filePath") String filePath,
            @Parameter(description = "Title of the section to retrieve. Supports fuzzy matching: partial titles, "
                    + "keywords, and minor misspellings will be matched to the closest section.", required = true, example = "Getting Started")
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
            @Parameter(description = "Quarkus version branch or tag", required = true, example = "3.27")
            @QueryParam("version") String version,
            @Parameter(description = "Comma-separated list of search keywords (case-insensitive, matched in lowercase)", required = true, example = "security,oidc")
            @QueryParam("keywords") String keywords,
            @Parameter(description = "Optional file path to filter results", example = "security-overview.adoc")
            @QueryParam("filePath") String filePath,
            @Parameter(description = "Optional section title to filter results", example = "Authentication")
            @QueryParam("sectionTitle") String sectionTitle,
            @Parameter(description = "Maximum number of results to return (default 10, max 100)", example = "10")
            @QueryParam("limit") Integer limit,
            @Parameter(description = "Number of results to skip (default 0)", example = "0")
            @QueryParam("offset") Integer offset) {
        InputValidator.validateVersion(version);
        InputValidator.validateKeywords(keywords);
        int validLimit = InputValidator.validateLimit(limit, DEFAULT_LIMIT, MAX_LIMIT);
        int validOffset = InputValidator.validateOffset(offset);
        if (filePath != null && !filePath.isBlank()) {
            InputValidator.validatePath(filePath);
        }
        if (sectionTitle != null && !sectionTitle.isBlank()) {
            InputValidator.validateSectionTitle(sectionTitle);
        }
        List<String> keywordList = Arrays.asList(keywords.split(","));
        PaginatedResult<CodeSampleSearchResult> result = searchService.searchCodeSamples(
                version, keywordList, filePath, sectionTitle, validLimit, validOffset);
        return new SearchResponse<>(result.items(), result.total(), validLimit, validOffset);
    }

    @GET
    @Path("/content")
    @Operation(
            summary = "Full-text search in document content",
            description = "Full-text search across all document content for a given version. "
                    + "Returns matching excerpts ranked by relevance. "
                    + "Optionally filter by file paths. When filePaths is omitted, all files are searched."
    )
    @APIResponse(
            responseCode = "200",
            description = "Content search results returned successfully",
            content = @Content(schema = @Schema(implementation = SearchResponse.class))
    )
    @APIResponse(
            responseCode = "400",
            description = "Invalid input parameters (missing or malformed version/keywords/filePaths)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    )
    public SearchResponse<ContentSearchResult> searchContent(
            @Parameter(description = "Quarkus version branch or tag", required = true, example = "3.27")
            @QueryParam("version") String version,
            @Parameter(description = "Comma-separated list of search keywords (case-insensitive, matched in document body)", required = true, example = "security,oidc")
            @QueryParam("keywords") String keywords,
            @Parameter(description = "Comma-separated list of file paths relative to the docs directory (optional)", example = "security-overview.adoc,config.adoc")
            @QueryParam("filePaths") String filePaths,
            @Parameter(description = "Maximum number of results to return (default 10, max 100)", example = "10")
            @QueryParam("limit") Integer limit,
            @Parameter(description = "Number of results to skip (default 0)", example = "0")
            @QueryParam("offset") Integer offset) {
        InputValidator.validateVersion(version);
        InputValidator.validateKeywords(keywords);
        int validLimit = InputValidator.validateLimit(limit, DEFAULT_LIMIT, MAX_LIMIT);
        int validOffset = InputValidator.validateOffset(offset);
        List<String> keywordList = Arrays.asList(keywords.split(","));
        List<String> filePathList = null;
        if (filePaths != null && !filePaths.isBlank()) {
            InputValidator.validateFilePaths(filePaths);
            filePathList = Arrays.asList(filePaths.split(","));
        }
        PaginatedResult<ContentSearchResult> result = searchService.searchContent(
                version, keywordList, filePathList, validLimit, validOffset);
        return new SearchResponse<>(result.items(), result.total(), validLimit, validOffset);
    }
}
