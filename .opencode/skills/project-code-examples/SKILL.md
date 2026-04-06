---
name: project-code-examples
description: Catalog of project code examples — what patterns exist and where to find them in .code-examples-for-ai/
---

# Project Code Examples

These examples demonstrate the coding patterns used in this project. They are extracted from real production source files in this repository and annotated to highlight what to imitate.

## Available Examples

| File | Pattern Demonstrated |
|------|---------------------|
| `resource.md` | JAX-RS Resource: thin router with `@RequiredArgsConstructor`, `@Tag`, `@Operation`, `@APIResponse`, `@Parameter`, `@Schema(defaultValue)`, and delegation to service |
| `service.md` | Application Service: `@ApplicationScoped`, `@Slf4j`, `@RequiredArgsConstructor`, private records as value carriers, `Optional<T>` usage, in-memory LRU cache, `@ConfigProperty` alongside Lombok constructor injection |
| `store.md` | Store (data access): raw `PreparedStatement` with `?` placeholders, try-with-resources for `Connection`/`PreparedStatement`/`ResultSet`, manual transaction with `setAutoCommit(false)`, `StoreException` wrapping, PostgreSQL `text[]` array binding, and batch inserts |
| `test-quarkus.md` | `@QuarkusTest` integration test: `@BeforeEach` seed data, RestAssured `given/when/then` DSL, Hamcrest matchers, static imports, happy path + error response + schema shape assertions |
| `error-handling.md` | Error handling: `AbstractProblemDetailMapper<T>` base class, `@Provider` `ExceptionMapper`, RFC 9457 `ProblemDetail` response shape, and the three standard domain exceptions (`InvalidInputException` → 400, `DocNotFoundException` → 404, `UpstreamException` → 502) |
| `dto.md` | Response DTO: `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@JsonFilter("fieldSelector")`, `@RegisterForReflection`, `@JsonInclude(NON_NULL)`, `@Schema` on every field |

## Location

`.code-examples-for-ai/`

## Maintenance

This index is maintained by the AI. Developers may add entries manually. One file per pattern.

When implementing a new feature that introduces a pattern not yet represented above, create a new `.md` file in `.code-examples-for-ai/` and add an entry to this table.
