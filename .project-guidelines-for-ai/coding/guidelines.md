# Coding Guidelines

## Project Context

Quarkus REST API (Java 21, Gradle wrapper) that caches and indexes Quarkus documentation from GitHub. PostgreSQL-backed full-text search using `doc_chunks` table with `tsvector`. Lombok enabled. Uses Jakarta REST, Quarkus ARC (CDI), MicroProfile OpenAPI, and Jackson.

## General Principles

- Follow existing patterns in this codebase. Keep changes minimal and consistent.
- Keep the public API stable unless a change is explicitly requested.
- Match existing error handling style and status codes.
- Use TDD: write or update tests first, then implement.

## Language

- Java 21 -- do not use features beyond Java 21.
- File encodings must be UTF-8; prefer ASCII in source files.

## Formatting

- 4-space indentation (no tabs).
- One class per file.
- Opening braces on the same line as the declaration.
- Blank line between import groups and class definitions.
- No trailing whitespace.

## Imports

- Use explicit imports; **no wildcard imports**.
- Order by groups: `java.*`, `javax/jakarta.*`, third-party, project (`com.fvd.*`).
- Static imports come last.
- In tests, use static imports for fluency (RestAssured, Hamcrest, AssertJ).

## Naming

- Packages: lowercase (`com.fvd`).
- Classes: `PascalCase`.
- Methods: `lowerCamelCase`.
- Test classes: suffix with `Test` (e.g., `SearchResourceTest`).
- Test methods: descriptive verbs (e.g., `testHelloEndpoint`, `shouldReturnNotFoundWhenDocMissing`).

## Lombok

Lombok is available and encouraged to reduce boilerplate:

| Annotation | Use For |
|------------|---------|
| `@RequiredArgsConstructor` | CDI constructor injection in `@ApplicationScoped` beans |
| `@Value` or `@Data` | DTOs where appropriate |
| `@Builder` | Complex constructors |
| `@Slf4j` | Logging (instead of manual `Logger` fields) |
| `@AllArgsConstructor` / `@NoArgsConstructor` | DTOs needing Jackson deserialization |
| `@UtilityClass` | Static utility classes (e.g., `InputValidator`) |

## Package Structure

Follow the established layout under `com.fvd`:

| Subpackage | Purpose |
|------------|---------|
| `api` | Public REST resources, DTOs, and services for the API layer |
| `asciidocs` | AsciiDoc parsing and section extraction |
| `cache` | Cache management, warmup, and refresh jobs |
| `common` | Shared exceptions, validators, error DTOs, matchers, utilities |
| `docs` | Document stores and file-level operations |
| `github` | GitHub API clients, zip download, upstream errors |
| `indexs` | Indexing services, stores, doc chunk builder and models |
| `quarkiverse` | Quarkiverse extension ingestion (models, parser, services) |
| `search` | Search services (DocChunkSearchService), scoring via PostgreSQL |
| `subject` | Subject classification and derivation |

### Subpackage Conventions

- `.../resources` -- JAX-RS endpoints and response DTOs.
- `.../services` -- business logic and orchestration.
- `.../stores` -- persistence/IO (cache, filesystem, PostgreSQL).
- `.../clients` -- external API clients.
- `.../exceptions` -- domain exceptions and `@Provider` exception mappers.
- `.../validators` -- input validation helpers.
- `.../indexers` -- index builders and indexing logic.

## REST Resources

- Annotate with `@Path`, `@GET`, `@Produces(MediaType.APPLICATION_JSON)`.
- Use `MediaType` constants -- never hardcode content-type strings.
- Keep endpoints small and side-effect free unless required.
- Use `@Parameter` and `@Schema` from MicroProfile OpenAPI on every query parameter.
- Use `@RequiredArgsConstructor` for DI instead of `@Inject` on fields.

## REST Clients

- Define as interfaces with `@RegisterRestClient`.
- Configure base URI via `quarkus.rest-client.<name>.url` in `application.properties`.
- Keep client DTOs as simple public-field classes unless encapsulation is needed.

## DTOs

- Favor immutable DTOs when possible.
- Use `Set`/`List` over arrays for collections.
- Use `String` for IDs unless a stronger type exists.
- Use Lombok annotations to reduce boilerplate.

## Error Handling

- Never swallow exceptions -- propagate or convert to meaningful HTTP responses.
- Use domain-specific exceptions: `InvalidInputException`, `DocNotFoundException`, `UpstreamException`.
- Map exceptions to HTTP responses via `@Provider` `ExceptionMapper` classes.
- Return structured `ProblemDetail` JSON (RFC 9457) with `type`, `title`, `status`, `detail`, and `instance` fields.
- HTTP status codes: `400` for bad input, `404` for not found, `502` for upstream failures.

## Configuration

- Use `application.properties` for all configuration -- do not hardcode values.
- Group config by prefix: `app.*`, `search.*`, `quarkus.*`.
- Use Quarkus `@ConfigProperty` or `@ConfigMapping` for injection.
- The `version` query parameter defaults to `main` on all endpoints.

## Writing Tests

### Test Types

- **Unit tests**: for pure logic (parsing, validation, scoring, indexing). Plain JUnit 5, no CDI. Use AssertJ for assertions and Mockito for dependency isolation.
- **Integration tests** (`@QuarkusTest`): when HTTP endpoints, CDI injection, or configuration are needed. Use RestAssured for HTTP assertions.

### Test Structure

- Test classes live in `src/test/java`.
- Test classes end with `Test` (e.g., `SearchResourceTest`).
- Test methods use descriptive names (e.g., `testHelloEndpoint`, `shouldReturnBadRequestForEmptyKeywords`).
- One behavior per test method. Keep tests focused and readable.
- Keep test data local to the test class -- do not share mutable state between tests.
- Do not mix assertion styles within the same test class -- pick AssertJ or Hamcrest and stay consistent.

### WireMock

- Stub mappings: `src/test/resources/mappings/`.
- Response bodies: `src/test/resources/__files/`.
- WireMock port is dynamic, injected via `${quarkus.wiremock.devservices.port}`.
- Use WireMock for all external API stubbing (GitHub API and repository clients). Never make real external calls in tests.

### Static Imports

Use static imports for test fluency:

```java
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
```

### Assertion Patterns

RestAssured (integration tests):

```java
given()
    .queryParam("keywords", "security,oidc")
    .queryParam("version", "main")
.when()
    .get("/api/search/files")
.then()
    .statusCode(200)
    .body("results.size()", greaterThan(0));
```

AssertJ (unit tests):

```java
assertThat(result).isNotNull();
assertThat(result.score).isGreaterThan(0.0);
assertThat(result.matchedKeywords).contains("security");
```

## Code Examples

See `coding/code-examples/` for reference implementations:
- `pojo-dto-example.md` -- DTO with Lombok.
- `store-example.md` -- persistence layer with `@ApplicationScoped`.
- `service-example.md` -- business logic with caching.
- `resource-example.md` -- JAX-RS endpoint with OpenAPI.
