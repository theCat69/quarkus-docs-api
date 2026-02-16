package com.fvd.api.services;

import com.fvd.api.dto.MetaResponse;
import com.fvd.api.dto.meta.ApiInfo;
import com.fvd.api.dto.meta.ConstraintsMeta;
import com.fvd.api.dto.meta.EndpointMeta;
import com.fvd.api.dto.meta.FiltersMeta;
import com.fvd.api.dto.meta.PaginationMeta;
import com.fvd.api.dto.meta.ParameterMeta;
import com.fvd.api.dto.meta.SearchSyntaxMeta;
import com.fvd.cache.services.CacheService;
import com.fvd.common.SearchConstants;
import com.fvd.common.validators.InputValidator;
import com.fvd.subject.services.SubjectDeriver;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Service that assembles the API meta/capabilities response.
 * Combines static endpoint definitions with dynamic filter values.
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class MetaService {

    private final SubjectDeriver subjectDeriver;
    private final CacheService cacheService;

    /**
     * Builds the full API capabilities response.
     *
     * @return the meta response containing all endpoint, syntax, filter, and pagination info
     */
    public MetaResponse getCapabilities() {
        return new MetaResponse(
                buildApiInfo(),
                buildEndpoints(),
                buildSearchSyntax(),
                buildFilters(),
                buildPagination()
        );
    }

    private ApiInfo buildApiInfo() {
        return new ApiInfo(
                "Quarkus Documentation API",
                "REST API for searching and retrieving Quarkus framework documentation. " +
                        "Indexes docs from quarkusio.github.io and quarkiverse extensions. " +
                        "Optimized for AI agent consumption via MCP server.",
                InputValidator.DEFAULT_VERSION
        );
    }

    private List<EndpointMeta> buildEndpoints() {
        return List.of(
                buildRootEndpoint(),
                buildMetaEndpoint(),
                buildCatalogEndpoint(),
                buildSearchEndpoint(),
                buildDocumentsEndpoint(),
                buildCodeSamplesEndpoint(),
                buildSearchSyntaxEndpoint(),
                buildDocumentsBatchEndpoint(),
                buildDocumentsRelatedEndpoint()
        );
    }

    private EndpointMeta buildRootEndpoint() {
        return new EndpointMeta(
                "GET",
                "/",
                "API entry point",
                "Returns a welcome message with links to /api/meta and /q/openapi. " +
                        "Hit this first if you don't know where to start.",
                List.of()
        );
    }

    private EndpointMeta buildMetaEndpoint() {
        return new EndpointMeta(
                "GET",
                "/api/meta",
                "API capabilities and self-discovery",
                "Returns a machine-readable description of all API endpoints, parameters, " +
                        "constraints, search syntax, and available filter values. " +
                        "Call this first to understand the API.",
                List.of()
        );
    }

    private EndpointMeta buildCatalogEndpoint() {
        return new EndpointMeta(
                "GET",
                "/api/catalog",
                "List catalog information",
                "Returns lists of available subjects (with doc counts), extensions " +
                        "(with doc counts and keywords), and cached versions for a given version. " +
                        "Use this to discover valid filter values before searching.",
                List.of(buildVersionParameter())
        );
    }

    private EndpointMeta buildSearchEndpoint() {
        return new EndpointMeta(
                "GET",
                "/api/search",
                "Quick discovery search",
                "Returns lightweight document references (path, title, subject, extension, " +
                        "score, matchedKeywords, snippet) without full content. Best for initial " +
                        "discovery. Use the returned path with /api/documents?path=... to fetch full content.",
                List.of(
                        buildVersionParameter(),
                        new ParameterMeta("keywords", "string", true, null,
                                "Space-separated search keywords. Stop words are automatically filtered.",
                                null),
                        buildSubjectParameter(),
                        buildExtensionParameter(),
                        buildLimitParameter(),
                        buildOffsetParameter()
                )
        );
    }

    private EndpointMeta buildDocumentsEndpoint() {
        return new EndpointMeta(
                "GET",
                "/api/documents",
                "Get document by path or search by keywords",
                "Dual-mode endpoint. Path mode: provide 'path' to get a single document with full " +
                        "structured content (sections, code blocks). Search mode: provide 'keywords' to " +
                        "search documents with scores. If both provided, path takes precedence. Use " +
                        "'brief=true' in search mode for metadata-only results (no sections/codeBlocks). " +
                        "Returns 400 if neither path nor keywords is provided.",
                List.of(
                        buildVersionParameter(),
                        new ParameterMeta("path", "string", false, null,
                                "Document path relative to docs directory. If provided, returns single " +
                                        "document with full content. Either 'path' or 'keywords' must be provided.",
                                null),
                        new ParameterMeta("keywords", "string", false, null,
                                "Space-separated search keywords for document search. " +
                                        "Either 'keywords' or 'path' must be provided.",
                                null),
                        buildSubjectParameter("Subject filter (search mode only)."),
                        buildExtensionParameter("Extension filter (search mode only)."),
                        buildLimitParameter("Maximum number of results (search mode only)."),
                        buildOffsetParameter("Pagination offset (search mode only)."),
                        new ParameterMeta("brief", "boolean", false, "false",
                                "When true, returns only metadata without sections and codeBlocks " +
                                        "(search mode only, ignored in path mode).",
                                null)
                )
        );
    }

    private EndpointMeta buildCodeSamplesEndpoint() {
        return new EndpointMeta(
                "GET",
                "/api/code-samples",
                "Search code samples by keywords",
                "Searches for code examples matching keywords. Returns code samples with full content, " +
                        "language, context (surrounding section title), and relevance scores. " +
                        "Results sorted by score descending.",
                List.of(
                        buildVersionParameter(),
                        new ParameterMeta("keywords", "string", true, null,
                                "Space-separated search keywords.",
                                null),
                        new ParameterMeta("language", "string", false, null,
                                "Programming language filter (e.g., 'java', 'properties', 'yaml').",
                                null),
                        buildSubjectParameter(),
                        buildExtensionParameter(),
                        buildLimitParameter(),
                        buildOffsetParameter()
                )
        );
    }

    private EndpointMeta buildSearchSyntaxEndpoint() {
        return new EndpointMeta(
                "GET",
                "/api/search/syntax",
                "Search syntax documentation",
                "Returns comprehensive, machine-readable documentation of search query syntax, " +
                        "supported features, scoring behavior, stemming examples, stop words, " +
                        "and query examples. Call this to understand how to construct effective " +
                        "search queries.",
                List.of()
        );
    }

    private EndpointMeta buildDocumentsBatchEndpoint() {
        return new EndpointMeta(
                "POST",
                "/api/documents/batch",
                "Batch document retrieval",
                "Retrieve multiple documents by path in a single request. Accepts a JSON body " +
                        "with a list of paths (max 10). Returns partial success — found documents " +
                        "in 'documents' array, errors in 'errors' array. Use brief=true for " +
                        "metadata-only results.",
                List.of(
                        new ParameterMeta("body", "object", true, null,
                                "JSON body with 'paths' (required, list of document paths), " +
                                        "'version' (optional, default 'main'), " +
                                        "'brief' (optional, default false)",
                                new ConstraintsMeta(null, 10, null, null))
                )
        );
    }

    private EndpointMeta buildDocumentsRelatedEndpoint() {
        return new EndpointMeta(
                "GET",
                "/api/documents/related",
                "Find related documents",
                "Returns a ranked list of documents similar to a given source document, computed " +
                        "from shared keyword overlap. Results include similarity scores and shared " +
                        "keywords. Useful for graph-like navigation across the documentation corpus.",
                List.of(
                        buildVersionParameter(),
                        new ParameterMeta("path", "string", true, null,
                                "Path of the source document to find related documents for.",
                                null),
                        buildSubjectParameter("Filter related documents by subject category."),
                        buildExtensionParameter("Filter related documents by extension name."),
                        new ParameterMeta("limit", "integer", false, "5",
                                "Maximum number of related documents to return.",
                                new ConstraintsMeta(1, 20, null, null))
                )
        );
    }

    private ParameterMeta buildVersionParameter() {
        return new ParameterMeta("version", "string", false,
                InputValidator.DEFAULT_VERSION,
                "Quarkus version branch or tag. Defaults to 'main' if omitted.",
                new ConstraintsMeta(null, null, "[a-zA-Z0-9._/-]+", null));
    }

    private ParameterMeta buildSubjectParameter() {
        return buildSubjectParameter(
                "Filter results by subject category. Use /api/catalog to list valid subjects.");
    }

    private ParameterMeta buildSubjectParameter(String description) {
        return new ParameterMeta("subject", "string", false, null, description, null);
    }

    private ParameterMeta buildExtensionParameter() {
        return buildExtensionParameter(
                "Filter results by extension name (e.g., 'quarkus-core'). " +
                        "Use /api/catalog to list valid extensions.");
    }

    private ParameterMeta buildExtensionParameter(String description) {
        return new ParameterMeta("extension", "string", false, null, description, null);
    }

    private ParameterMeta buildLimitParameter() {
        return buildLimitParameter("Maximum number of results to return.");
    }

    private ParameterMeta buildLimitParameter(String description) {
        return new ParameterMeta("limit", "integer", false,
                String.valueOf(SearchConstants.DEFAULT_LIMIT),
                description,
                new ConstraintsMeta(1, SearchConstants.MAX_LIMIT, null, null));
    }

    private ParameterMeta buildOffsetParameter() {
        return buildOffsetParameter("Number of results to skip for pagination.");
    }

    private ParameterMeta buildOffsetParameter(String description) {
        return new ParameterMeta("offset", "integer", false,
                String.valueOf(SearchConstants.DEFAULT_OFFSET),
                description,
                new ConstraintsMeta(0, null, null, null));
    }

    private SearchSyntaxMeta buildSearchSyntax() {
        return new SearchSyntaxMeta(
                "space",
                "/api/search/syntax",
                List.of(
                        "Space-separated keywords (e.g., 'security authentication')",
                        "Stemming (e.g., 'configuring' matches 'configuration')",
                        "Prefix matching (e.g., 'secur' matches 'security')",
                        "Stop word filtering (common words like 'the', 'and' are removed)",
                        "Case-insensitive matching"
                ),
                List.of(
                        "Phrase search (quoted strings)",
                        "Boolean operators (AND, OR, NOT)",
                        "Wildcards (* or ?)",
                        "Field-specific queries (field:value)",
                        "Regular expressions"
                ),
                List.of(
                        "Use 2-3 specific keywords for best results",
                        "Prefer nouns over verbs (e.g., 'security' over 'securing')",
                        "Use /api/search for quick discovery, then /api/documents?path=... for full content",
                        "Use brief=true on /api/documents search to avoid downloading full document content",
                        "Check /api/catalog for valid subject and extension filter values",
                        "For comprehensive search syntax documentation including stemming examples, " +
                                "scoring details, and stop words, call GET /api/search/syntax"
                )
        );
    }

    private FiltersMeta buildFilters() {
        List<String> subjects = subjectDeriver.getValidSubjectNames().stream()
                .sorted()
                .toList();
        List<String> versions = cacheService.listCachedVersions();

        return new FiltersMeta(
                subjects,
                versions,
                "Extensions are version-specific and can be numerous. " +
                        "Use GET /api/catalog?version=X to list extensions for a specific version."
        );
    }

    private PaginationMeta buildPagination() {
        return new PaginationMeta(
                SearchConstants.DEFAULT_LIMIT,
                SearchConstants.MAX_LIMIT,
                SearchConstants.DEFAULT_OFFSET
        );
    }
}
