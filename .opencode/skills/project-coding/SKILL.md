---
name: project-coding
description: Project-specific coding guidelines, naming conventions, architecture patterns, and code examples
---

# Project Coding Guidelines

This project is a **Quarkus 3.31.2 REST API** (Java 21, Gradle wrapper) that caches and full-text-searches Quarkus documentation from GitHub. PostgreSQL is the backing store, queried with raw `PreparedStatement` (no ORM). Lombok is enabled and encouraged.

---

## Code Style

- **Java 21** — use only finalized Java 21 features. No preview features in production.
- **4-space indentation**, no tabs.
- **One class per file.**
- Opening braces on the same line as the declaration.
- Blank line between import groups and between class-level declarations.
- No trailing whitespace.
- File encoding: UTF-8; prefer ASCII in source files.
- Use `jakarta.*` imports — NOT `javax.*` (Quarkus 3.x requires Jakarta EE 10 namespace).

---

## Naming Conventions

| Element | Convention | Example |
|---------|-----------|---------|
| Packages | lowercase | `com.fvd.search` |
| Classes | PascalCase | `DocChunkStore` |
| Methods | lowerCamelCase | `findByPage` |
| Test classes | suffix `Test` | `SearchResourceTest` |
| Test methods | descriptive verb phrase | `shouldReturnNotFoundWhenDocMissing` |
| Layer suffixes | `Resource`, `Service`, `Store`, `Client` | `SearchResource`, `CacheService`, `DocChunkStore` |

---

## Import Ordering

1. `java.*`
2. `jakarta.*`
3. Third-party (Quarkus, Lombok, Jackson, MicroProfile, AssertJ, Mockito, etc.)
4. Project (`com.fvd.*`)
5. Static imports last (for test fluency: RestAssured, Hamcrest, AssertJ, Mockito)

**No wildcard imports.**

---

## Package Structure

All production sources live under `com.fvd`:

| Subpackage | Purpose |
|------------|---------|
| `api` | REST resources, DTOs, API-layer services |
| `api.resources` | JAX-RS `@Path` endpoints |
| `api.services` | Business logic and orchestration |
| `api.dto` | Request/response data objects |
| `asciidocs` | AsciiDoc parsing and section extraction |
| `cache` | Cache management, warmup, refresh jobs, health checks |
| `common` | Shared exceptions, validators, filters, matchers, utilities |
| `common.exceptions` | Domain exceptions + `@Provider` `ExceptionMapper` classes |
| `common.validators` | Input validation (`InputValidator`) |
| `docs` | Document stores and file-level operations |
| `github` | GitHub API REST clients, zip download |
| `indexs` | Indexing services, `DocChunkStore`, `DocChunk` model, `DocChunkBuilder` |
| `quarkiverse` | Quarkiverse extension ingestion (Antora playbook, zip extractor) |
| `search` | `DocChunkSearchService`, scoring, `SearchConfig` |
| `subject` | Subject classification and derivation |

---

## Dependency Injection

- **`@ApplicationScoped`** is the default CDI scope for all beans.
- Use **`@RequiredArgsConstructor`** (Lombok) for constructor injection — never `@Inject` on fields.
- Never use Spring annotations in a Quarkus project.

```java
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class DocumentService {

    private final DocStore docStore;           // injected via constructor
    private final DocChunkSearchService search;
}
```

---

## Lombok Usage

| Annotation | Use For |
|------------|---------|
| `@RequiredArgsConstructor` | CDI constructor injection in `@ApplicationScoped` beans |
| `@Slf4j` | Logging (replaces manual `Logger` field declaration) |
| `@Value` / `@Data` | DTOs where appropriate |
| `@Builder` | Complex object construction |
| `@AllArgsConstructor` / `@NoArgsConstructor` | DTOs requiring Jackson deserialization |
| `@UtilityClass` | Static utility classes (e.g., `InputValidator`) |

Avoid Lombok on CDI beans in ways that could conflict with proxy generation (e.g., `@EqualsAndHashCode` on proxied beans).

---

## Java 21 Idioms

- Use **records** for private value holders and internal data carriers (e.g., `record ParsedDocument(...) {}`).
- Use **`Optional<T>`** as a return type when a method may legitimately return nothing — never as a field/parameter type.
- Use **text blocks** for multi-line SQL strings.
- Prefer **`var`** for local variables where the type is obvious from the RHS.
- Prefer **`List.of()`**, **`Set.of()`**, **`Map.of()`** for immutable collections.
- Use **Stream API** over imperative loops for data transformation and filtering.
- Use **`SequencedCollection`** API (`getFirst()`, `getLast()`) instead of index-based access.

---

## REST Resources

