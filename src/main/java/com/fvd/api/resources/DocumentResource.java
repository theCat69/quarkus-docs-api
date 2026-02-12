package com.fvd.api.resources;

import com.fvd.api.dto.DocumentResponse;
import com.fvd.api.dto.DocumentSearchResponse;
import com.fvd.api.services.DocumentService;
import com.fvd.common.exceptions.InvalidInputException;
import com.fvd.common.resources.ErrorResponse;
import com.fvd.common.validators.InputValidator;
import com.fvd.docs.exceptions.DocNotFoundException;
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

import java.util.List;

/**
 * REST endpoint for document retrieval and search.
 */
@Path("/api/documents")
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
@Tag(name = "Documents", description = "Document retrieval and search operations")
public class DocumentResource {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final DocumentService documentService;

    @GET
    @Operation(
            summary = "Get document by path or search by keywords",
            description = "If 'path' is provided, returns a single document with full structured content " +
                    "including sections and code blocks. If 'keywords' is provided, searches documents " +
                    "and returns matching results with scores. Path takes precedence if both are provided. " +
                    "Returns 400 if neither path nor keywords is provided."
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
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    )
    @APIResponse(
            responseCode = "404",
            description = "Document not found (path mode only)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    )
    public Object getDocuments(
            @Parameter(
                    description = "Quarkus version branch or tag. Defaults to 'main' if omitted.",
                    required = false,
                    example = "3.27",
                    schema = @Schema(defaultValue = "main")
            )
            @QueryParam("version") String version,

            @Parameter(
                    description = "Document path relative to docs directory. If provided, returns single document.",
                    required = false,
                    example = "security-overview.adoc"
            )
            @QueryParam("path") String path,

            @Parameter(
                    description = "Space-separated search keywords. Required if path not provided.",
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
            @QueryParam("offset") Integer offset) {

        version = InputValidator.resolveVersion(version);

        // Path mode takes precedence
        if (path != null && !path.isBlank()) {
            InputValidator.validatePath(path);
            DocumentResponse doc = documentService.getDocumentByPath(version, path);
            if (doc == null) {
                throw new DocNotFoundException("Document not found: " + path);
            }
            return doc;
        }

        // Search mode
        if (keywords != null && !keywords.isBlank()) {
            List<String> keywordList = InputValidator.parseKeywords(keywords);
            int validLimit = InputValidator.validateLimit(limit, DEFAULT_LIMIT, MAX_LIMIT);
            int validOffset = InputValidator.validateOffset(offset);

            return documentService.searchDocuments(version, keywordList, subject, extension,
                    validLimit, validOffset);
        }

        // Neither provided
        throw new InvalidInputException("Either 'path' or 'keywords' must be provided");
    }
}
