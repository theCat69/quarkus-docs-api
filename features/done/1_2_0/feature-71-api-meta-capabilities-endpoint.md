# Feature 71: API Meta/Capabilities Endpoint

> **Dependencies**: None. This is a self-contained new endpoint. Uses existing `CatalogService`, `SubjectDeriver`, and `CacheService` for dynamic filter values but introduces no changes to existing endpoints.

> **Note**: Feature 70 (Search Syntax Documentation Endpoint) provides a dedicated `/api/search/syntax` endpoint with comprehensive search syntax details. The `searchSyntax` section in this meta response is intentionally a summary — agents needing full details (stemming examples, scoring weights, stop word list) should call `/api/search/syntax` directly.

## Summary

AI agents consuming this API through an MCP server currently have no machine-readable way to discover what the API can do — which endpoints exist, what parameters they accept, what constraints apply, what search syntax is supported, and what filter values are valid. They must rely on parsing OpenAPI specs or external documentation. This feature adds a new `GET /api/meta` endpoint that returns a structured JSON description of the entire API surface: endpoints, parameters (types, constraints, defaults), search syntax capabilities, available filter values, and pagination rules. This is the first endpoint an AI agent calls to understand the API before making any other request.

## User Story

As an **AI agent connecting to the Quarkus Docs API for the first time**, I want to call a single endpoint that returns a machine-readable description of all available endpoints, their parameters, constraints, search syntax rules, and valid filter values so that I can construct correct API requests without parsing OpenAPI specs or relying on external documentation.

## Motivation

### Current Behavior

An AI agent must either:
1. Parse the OpenAPI spec at `/q/openapi` (verbose, requires OpenAPI parsing capability)
2. Be pre-configured with API knowledge (brittle, breaks when API evolves)
3. Trial-and-error with 400 responses to discover constraints

### Desired Behavior

`GET /api/meta` returns a compact, purpose-built JSON response that tells the agent everything it needs:
- What endpoints exist and what they do
- What parameters each endpoint accepts (type, required, default, constraints)
- What search syntax is supported (and what is NOT supported)
- What filter values are currently valid (subjects, extensions, versions)
- How pagination works

### Why Not Just Use OpenAPI?

OpenAPI is designed for tooling (Swagger UI, code generators), not for AI agent self-discovery. It is verbose (~100KB+), requires OpenAPI-aware parsing, and does not capture semantic information like "search supports stemming but not boolean operators" or "use the two-step discovery pattern: search first, then fetch by path."

The `/api/meta` endpoint is a **semantic API description** — it tells the agent how to *use* the API effectively, not just what HTTP calls are available.

---

## Requirements

### R1: New `MetaResource` Endpoint

**New file:** `src/main/java/com/fvd/api/resources/MetaResource.java`

Create a new JAX-RS resource following existing patterns:

```java
@Path("/api/meta")
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
@Tag(name = "Meta", description = "API capabilities and self-discovery for AI agents")
public class MetaResource {

    private final MetaService metaService;

    @GET
    @Operation(
            summary = "API capabilities and self-discovery",
            description = "Returns a machine-readable description of all API endpoints, " +
                    "parameters, constraints, search syntax, and available filter values. " +
                    "Designed for AI agents to self-discover API capabilities on first connection."
    )
    @APIResponse(
            responseCode = "200",
            description = "API capabilities returned successfully",
            content = @Content(schema = @Schema(implementation = MetaResponse.class))
    )
    public Response getMeta() {
        MetaResponse meta = metaService.getCapabilities();
        return Response.ok(meta)
                .header("Cache-Control", "public, max-age=3600")
                .build();
    }
}
```

**Key decisions:**
- No query parameters — the meta endpoint describes the entire API
- Returns `Response` instead of the DTO directly to add `Cache-Control` header
- `Cache-Control: public, max-age=3600` (1 hour) since meta changes rarely but filter values (subjects, extensions, versions) can change on cache refresh
- No version parameter — meta describes all versions, and the dynamic filter values include all available versions

### R2: New `MetaService` Service

**New file:** `src/main/java/com/fvd/api/services/MetaService.java`

```java
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class MetaService {

    private final SubjectDeriver subjectDeriver;
    private final CacheService cacheService;

    public MetaResponse getCapabilities() {
        return new MetaResponse(
                buildApiInfo(),
                buildEndpoints(),
                buildSearchSyntax(),
                buildFilters(),
                buildPagination()
        );
    }
}
```