- Annotate with `@Path`, HTTP method annotations, and `@Produces(MediaType.APPLICATION_JSON)`.
- Use `MediaType` constants — never hardcode content-type strings.
- Keep resource methods as **thin routers**: parse → validate → delegate to service → return response.
- No business logic in resource classes.
- Use `@RequiredArgsConstructor` for DI.
- `version` query parameter defaults to `"main"` on all endpoints.

```java
@Path("/api/search")
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
@Tag(name = "Search", description = "...")
public class SearchResource {

    private final DocChunkSearchService service;

    @GET
    @Operation(summary = "Search documentation chunks")
    @APIResponse(responseCode = "200", ...)
    @APIResponse(responseCode = "400", ...)
    public ChunkSearchResponse search(
            @Parameter(description = "...", required = true, example = "reactive rest")
            @QueryParam("q") String q,
            @Parameter(description = "Quarkus version. Defaults to 'main'.", required = false,
                       example = "main", schema = @Schema(defaultValue = "main"))
            @QueryParam("version") String version) {
        // validate → delegate → return
    }
}
```

---

## OpenAPI Annotations

Every endpoint **must** have:
- `@Operation(summary = "...", description = "...")`
- `@APIResponse` for each HTTP status code returned
- `@Parameter(description, required, example, schema)` on every query parameter
- `@Schema(defaultValue = "main")` on the `version` parameter
- `@Tag` at class level

Every response DTO **must** have:
- `@Schema(description = "...")` on each field

---

## REST Clients

- Define as interfaces with `@RegisterRestClient`.
- Configure base URI via `quarkus.rest-client.<name>.url` in `application.properties`.
- Never hardcode URLs in source code.

---

## Configuration

- Use `application.properties` for all configuration.
- Group by prefix: `app.*`, `search.*`, `quarkus.*`.
- Inject via `@ConfigProperty` or `@ConfigMapping` (prefer `@ConfigMapping` for groups).
- Never hardcode configurable values in source.

---

## Data Access (Store Layer)

- One store class per table/domain.
- Use raw `PreparedStatement` with `?` placeholders — **never** string-concatenate user input into SQL.
- Always use **try-with-resources** for `Connection`, `PreparedStatement`, and `ResultSet`.
- Specify columns explicitly in `SELECT` — never `SELECT *`.
- Wrap `SQLException` in domain-specific `StoreException` — never expose raw `SQLException` to upper layers.
- Use `conn.setAutoCommit(false)` / `conn.commit()` / `conn.rollback()` for transactional operations.

```java
try (Connection conn = dataSource.getConnection();
     PreparedStatement stmt = conn.prepareStatement(sql)) {
    stmt.setString(1, version);
    try (ResultSet rs = stmt.executeQuery()) {
        // map results
    }
} catch (SQLException e) {
    throw new StoreException("Failed to ...", e);
}
```

---

## Error Handling

- Never swallow exceptions.
- Use domain-specific exceptions: `InvalidInputException`, `DocNotFoundException`, `UpstreamException`, `StoreException`.
- Map exceptions to HTTP responses via `@Provider` `ExceptionMapper` classes extending `AbstractProblemDetailMapper<T>`.
- Return structured **`ProblemDetail`** JSON (RFC 9457) with `type`, `title`, `status`, `detail`, `instance`.
- HTTP status mapping:
  - `400 Bad Request` → `InvalidInputException`
  - `404 Not Found` → `DocNotFoundException`
  - `502 Bad Gateway` → `UpstreamException`
- Never expose raw stack traces, internal file paths, or system-level error details in API responses.

```java
@Provider
public class InvalidInputExceptionMapper extends AbstractProblemDetailMapper<InvalidInputException> {

    @Override
    protected Response.Status getStatus() { return Response.Status.BAD_REQUEST; }

    @Override
    protected String getTitle() { return "Bad Request"; }

    @Override
    protected String getDetail(InvalidInputException ex) { return ex.getMessage(); }
}
```

---

## DTOs

- Favor **immutable** DTOs where possible.
- Use `List`/`Set` over arrays for collections.
- Use `String` for IDs unless a stronger type exists.
- Apply `@JsonFilter("fieldSelector")` on API-layer DTOs to support dynamic field selection.
- Apply `@JsonInclude(JsonInclude.Include.NON_NULL)` on DTOs with nullable fields to omit nulls from JSON.
- Use Lombok `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor` to reduce boilerplate.

---

## Patterns & Architecture

The architecture is strictly layered:

```
HTTP → *Resource (JAX-RS, thin router)
         → *Service (business logic, orchestration)
            → *Store (raw JDBC, Agroal DataSource)
            → *Client (MicroProfile REST Client, external API)
```

- Each layer depends only on the layer directly below it.
- Store classes are never called from Resource classes.
- Never mix persistence logic into service or resource classes.
- Private records are acceptable within service classes for internal value carriers.

---

## Writing Tests

- Use **TDD**: write or update tests before implementation.
- See `.opencode/skills/project-test/SKILL.md` and `.code-examples-for-ai/` for test patterns.
