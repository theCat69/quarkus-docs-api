package com.fvd.api.resources;

import com.fvd.api.dto.BatchDocumentRequest;
import com.fvd.api.dto.BatchDocumentResponse;
import com.fvd.api.dto.DocumentResponse;
import com.fvd.api.dto.DocumentSearchResponse;
import com.fvd.api.dto.SearchParams;
import com.fvd.api.services.DocumentService;
import com.fvd.cache.services.CacheService;
import com.fvd.common.exceptions.InvalidInputException;
import com.fvd.common.resources.ProblemDetail;
import com.fvd.common.validators.InputValidator;
import com.fvd.docs.exceptions.DocNotFoundException;
import com.fvd.subject.services.SubjectDeriver;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

/**
 * REST endpoint for document retrieval and search.
 */
@Path("/api/documents")
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
@Tag(name = "Documents", description = "Document retrieval and search operations")
public class DocumentResource {

    private final DocumentService documentService;
    private final CacheService cacheService;
    private final SubjectDeriver subjectDeriver;

    @ConfigProperty(name = "app.batch.max-size", defaultValue = "10")
    int maxBatchSize;

    @GET
    @Operation(
            summary = "Get document by path or search by keywords (at least one required)",
            description = "REQUIRED: At least one of 'path' or 'keywords' must be provided. " +
                    "Returns 400 if neither is specified.\n\n" +
                    "Mode 1 — Path lookup: If 'path' is provided, returns a single document with full " +
                    "structured content including sections and code blocks.\n" +
                    "Mode 2 — Keyword search: If 'keywords' is provided, searches documents and returns " +
                    "matching results with scores. Supports optional 'subject' and 'extension' filters. " +
                    "Use 'brief=true' to return only metadata without sections and codeBlocks.\n\n" +
                    "If both 'path' and 'keywords' are provided, path takes precedence (keyword search is ignored)."
    )
    @APIResponse(
            responseCode = "200",
            description = "Document(s) returned successfully. Single document if path mode, " +
                    "search results if search mode.",
            content = @Content(schema = @Schema(oneOf = {DocumentResponse.class, DocumentSearchResponse.class}))
    )
    @APIResponse(
            responseCode = "400",
            description = "Invalid input parameters or neither path nor keywords provided",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class))
    )
    @APIResponse(
            responseCode = "404",
            description = "Document not found (path mode only)",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class))
    )
    public Object getDocuments(
            @Parameter(
                    description = "Quarkus version branch or tag. Defaults to 'main' if omitted.",
                    required = false,
                    example = "main",
                    schema = @Schema(defaultValue = "main")
            )
            @QueryParam("version") String version,

            @Parameter(
                    description = "Document path relative to docs directory. If provided, returns a single document " +
                            "with full content. Either 'path' or 'keywords' must be provided.",
                    required = false,
                    example = "security-overview.adoc"
            )
            @QueryParam("path") String path,

            @Parameter(
                    description = "Space-separated search keywords for document search. " +
                            "Either 'keywords' or 'path' must be provided.",
                    required = false,
                    example = "security oidc"
            )
            @QueryParam("keywords") String keywords,

            @Parameter(
                    description = "Subject filter (e.g., 'security', 'rest-apis')",
                    required = false,
                    example = "security"
            )
            @QueryParam("subject") String subject,

            @Parameter(
                    description = "Extension filter (e.g., 'quarkus-core', 'quarkus-resteasy-reactive')",
                    required = false,
                    example = "quarkus-core"
            )
            @QueryParam("extension") String extension,

            @Parameter(
                    description = "Maximum number of results to return (default 20, max 100)",
                    required = false,
                    example = "20"
            )
            @QueryParam("limit") Integer limit,

            @Parameter(
                    description = "Number of results to skip for pagination (default 0)",
                    required = false,
                    example = "0"
            )
            @QueryParam("offset") Integer offset,

            @Parameter(
                    description = "When true, returns only metadata (title, description, path, subject, " +
                            "extension, matchedKeywords, score) without full sections and codeBlocks. " +
                            "Useful for lightweight discovery before fetching full documents by path. " +
                            "Only applies to search mode (ignored in path mode).",
                    required = false,
                    example = "true",
                    schema = @Schema(defaultValue = "false")
            )
            @QueryParam("brief") Boolean brief,

            @Parameter(
                    description = "Comma-separated list of fields to include in each result item. " +
                            "When omitted, all fields are returned. " +
                            "Invalid field names return 400 with the list of available fields. " +
                            "Example: 'title,path,score'",
                    required = false,
                    example = "title,path,score"
            )
            @QueryParam("fields") String fields) {

        // Path mode takes precedence
        if (path != null && !path.isBlank()) {
            String resolvedVersion = InputValidator.resolveVersion(version);
            InputValidator.validateVersionExists(resolvedVersion, cacheService.listCachedVersions());
            InputValidator.validatePath(path);
            DocumentResponse doc = documentService.getDocumentByPath(resolvedVersion, path);
            if (doc == null) {
                throw new DocNotFoundException("Document not found: " + path);
            }
            return doc;
        }

        // Search mode
        if (keywords != null && !keywords.isBlank()) {
            SearchParams params = SearchParams.fromRaw(version, keywords, subject, extension, limit, offset);
            InputValidator.validateVersionExists(params.version(), cacheService.listCachedVersions());
            InputValidator.validateSubjectExists(params.subject(), subjectDeriver.getValidSubjectNames());

            return documentService.searchDocuments(params.version(), params.keywords(), params.subject(),
                    params.extension(), params.limit(), params.offset(), Boolean.TRUE.equals(brief));
        }

        // Neither provided
        throw new InvalidInputException("Either 'path' or 'keywords' must be provided");
    }

    @POST
    @Path("/batch")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(
            summary = "Retrieve multiple documents by path in a single request",
            description = "Accepts a JSON body with a list of document paths and returns each document's " +
                    "full structured content (or brief metadata if brief=true). Partial failures are " +
                    "reported per-path in the 'errors' array — the request succeeds (200) as long as " +
                    "at least one document is found. Returns 400 if the request body is invalid " +
                    "(empty paths, too many paths, or malformed input). Returns 404 only if ALL " +
                    "requested documents are not found."
    )
    @APIResponse(responseCode = "200", description = "Batch results returned (may include partial errors)",
            content = @Content(schema = @Schema(implementation = BatchDocumentResponse.class)))
    @APIResponse(responseCode = "400", description = "Invalid request (empty paths, exceeds max batch size, invalid path format)",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @APIResponse(responseCode = "404", description = "All requested documents not found",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public BatchDocumentResponse getDocumentsBatch(
            BatchDocumentRequest request,

            @Parameter(
                    description = "Comma-separated list of fields to include in the response. " +
                            "When omitted, all fields are returned. " +
                            "Invalid field names return 400 with the list of available fields. " +
                            "Example: 'documents,retrievedCount'",
                    required = false,
                    example = "documents,retrievedCount"
            )
            @QueryParam("fields") String fields) {
        if (request == null) {
            throw new InvalidInputException("Request body is required");
        }

        String resolvedVersion = InputValidator.resolveVersion(request.version);
        InputValidator.validateVersionExists(resolvedVersion, cacheService.listCachedVersions());
        List<String> validatedPaths = InputValidator.validateBatchPaths(request.paths, maxBatchSize);

        boolean brief = Boolean.TRUE.equals(request.brief);
        BatchDocumentResponse response = documentService.getDocumentsBatch(resolvedVersion, validatedPaths, brief);

        if (response.retrievedCount == 0 && response.errorCount > 0) {
            throw new DocNotFoundException("None of the requested documents were found");
        }

        return response;
    }
}