The service assembles the response from:
- **Static data**: endpoint descriptions, parameter definitions, search syntax rules, pagination constraints (hardcoded — these reflect the API design)
- **Dynamic data**: valid subject names from `SubjectDeriver.getValidSubjectNames()`, cached versions from `CacheService.listCachedVersions()`

Extensions are intentionally excluded from the meta response filters — the list can be large and version-dependent. Agents should use `GET /api/catalog?version=X` to discover extensions for a specific version. The meta response documents this in the endpoint description.

### R3: Response DTOs

**New file:** `src/main/java/com/fvd/api/dto/MetaResponse.java`

Top-level response DTO:

```java
@RegisterForReflection
@NoArgsConstructor
@AllArgsConstructor
public class MetaResponse {

    public ApiInfo apiInfo;
    public List<EndpointMeta> endpoints;
    public SearchSyntaxMeta searchSyntax;
    public FiltersMeta filters;
    public PaginationMeta pagination;

}
```

**New file:** `src/main/java/com/fvd/api/dto/meta/ApiInfo.java`

```java
@RegisterForReflection
@NoArgsConstructor
@AllArgsConstructor
public class ApiInfo {

    public String name;
    public String description;
    public String defaultVersion;

}
```

**New file:** `src/main/java/com/fvd/api/dto/meta/EndpointMeta.java`

```java
@RegisterForReflection
@NoArgsConstructor
@AllArgsConstructor
public class EndpointMeta {

    public String method;
    public String path;
    public String summary;
    public String description;
    public List<ParameterMeta> parameters;

}
```

**New file:** `src/main/java/com/fvd/api/dto/meta/ParameterMeta.java`

```java
@RegisterForReflection
@NoArgsConstructor
@AllArgsConstructor
public class ParameterMeta {

    public String name;
    public String type;
    public boolean required;
    public String defaultValue;
    public String description;
    public ConstraintsMeta constraints;

}
```

**New file:** `src/main/java/com/fvd/api/dto/meta/ConstraintsMeta.java`

```java
@RegisterForReflection
@NoArgsConstructor
@AllArgsConstructor
public class ConstraintsMeta {

    public Integer min;
    public Integer max;
    public String pattern;
    public List<String> allowedValues;

}
```

**New file:** `src/main/java/com/fvd/api/dto/meta/SearchSyntaxMeta.java`

```java
@RegisterForReflection
@NoArgsConstructor
@AllArgsConstructor
public class SearchSyntaxMeta {

    public String keywordSeparator;
    public List<String> supportedFeatures;
    public List<String> unsupportedFeatures;
    public List<String> tips;

}
```

**New file:** `src/main/java/com/fvd/api/dto/meta/FiltersMeta.java`

```java
@RegisterForReflection
@NoArgsConstructor
@AllArgsConstructor
public class FiltersMeta {

    public List<String> subjects;
    public List<String> versions;
    public String extensionsNote;

}
```

**New file:** `src/main/java/com/fvd/api/dto/meta/PaginationMeta.java`

```java
@RegisterForReflection
@NoArgsConstructor
@AllArgsConstructor
public class PaginationMeta {

    public int defaultLimit;
    public int maxLimit;
    public int defaultOffset;

}
```

### R4: Expected JSON Response Schema

`GET /api/meta` returns:

