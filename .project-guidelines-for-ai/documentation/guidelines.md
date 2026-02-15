# Documentation Guidelines

## Project Context

Quarkus REST API (Java 21, Gradle wrapper) that caches and indexes Quarkus documentation from GitHub. SQLite-backed indexes. Lombok enabled. API documented via SmallRye OpenAPI with Swagger UI.

## API Documentation

- All REST endpoints must be documented via **SmallRye OpenAPI** annotations.
- Use `@Parameter` with `description`, `required`, and `example` on every query parameter.
- Use `@Schema(defaultValue = ...)` to document default values (e.g., version defaults to `main`).
- The interactive Swagger UI is available at `/q/swagger-ui` in dev mode.

### Endpoint Annotation Pattern

```java
@GET
@Path("/example")
public SomeResponse example(
        @Parameter(description = "Quarkus version branch or tag. Defaults to 'main' if omitted.",
                required = false, example = "main", schema = @Schema(defaultValue = "main"))
        @QueryParam("version") String version,
        @Parameter(description = "Description of the parameter", required = true, example = "example-value")
        @QueryParam("param") String param,
        @Parameter(description = "Comma-separated list of fields to include in each result item. "
                + "When omitted, all fields are returned. "
                + "Invalid field names return 400 with the list of available fields.",
                required = false, example = "title,path,score")
        @QueryParam("fields") String fields) {
    // ...
}
```

## Response Format Standards

- All API responses are JSON (`MediaType.APPLICATION_JSON`).
- All item-level DTOs are annotated with `@JsonFilter("fieldSelector")` to support dynamic field selection via the `fields` query parameter. A default `serializeAll()` filter is registered at startup so responses are unaffected when `fields` is omitted. Envelope fields (`results`, `totalCount`, `returnedCount`) are never filtered.
- Error responses use `ProblemDetail` with RFC 9457 fields: `type`, `title`, `status`, `detail`, `instance`.
- Search/listing responses extend `PaginatedResponse<T>` with `results`, `totalCount`, `limit`, `offset`, `queriedKeywords`, and `searchTimeMs`.
- Document responses use `DocumentResponse` with `path`, `title`, `description`, `subject`, `extension`, `matchedKeywords`, `score`, `sections`, and `codeBlocks`.
- Catalog responses use `CatalogResponse` with `subjects`, `extensions`, and `versions`.
- Quick search responses use `QuickSearchResponse` with lightweight `SearchResultRef` items.
- Code sample responses use `CodeSampleSearchResponse` with `CodeSampleResult` items.
- Search syntax responses use `SearchSyntaxResponse` with static documentation of supported search operators, examples, and tips.
- Meta responses use `MetaResponse` with `apiInfo`, `endpoints`, `searchSyntax`, `filters`, `pagination`.
- Batch document responses use `BatchDocumentResponse` with `documents`, `errors`, `requestedCount`, `retrievedCount`, `errorCount`.
- Related document responses use `RelatedDocumentResponse` with `RelatedDocumentRef` items containing `path`, `title`, `description`, `subject`, `extension`, `similarityScore`, and `sharedKeywords`.

## Code Documentation

- Write self-documenting code -- prefer clear naming and small methods over excessive comments.
- Add Javadoc or inline comments only when the intent is non-obvious.
- Do not create standalone documentation files (`.md`, `README`, etc.) unless explicitly requested.

## Configuration Documentation

- All config keys and their defaults should be documented in `application.properties` with inline comments.
- Group related config keys by prefix (`app.*`, `search.*`, `quarkus.*`).

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
| `app.batch.max-size` | `10` | Maximum number of document paths per batch request |
| `app.quarkiverse.enabled` | `true` | Enable quarkiverse ingestion |
| `app.quarkiverse.playbook-repo` | `quarkiverse/quarkiverse-docs` | Antora playbook repo |
| `app.quarkiverse.playbook-branch` | `main` | Playbook branch |
| `app.quarkiverse.download-concurrency` | `4` | Max concurrent extension downloads |

### Search Tuning Reference

| Key | Default | Description |
|-----|---------|-------------|
| `search.boost.filename-boost` | `10` | Score boost for filename matches |
| `search.boost.title-boost` | `5` | Score boost for document title matches |
| `search.boost.import-boost` | `5` | Score boost for import statement matches |
| `search.boost.section-title-boost` | `5` | Score boost for section title matches |
| `search.boost.multi-keyword-boost` | `1.5` | Multiplier when multiple keywords match |
| `search.boost.prefix-match-multiplier` | `0.8` | Discount factor for prefix matches |
| `search.fuzzy.levenshtein-weight` | `0.4` | Weight for Levenshtein similarity |
| `search.fuzzy.containment-weight` | `0.35` | Weight for substring containment |
| `search.fuzzy.word-overlap-weight` | `0.25` | Weight for word overlap |
| `search.fuzzy.default-threshold` | `0.3` | Minimum fuzzy match score |
| `search.index.min-keyword-score` | `2` | Minimum keyword score for indexing |
| `search.index.min-token-length` | `3` | Minimum token length for indexing |
| `search.snippet.context-size` | `100` | Characters of context around matches |
| `search.related.default-limit` | `5` | Default number of related documents to return |
| `search.related.max-limit` | `20` | Maximum allowed limit for related documents |
| `search.related.min-similarity` | `0.05` | Minimum similarity score to include a related document |
| `search.related.max-shared-keywords` | `10` | Maximum shared keywords to consider for similarity |
| `search.annotation-boost` | _(unset)_ | Score boost for annotation matches |
| `search.annotation-packages` | _(unset)_ | Annotation packages to boost during indexing |

## Feature Planning

- Feature planning documents are tracked under `features/`.
- Completed features: `features/done/`.
- Pending features: `features/todo/`.

## Project Documentation Files

| File | Purpose |
|------|---------|
| `README.md` | Project overview, architecture, API endpoints, config reference, quick start |
| `AGENTS.md` | Guidelines for AI agents working in this repository |
| `application.properties` | All runtime configuration with inline comments |
