package com.fvd.api.resources;

import com.fvd.api.dto.RelatedDocumentResponse;
import com.fvd.api.services.RelatedDocumentService;
import com.fvd.cache.services.CacheService;
import com.fvd.common.resources.ProblemDetail;
import com.fvd.common.validators.InputValidator;
import com.fvd.search.SearchConfig;
import com.fvd.subject.services.SubjectDeriver;
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
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * REST endpoint for finding documents related to a given source document.
 */
@Path("/api/documents/related")
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
@Tag(name = "Documents", description = "Document retrieval and search operations")
public class RelatedDocumentResource {

    private final RelatedDocumentService relatedDocumentService;
    private final CacheService cacheService;
    private final SubjectDeriver subjectDeriver;
    private final SearchConfig searchConfig;

    @GET
    @Operation(
            summary = "Find documents related to a given document",
            description = "Returns a ranked list of documents similar to the specified source document, " +
                    "computed from shared keyword overlap in the keyword index. " +
                    "Similarity is based on weighted cosine similarity of keyword vectors. " +
                    "Results are lightweight references (no full content) suitable for discovery."
    )
    @APIResponse(
            responseCode = "200",
            description = "Related documents returned successfully",
            content = @Content(schema = @Schema(implementation = RelatedDocumentResponse.class))
    )
    @APIResponse(
            responseCode = "400",
            description = "Invalid input parameters",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class))
    )
    @APIResponse(
            responseCode = "404",
            description = "Source document not found in keyword index",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class))
    )
    public RelatedDocumentResponse getRelatedDocuments(
            @Parameter(description = "Quarkus version branch or tag. Defaults to 'main' if omitted.",
                    required = false, example = "main", schema = @Schema(defaultValue = "main"))
            @QueryParam("version") String version,

            @Parameter(description = "Path of the source document to find related documents for.",
                    required = true, example = "security-overview.adoc")
            @QueryParam("path") String path,

            @Parameter(description = "Filter results by subject (e.g., 'security', 'rest-apis')",
                    required = false, example = "security")
            @QueryParam("subject") String subject,

            @Parameter(description = "Filter results by extension (e.g., 'quarkus-core')",
                    required = false, example = "quarkus-core")
            @QueryParam("extension") String extension,

            @Parameter(description = "Maximum number of related documents to return (default 5, max 20)",
                    required = false, example = "5")
            @QueryParam("limit") Integer limit,

            @Parameter(
                    description = "Comma-separated list of fields to include in each result item. " +
                            "When omitted, all fields are returned. " +
                            "Invalid field names return 400 with the list of available fields. " +
                            "Example: 'path,title,similarityScore'",
                    required = false,
                    example = "path,title,similarityScore"
            )
            @QueryParam("fields") String fields) {

        String resolvedVersion = InputValidator.resolveVersion(version);
        InputValidator.validateVersionExists(resolvedVersion, cacheService.listCachedVersions());
        InputValidator.validatePath(path);
        InputValidator.validateSubjectExists(subject, subjectDeriver.getValidSubjectNames());
        int resolvedLimit = InputValidator.validateLimit(limit,
                searchConfig.related().defaultLimit(),
                searchConfig.related().maxLimit());

        return relatedDocumentService.findRelatedDocuments(
                resolvedVersion, path, subject, extension, resolvedLimit);
    }
}