```json
{
    "apiInfo": {
        "name": "Quarkus Documentation API",
        "description": "REST API for searching and retrieving Quarkus framework documentation. Indexes docs from quarkusio.github.io and quarkiverse extensions. Optimized for AI agent consumption via MCP server.",
        "defaultVersion": "main"
    },
    "endpoints": [
        {
            "method": "GET",
            "path": "/api/meta",
            "summary": "API capabilities and self-discovery",
            "description": "Returns a machine-readable description of all API endpoints, parameters, constraints, search syntax, and available filter values. Call this first to understand the API.",
            "parameters": []
        },
        {
            "method": "GET",
            "path": "/api/catalog",
            "summary": "List catalog information",
            "description": "Returns lists of available subjects (with doc counts), extensions (with doc counts and keywords), and cached versions for a given version. Use this to discover valid filter values before searching.",
            "parameters": [
                {
                    "name": "version",
                    "type": "string",
                    "required": false,
                    "defaultValue": "main",
                    "description": "Quarkus version branch or tag. Defaults to 'main' if omitted.",
                    "constraints": {
                        "pattern": "[a-zA-Z0-9._/-]+",
                        "allowedValues": null
                    }
                }
            ]
        },
        {
            "method": "GET",
            "path": "/api/search",
            "summary": "Quick discovery search",
            "description": "Returns lightweight document references (path, title, subject, extension, score, matchedKeywords, snippet) without full content. Best for initial discovery. Use the returned path with /api/documents?path=... to fetch full content.",
            "parameters": [
                {
                    "name": "version",
                    "type": "string",
                    "required": false,
                    "defaultValue": "main",
                    "description": "Quarkus version branch or tag.",
                    "constraints": {
                        "pattern": "[a-zA-Z0-9._/-]+"
                    }
                },
                {
                    "name": "keywords",
                    "type": "string",
                    "required": true,
                    "defaultValue": null,
                    "description": "Space-separated search keywords. Stop words are automatically filtered.",
                    "constraints": null
                },
                {
                    "name": "subject",
                    "type": "string",
                    "required": false,
                    "defaultValue": null,
                    "description": "Filter results by subject category. Use /api/catalog to list valid subjects.",
                    "constraints": null
                },
                {
                    "name": "extension",
                    "type": "string",
                    "required": false,
                    "defaultValue": null,
                    "description": "Filter results by extension name (e.g., 'quarkus-core'). Use /api/catalog to list valid extensions.",
                    "constraints": null
                },
                {
                    "name": "limit",
                    "type": "integer",
                    "required": false,
                    "defaultValue": "20",
                    "description": "Maximum number of results to return.",
                    "constraints": { "min": 1, "max": 100 }
                },
                {
                    "name": "offset",
                    "type": "integer",
                    "required": false,
                    "defaultValue": "0",
                    "description": "Number of results to skip for pagination.",
                    "constraints": { "min": 0 }
                }
            ]
        },
        {
            "method": "GET",
            "path": "/api/documents",
            "summary": "Get document by path or search by keywords",
            "description": "Dual-mode endpoint. Path mode: provide 'path' to get a single document with full structured content (sections, code blocks). Search mode: provide 'keywords' to search documents with scores. If both provided, path takes precedence. Use 'brief=true' in search mode for metadata-only results (no sections/codeBlocks). Returns 400 if neither path nor keywords is provided.",
            "parameters": [
                {
                    "name": "version",
                    "type": "string",
                    "required": false,
                    "defaultValue": "main",
                    "description": "Quarkus version branch or tag.",
                    "constraints": {
                        "pattern": "[a-zA-Z0-9._/-]+"
                    }
                },
                {
                    "name": "path",
                    "type": "string",
                    "required": false,
                    "defaultValue": null,
                    "description": "Document path relative to docs directory. If provided, returns single document with full content. Either 'path' or 'keywords' must be provided.",
                    "constraints": null
                },
                {
                    "name": "keywords",
                    "type": "string",
                    "required": false,
                    "defaultValue": null,
                    "description": "Space-separated search keywords for document search. Either 'keywords' or 'path' must be provided.",
                    "constraints": null
                },
                {
                    "name": "subject",
                    "type": "string",
                    "required": false,
                    "defaultValue": null,
                    "description": "Subject filter (search mode only).",
                    "constraints": null
                },
                {
                    "name": "extension",
                    "type": "string",
                    "required": false,
                    "defaultValue": null,
                    "description": "Extension filter (search mode only).",
                    "constraints": null
                },
                {
                    "name": "limit",
                    "type": "integer",
                    "required": false,
                    "defaultValue": "20",
                    "description": "Maximum number of results (search mode only).",
                    "constraints": { "min": 1, "max": 100 }
                },
                {
                    "name": "offset",
                    "type": "integer",
                    "required": false,
                    "defaultValue": "0",
                    "description": "Pagination offset (search mode only).",
                    "constraints": { "min": 0 }
                },
                {
                    "name": "brief",
                    "type": "boolean",
                    "required": false,
                    "defaultValue": "false",
                    "description": "When true, returns only metadata without sections and codeBlocks (search mode only, ignored in path mode).",
                    "constraints": null
                }
            ]
        },
        {
            "method": "GET",
            "path": "/api/code-samples",
            "summary": "Search code samples by keywords",
            "description": "Searches for code examples matching keywords. Returns code samples with full content, language, context (surrounding section title), and relevance scores. Results sorted by score descending.",
            "parameters": [
                {
                    "name": "version",
                    "type": "string",
                    "required": false,
                    "defaultValue": "main",
                    "description": "Quarkus version branch or tag.",
                    "constraints": {
                        "pattern": "[a-zA-Z0-9._/-]+"
                    }
                },
                {
                    "name": "keywords",
                    "type": "string",
                    "required": true,
                    "defaultValue": null,
                    "description": "Space-separated search keywords.",
                    "constraints": null
                },
                {
                    "name": "language",
                    "type": "string",
                    "required": false,
                    "defaultValue": null,
                    "description": "Programming language filter (e.g., 'java', 'properties', 'yaml').",
                    "constraints": null
                },
                {
                    "name": "subject",
                    "type": "string",
                    "required": false,
                    "defaultValue": null,
                    "description": "Subject filter.",
                    "constraints": null
                },
                {
                    "name": "extension",
                    "type": "string",
                    "required": false,
                    "defaultValue": null,
                    "description": "Extension filter.",
                    "constraints": null
                },
                {
                    "name": "limit",
                    "type": "integer",
                    "required": false,
                    "defaultValue": "20",
                    "description": "Maximum number of results to return.",
                    "constraints": { "min": 1, "max": 100 }
                },
                {
                    "name": "offset",
                    "type": "integer",
                    "required": false,
                    "defaultValue": "0",
                    "description": "Pagination offset.",
                    "constraints": { "min": 0 }
                }
            ]
        },
        {
            "method": "GET",
            "path": "/api/search/syntax",
            "summary": "Search syntax documentation",
            "description": "Returns comprehensive, machine-readable documentation of search query syntax, supported features, scoring behavior, stemming examples, stop words, and query examples. Call this to understand how to construct effective search queries.",
            "parameters": []
        },
        {
            "method": "POST",
            "path": "/api/documents/batch",
            "summary": "Batch document retrieval",
            "description": "Retrieve multiple documents by path in a single request. Accepts a JSON body with a list of paths (max 10). Returns partial success — found documents in 'documents' array, errors in 'errors' array. Use brief=true for metadata-only results.",
            "parameters": [
                {
                    "name": "body",
                    "type": "object",
                    "required": true,
                    "defaultValue": null,
                    "description": "JSON body with 'paths' (required, list of document paths), 'version' (optional, default 'main'), 'brief' (optional, default false)",
                    "constraints": { "max": 10 }
                }
            ]
        },
        {
            "method": "GET",
            "path": "/api/documents/related",
            "summary": "Find related documents",
            "description": "Returns a ranked list of documents similar to a given source document, computed from shared keyword overlap. Results include similarity scores and shared keywords. Useful for graph-like navigation across the documentation corpus.",
            "parameters": [
                {
                    "name": "version",
                    "type": "string",
                    "required": false,
                    "defaultValue": "main",
                    "description": "Quarkus version branch or tag.",
                    "constraints": { "pattern": "[a-zA-Z0-9._/-]+" }
                },
                {
                    "name": "path",
                    "type": "string",
                    "required": true,
                    "defaultValue": null,
                    "description": "Path of the source document to find related documents for.",
                    "constraints": null
                },
                {
                    "name": "subject",
                    "type": "string",
                    "required": false,
                    "defaultValue": null,
                    "description": "Filter related documents by subject category.",
                    "constraints": null
                },
                {
                    "name": "extension",
                    "type": "string",
                    "required": false,
                    "defaultValue": null,
                    "description": "Filter related documents by extension name.",
                    "constraints": null
                },
                {
                    "name": "limit",
                    "type": "integer",
                    "required": false,
                    "defaultValue": "5",
                    "description": "Maximum number of related documents to return.",
                    "constraints": { "min": 1, "max": 20 }
                }
            ]
        }
    ],
    "searchSyntax": {
        "keywordSeparator": "space",
        "detailedSyntaxEndpoint": "/api/search/syntax",
        "supportedFeatures": [
            "Space-separated keywords (e.g., 'security authentication')",
            "Stemming (e.g., 'configuring' matches 'configuration')",
            "Prefix matching (e.g., 'secur' matches 'security')",
            "Stop word filtering (common words like 'the', 'and' are removed)",
            "Case-insensitive matching"
        ],
        "unsupportedFeatures": [
            "Phrase search (quoted strings)",
            "Boolean operators (AND, OR, NOT)",
            "Wildcards (* or ?)",
            "Field-specific queries (field:value)",
            "Regular expressions"
        ],
        "tips": [
            "Use 2-3 specific keywords for best results",
            "Prefer nouns over verbs (e.g., 'security' over 'securing')",
            "Use /api/search for quick discovery, then /api/documents?path=... for full content",
            "Use brief=true on /api/documents search to avoid downloading full document content",
            "Check /api/catalog for valid subject and extension filter values",
            "For comprehensive search syntax documentation including stemming examples, scoring details, and stop words, call GET /api/search/syntax"
        ]
    },
    "filters": {
        "subjects": ["cloud", "core-concepts", "data-persistence", "extensions", "getting-started", "messaging", "misc", "observability", "rest-apis", "security", "testing", "tooling"],
        "versions": ["main", "3.27"],
        "extensionsNote": "Extensions are version-specific and can be numerous. Use GET /api/catalog?version=X to list extensions for a specific version."
    },
    "pagination": {
        "defaultLimit": 20,
        "maxLimit": 100,
        "defaultOffset": 0
    }
}
```

