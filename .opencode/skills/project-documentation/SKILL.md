---
name: project-documentation
description: Project-specific documentation standards for code, README, API docs, and changelog
---

# Project Documentation Guidelines

---

## Code Documentation

- Write **self-documenting code** — prefer clear naming and small methods over excessive comments.
- Add Javadoc or inline comments only when the intent is **non-obvious**.
- Do not create standalone documentation files (`.md`, `README`, etc.) unless explicitly requested.
- Comment non-obvious *why* decisions: workarounds, business rules, performance constraints.
- Do **not** add noise comments that repeat what the code already says.

```java
/**
 * Atomically replaces all doc chunks for a version: deletes existing chunks
 * and inserts new ones within a single transaction.
 */
public void replaceVersion(String version, List<DocChunk> chunks) { ... }
```

---

## API Documentation

All REST endpoints **must** be documented via **SmallRye OpenAPI** annotations.

### Required Annotations Per Endpoint

| Annotation | Placement | Purpose |
|-----------|-----------|---------|
| `@Tag(name, description)` | Class | Groups endpoints in Swagger UI |
| `@Operation(summary, description)` | Method | Describes the endpoint |
| `@APIResponse(responseCode, description, content)` | Method (one per status) | Documents each HTTP status returned |
| `@Parameter(description, required, example, schema)` | Each query param | Documents inputs |
| `@Schema(defaultValue = "main")` | `version` param schema | Documents the default value |
| `@Schema(description)` | DTO fields | Documents response structure |

### Endpoint Annotation Pattern

```java
@GET
@Path("/example")
@Operation(
        summary = "Short summary",
        description = "Full description including filtering, sorting, and defaults."
)
@APIResponse(
        responseCode = "200",
        description = "Results returned successfully",
        content = @Content(schema = @Schema(implementation = MyResponse.class))
)
@APIResponse(
        responseCode = "400",
        description = "Invalid input parameters",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))
)
public MyResponse example(
        @Parameter(description = "Quarkus version branch or tag. Defaults to 'main' if omitted.",
                required = false, example = "main", schema = @Schema(defaultValue = "main"))
        @QueryParam("version") String version,
        @Parameter(description = "Description of the parameter", required = true, example = "example-value")
        @QueryParam("param") String param) {
    // ...
}
```

### OpenAPI UI

- Swagger UI is available at `/q/swagger-ui` in dev mode.
- OpenAPI spec available at `/q/openapi`.

---

## Response Format Standards

| Response Type | DTO | Key Fields |
|---------------|-----|-----------|
| Search/listing | Extends `PaginatedResponse<T>` | `results`, `totalCount`, `returnedCount`, `offset`, `limit`, `hasMore` |
| Chunk search | `ChunkSearchResponse` | `results` (List of `ChunkResult`), `total`, `limit`, `offset` |
| Document | `DocumentResponse` | `path`, `title`, `description`, `subject`, `extension`, `matchedKeywords`, `score`, `sections`, `codeBlocks` |
| Document search | `DocumentSearchResponse` | Extends `PaginatedResponse<DocumentResponse>` + optional `warning` field |
| Batch document | `BatchDocumentResponse` | `documents`, `errors`, `requestedCount`, `retrievedCount`, `errorCount` |
| Catalog | `CatalogResponse` | `subjects`, `extensions`, `versions` |
| Error | `ProblemDetail` | `type`, `title`, `status`, `detail`, `instance` (RFC 9457) |
| Status | `StatusResponse` | `ready`, `cachedVersions`, `warmupProgress` |
| Related docs | `RelatedDocumentResponse` | `RelatedDocumentRef` items with `path`, `title`, `similarityScore`, `sharedKeywords` |

- All API responses are JSON (`MediaType.APPLICATION_JSON`).
- Item-level DTOs use `@JsonFilter("fieldSelector")` for dynamic field selection via the `fields` query parameter.
- DTOs with nullable fields use `@JsonInclude(NON_NULL)` so null fields are omitted from JSON output.
- Error responses use `ProblemDetail` (RFC 9457): `type`, `title`, `status`, `detail`, `instance`.

---

## Configuration Documentation

All config keys and their defaults must be documented in `application.properties` with inline comments. Group related keys by prefix.

### Core Configuration Reference

| Key | Default | Description |
|-----|---------|-------------|
| `app.cache.dir` | `.cache` | Local cache directory |
| `app.github.owner` | `quarkusio` | GitHub org for docs repo |
| `app.github.repo` | `quarkusio.github.io` | GitHub docs repository name |
| `app.github.branch` | `main` | Branch to fetch from |
| `app.versions` | `main` | Versions to cache on startup |
| `app.refresh.interval` | `6h` | Cache refresh interval |
| `app.document-cache.enabled` | `true` | Enable in-memory document parse cache |
| `app.batch.max-size` | `10` | Maximum document paths per batch request |
| `app.quarkiverse.enabled` | `true` | Enable Quarkiverse ingestion |
| `app.quarkiverse.playbook-repo` | `quarkiverse/quarkiverse-docs` | Antora playbook repo |
| `app.quarkiverse.playbook-branch` | `main` | Playbook branch |
| `app.quarkiverse.download-concurrency` | `4` | Max concurrent extension downloads |
| `app.cache.http.max-age.versioned` | `3600` | HTTP `Cache-Control` max-age for versioned content (seconds) |
| `app.cache.http.max-age.main` | `900` | HTTP `Cache-Control` max-age for main/latest content (seconds) |
| `app.cache.http.max-age.catalog` | `1800` | HTTP `Cache-Control` max-age for catalog responses (seconds) |

### Search Tuning Reference

| Key | Default | Description |
|-----|---------|-------------|
| `search.index.min-keyword-score` | `2` | Minimum keyword score to include in index |
| `search.boost.annotation-boost` | `10` | Score boost for annotation matches during indexing |
| `search.boost.annotation-packages` | `io.quarkus,jakarta,...` | Annotation packages to boost during indexing |
| `search.snippet.highlight-enabled` | `true` | Enable snippet highlighting in search results |
| `search.related.default-limit` | `5` | Default number of related documents to return |
| `search.related.max-limit` | `20` | Maximum allowed limit for related documents |
| `search.related.min-similarity` | `0.05` | Minimum similarity score to include a related document |
| `search.related.max-shared-keywords` | `10` | Maximum shared keywords to consider for similarity |

---

## README Format

The `README.md` documents:
- Project overview and purpose
- Architecture diagram (layered: Resource → Service → Store)
- API endpoints table
- Configuration reference
- Quick start instructions (`./gradlew quarkusDev`)

---

## Feature Planning

- Feature planning documents are tracked under `features/`.
- Completed features: `features/done/`
- Pending features: `features/todo/`

---

## Project Documentation Files

| File | Purpose |
|------|---------|
| `README.md` | Project overview, architecture, API endpoints, config reference, quick start |
| `AGENTS.md` | Entry point for AI agents — references `.opencode/skills/` for detailed guidelines |
| `application.properties` | All runtime configuration with inline comments |
| `.project-guidelines-for-ai/` | Detailed authoritative guidelines by domain (coding, building, testing, security, documentation) |
| `.opencode/skills/` | Structured skill files for AI coding agents |
| `.code-examples-for-ai/` | Real code pattern examples for AI reference |