### R5: Caching Strategy

The meta response mixes static (endpoint definitions, search syntax) and dynamic (subjects, versions) data. Options:

**Chosen approach: HTTP-level caching with `Cache-Control`**

- `Cache-Control: public, max-age=3600` (1 hour)
- No in-memory caching in `MetaService` — the response is cheap to build (two `Set`/`List` lookups from already-cached data in `SubjectDeriver` and `CacheService`)
- The response changes only when: (a) new versions are cached, or (b) subject configuration changes (both rare)
- 1 hour is a good balance: short enough to pick up new versions after cache refresh (6h interval), long enough to avoid redundant requests from the same agent session

**Alternative considered but rejected: ETag-based caching**

ETag would require computing a hash of the response content and handling `If-None-Match` — more complexity for minimal benefit since the response is small (~3KB) and cheap to compute.

### R6: Configuration

**File:** `src/main/resources/application.properties`

No new configuration required. The meta endpoint uses existing constants:
- `SearchConstants.DEFAULT_LIMIT` (20)
- `SearchConstants.MAX_LIMIT` (100)
- `SearchConstants.DEFAULT_OFFSET` (0)
- `InputValidator.DEFAULT_VERSION` ("main")
- `SubjectDeriver.getValidSubjectNames()` (from existing config)
- `CacheService.listCachedVersions()` (from filesystem)

If a configurable cache duration is desired in the future, add:
```properties
app.meta.cache-max-age=3600
```

But this is not needed for the initial implementation — hardcode 3600 seconds.

---

## Implementation Notes

### Endpoint Description Quality

The descriptions in `EndpointMeta` are **semantic** — they tell the AI agent *how* to use the endpoint, not just what it does. For example:
- `/api/search` description mentions "Use the returned path with /api/documents?path=..." — guiding the two-step discovery pattern
- `/api/documents` description explains the dual-mode behavior and `brief` parameter
- `/api/catalog` description says "Use this to discover valid filter values before searching"

This semantic richness is what makes `/api/meta` valuable over raw OpenAPI.

### Static vs Dynamic Data

| Data | Source | Changes When |
|------|--------|--------------|
| Endpoint definitions | Hardcoded in `MetaService` | Code changes (new release) |
| Parameter definitions | Hardcoded in `MetaService` | Code changes (new release) |
| Search syntax features | Hardcoded in `MetaService` | Code changes (new release) |
| Pagination constraints | `SearchConstants` | Code changes (rare) |
| Default version | `InputValidator.DEFAULT_VERSION` | Code changes (never expected) |
| Valid subjects | `SubjectDeriver.getValidSubjectNames()` | Config changes (rare) |
| Cached versions | `CacheService.listCachedVersions()` | Cache warmup/refresh (every 6h) |

### Why Extensions Are Excluded from Filters

Extensions are:
1. **Version-specific** — different versions may have different extensions
2. **Numerous** — could be 50+ extensions, bloating the meta response
3. **Already available** — `GET /api/catalog?version=X` returns extensions with doc counts and keywords

The `filters.extensionsNote` field explains this to the AI agent.

### Package Location for DTOs

The meta-specific DTOs (`EndpointMeta`, `ParameterMeta`, etc.) live in a `meta` subpackage under `api/dto`:

```
com.fvd.api.dto.meta.ApiInfo
com.fvd.api.dto.meta.EndpointMeta
com.fvd.api.dto.meta.ParameterMeta
com.fvd.api.dto.meta.ConstraintsMeta
com.fvd.api.dto.meta.SearchSyntaxMeta
com.fvd.api.dto.meta.FiltersMeta
com.fvd.api.dto.meta.PaginationMeta
```

The top-level `MetaResponse` stays in `com.fvd.api.dto` alongside `CatalogResponse`, `QuickSearchResponse`, etc.

### No Version Parameter

Unlike other endpoints, `/api/meta` does not accept a `version` parameter. It describes the API as a whole:
- Subjects are version-independent (derived from configuration, not from indexed data)
- Versions list shows all cached versions
- Extensions are explicitly delegated to `/api/catalog?version=X`

---

## Tasks

- [ ] Create `MetaResponse` DTO in `com.fvd.api.dto`
- [ ] Create meta sub-DTOs in `com.fvd.api.dto.meta` package: `ApiInfo`, `EndpointMeta`, `ParameterMeta`, `ConstraintsMeta`, `SearchSyntaxMeta`, `FiltersMeta`, `PaginationMeta`
- [ ] Create `MetaService` in `com.fvd.api.services` with `getCapabilities()` method that assembles the full response
- [ ] Implement static endpoint/parameter builders in `MetaService` (one private method per endpoint)
- [ ] Implement dynamic filter assembly using `SubjectDeriver.getValidSubjectNames()` and `CacheService.listCachedVersions()`
- [ ] Implement search syntax and pagination assembly using `SearchConstants` and `InputValidator` constants
- [ ] Create `MetaResource` in `com.fvd.api.resources` with `GET /api/meta` endpoint
- [ ] Add `Cache-Control: public, max-age=3600` header to the response
- [ ] Add OpenAPI annotations (`@Operation`, `@APIResponse`, `@Tag`) to `MetaResource`
- [ ] Add unit tests for `MetaService`:
    - Response contains all 8 endpoints (meta, catalog, search, documents, code-samples, search/syntax, documents/batch, documents/related)
    - Each endpoint has correct method, path, and non-empty description
    - Search endpoint has `keywords` parameter marked as required
    - Documents endpoint has `path`, `keywords`, and `brief` parameters
    - Code-samples endpoint has `language` parameter
    - Pagination values match `SearchConstants` (DEFAULT_LIMIT=20, MAX_LIMIT=100, DEFAULT_OFFSET=0)
    - `searchSyntax.supportedFeatures` is non-empty
    - `searchSyntax.unsupportedFeatures` is non-empty
    - `filters.subjects` matches `SubjectDeriver.getValidSubjectNames()` (sorted)
    - `filters.versions` matches `CacheService.listCachedVersions()`
    - `apiInfo.defaultVersion` equals "main"
- [ ] Add integration tests for `MetaResource`:
    - `GET /api/meta` returns 200
    - Response has `Cache-Control` header containing `max-age=3600`
    - Response body has non-null `apiInfo`, `endpoints`, `searchSyntax`, `filters`, `pagination`
    - `endpoints` array has size 8
    - `endpoints[*].path` contains `/api/meta`, `/api/catalog`, `/api/search`, `/api/documents`, `/api/code-samples`, `/api/search/syntax`, `/api/documents/batch`, `/api/documents/related`
    - `filters.subjects` is non-empty
    - `filters.versions` contains "main"
    - `pagination.defaultLimit` equals 20
    - `pagination.maxLimit` equals 100
- [ ] Run `./gradlew test` — all tests pass

---

## Acceptance Criteria

1. `GET /api/meta` returns 200 with a valid JSON response matching the `MetaResponse` schema
2. Response contains descriptions for all 8 endpoints: `/api/meta`, `/api/catalog`, `/api/search`, `/api/documents`, `/api/code-samples`, `/api/search/syntax`, `/api/documents/batch`, `/api/documents/related`
3. Each endpoint description includes all its parameters with `name`, `type`, `required`, `defaultValue`, and `description`
4. Parameters with numeric constraints (`limit`, `offset`) include `constraints.min` and/or `constraints.max`
5. `searchSyntax` includes both `supportedFeatures` and `unsupportedFeatures` lists
6. `filters.subjects` contains all valid subject names from `SubjectDeriver` (sorted alphabetically)
7. `filters.versions` contains all currently cached versions from `CacheService`
8. `pagination` values match `SearchConstants` (defaultLimit=20, maxLimit=100, defaultOffset=0)
9. `apiInfo.defaultVersion` equals "main"
10. Response includes `Cache-Control: public, max-age=3600` header
11. Endpoint has OpenAPI annotations (`@Operation`, `@APIResponse`, `@Tag`)
12. `./gradlew test` passes with zero failures

---

## Test Scenarios

### Unit Tests (`MetaServiceTest`)

| # | Test Method | Description |
|---|-------------|-------------|
| 1 | `shouldReturnAllEndpoints` | Verify `endpoints` list contains exactly 8 entries with correct paths |
| 2 | `shouldReturnCorrectMethodAndPathForEachEndpoint` | Verify each endpoint has `method=GET` and the expected path |
| 3 | `shouldReturnNonEmptyDescriptionsForAllEndpoints` | Verify all endpoints have non-blank `summary` and `description` |
| 4 | `shouldMarkKeywordsAsRequiredOnSearchEndpoint` | Verify `/api/search` has `keywords` parameter with `required=true` |
| 5 | `shouldMarkKeywordsAsRequiredOnCodeSamplesEndpoint` | Verify `/api/code-samples` has `keywords` parameter with `required=true` |
| 6 | `shouldIncludeAllDocumentEndpointParameters` | Verify `/api/documents` includes `path`, `keywords`, `subject`, `extension`, `limit`, `offset`, `brief` |
| 7 | `shouldIncludeLanguageParameterOnCodeSamples` | Verify `/api/code-samples` includes `language` parameter |
| 8 | `shouldReturnCorrectPaginationConstraints` | Verify `pagination.defaultLimit=20`, `pagination.maxLimit=100`, `pagination.defaultOffset=0` |
| 9 | `shouldReturnSupportedSearchFeatures` | Verify `searchSyntax.supportedFeatures` includes stemming, prefix matching entries |
| 10 | `shouldReturnUnsupportedSearchFeatures` | Verify `searchSyntax.unsupportedFeatures` includes phrases, boolean operators entries |
| 11 | `shouldReturnSearchTips` | Verify `searchSyntax.tips` is non-empty |
| 12 | `shouldReturnSubjectsFromSubjectDeriver` | Mock `SubjectDeriver.getValidSubjectNames()` and verify `filters.subjects` matches |
| 13 | `shouldReturnVersionsFromCacheService` | Mock `CacheService.listCachedVersions()` and verify `filters.versions` matches |
| 14 | `shouldReturnDefaultVersionAsMain` | Verify `apiInfo.defaultVersion` equals "main" |
| 15 | `shouldReturnExtensionsNote` | Verify `filters.extensionsNote` is non-blank |
| 16 | `shouldReturnLimitConstraintsOnSearchEndpoints` | Verify `limit` parameter on `/api/search` has `constraints.min=1, constraints.max=100` |
| 17 | `shouldReturnOffsetConstraintsOnSearchEndpoints` | Verify `offset` parameter on `/api/search` has `constraints.min=0` |
| 18 | `shouldReturnVersionPatternConstraint` | Verify `version` parameter has `constraints.pattern` matching the regex `[a-zA-Z0-9._/-]+` |
| 19 | `shouldReturnMetaEndpointWithEmptyParameters` | Verify `/api/meta` endpoint has an empty parameters list |

### Integration Tests (`MetaResourceTest`)

| # | Test Method | Description |
|---|-------------|-------------|
| 1 | `testMetaEndpointReturns200` | `GET /api/meta` returns 200 |
| 2 | `testMetaEndpointReturnsCacheControlHeader` | Response has `Cache-Control` header with `max-age=3600` |
| 3 | `testMetaEndpointReturnsAllEndpoints` | `endpoints.size()` equals 8 |
| 4 | `testMetaEndpointContainsApiInfo` | `apiInfo.name` is not null, `apiInfo.defaultVersion` equals "main" |
| 5 | `testMetaEndpointContainsSearchSyntax` | `searchSyntax.supportedFeatures.size()` > 0, `searchSyntax.unsupportedFeatures.size()` > 0 |
| 6 | `testMetaEndpointContainsSubjects` | `filters.subjects.size()` > 0, `filters.subjects` contains "security" |
| 7 | `testMetaEndpointContainsVersions` | `filters.versions` contains "main" |
| 8 | `testMetaEndpointContainsPagination` | `pagination.defaultLimit` equals 20, `pagination.maxLimit` equals 100 |
| 9 | `testMetaEndpointContainsDocumentsEndpointWithBriefParam` | Find `/api/documents` endpoint, verify it has a `brief` parameter |
| 10 | `testMetaEndpointContainsCodeSamplesWithLanguageParam` | Find `/api/code-samples` endpoint, verify it has a `language` parameter |

---

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Meta response goes stale when new endpoints or parameters are added | Medium | Medium | Meta is hardcoded — developer must update `MetaService` when API surface changes. Add a comment in each resource class: "If you modify parameters, update MetaService." Consider a future feature to generate meta from reflection/annotations. |
| `filters.subjects` or `filters.versions` empty on cold start (before cache warmup) | Low | Low | Subjects come from config/defaults (always populated). Versions: `main` is always available via `DEFAULT_VERSION`. `listCachedVersions()` may return empty but `main` is always valid. |
| Response schema is too verbose for some AI agents | Low | Low | Response is ~3KB — much smaller than OpenAPI. Structure is flat and predictable. |
| Cache-Control too long (1h) causes stale version list after cache refresh | Low | Low | Versions only change when explicitly cached (startup + 6h refresh). 1h max-age is a reasonable trade-off. Agent can always call `/api/catalog` for authoritative filter values. |
| Adding a `meta` subpackage under `api/dto` breaks convention | Low | Low | Convention says DTOs go in `api/dto`. A subpackage keeps the 7 meta-specific DTOs organized without cluttering the main `dto` package. Other packages like `subject` already use nested structures. |
| Hardcoded endpoint descriptions may drift from actual `@Operation` annotations | Medium | Low | Descriptions are intentionally different — meta descriptions are for AI agents (semantic/usage-oriented), OpenAPI descriptions are for developer tooling. But core facts (parameters, constraints) must stay in sync. |

---

## Estimated Effort

| Task | Hours |
|------|-------|
| Create meta DTOs (8 classes) | 1.0 |
| Implement `MetaService` with endpoint/parameter builders | 2.0 |
| Create `MetaResource` with OpenAPI annotations and Cache-Control | 0.5 |
| Unit tests for `MetaService` (19 test methods) | 2.0 |
| Integration tests for `MetaResource` (10 test methods) | 1.5 |
| Run full test suite and fix issues | 0.5 |
| **Total** | **~7.5 hours** |

---

## Files Modified

### New Production Code (10 files)
- `src/main/java/com/fvd/api/resources/MetaResource.java` — JAX-RS endpoint for `GET /api/meta`
- `src/main/java/com/fvd/api/services/MetaService.java` — Service assembling the capabilities response
- `src/main/java/com/fvd/api/dto/MetaResponse.java` — Top-level response DTO
- `src/main/java/com/fvd/api/dto/meta/ApiInfo.java` — API name, description, default version
- `src/main/java/com/fvd/api/dto/meta/EndpointMeta.java` — Endpoint method, path, description, parameters
- `src/main/java/com/fvd/api/dto/meta/ParameterMeta.java` — Parameter name, type, required, default, constraints
- `src/main/java/com/fvd/api/dto/meta/ConstraintsMeta.java` — Min, max, pattern, allowed values
- `src/main/java/com/fvd/api/dto/meta/SearchSyntaxMeta.java` — Supported/unsupported search features
- `src/main/java/com/fvd/api/dto/meta/FiltersMeta.java` — Valid subjects, versions, extensions note
- `src/main/java/com/fvd/api/dto/meta/PaginationMeta.java` — Limit/offset defaults and maximums

### Unchanged Production Files
- `src/main/java/com/fvd/api/resources/CatalogResource.java` — no changes
- `src/main/java/com/fvd/api/resources/SearchResource.java` — no changes
- `src/main/java/com/fvd/api/resources/DocumentResource.java` — no changes
- `src/main/java/com/fvd/api/resources/CodeSampleResource.java` — no changes
- `src/main/java/com/fvd/common/validators/InputValidator.java` — no changes (constants referenced read-only)
- `src/main/java/com/fvd/common/SearchConstants.java` — no changes (constants referenced read-only)
- `src/main/java/com/fvd/subject/services/SubjectDeriver.java` — no changes (`getValidSubjectNames()` already exists)
- `src/main/java/com/fvd/cache/services/CacheService.java` — no changes (`listCachedVersions()` already exists)
- `src/main/resources/application.properties` — no changes

### New Test Code (2 files)
- `src/test/java/com/fvd/api/services/MetaServiceTest.java` — Unit tests for `MetaService`
- `src/test/java/com/fvd/api/resources/MetaResourceTest.java` — Integration tests for `GET /api/meta`

---

END OF FILE
