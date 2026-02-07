# AGENTS.md

This file guides agentic coding in this repository. Follow it exactly.

## Project summary
- Quarkus REST API using Gradle.
- Java 25 source/target compatibility.
- Tests are JUnit 5 with QuarkusTest and RestAssured.

## Source layout
- Main code: `src/main/java`
- Test code: `src/test/java`
- Config: `src/main/resources/application.properties`

## Build, lint, and test commands
Use the Gradle wrapper. Do not call system Gradle.

### Build
- Build (includes tests): `./gradlew build`
- Build without tests: `./gradlew build -x test`
- Dev mode: `./gradlew quarkusDev`

### Tests (TDD workflow)
This project is built using TDD. Prefer tight feedback loops.
Make : 
- unit tests with assertJ assertions 
- integration tests with @QuarkusTest and rest-assured.

- All unit tests: `./gradlew test`
- Single test class: `./gradlew test --tests "com.fvd.GreetingResourceTest"`
- Single test method: `./gradlew test --tests "com.fvd.GreetingResourceTest.testHelloEndpoint"`

### Quarkus JVM tests
Use @QuarkusTest for JVM integration-style tests only.

- All @QuarkusTest tests (same as test task): `./gradlew test`
- Single @QuarkusTest class: `./gradlew test --tests "com.fvd.GreetingResourceTest"`

### Lint/format
No dedicated lint or formatter tasks are configured. Keep formatting
consistent with existing code and Java conventions.

## Required testing scope
- Unit tests and @QuarkusTest (JVM) only.
- Do not add or run native tests unless explicitly requested.

## Code style guidelines
Follow existing patterns in this codebase. Keep changes minimal and
consistent.

### Imports
- Use explicit imports; no wildcard imports.
- Order imports by groups: static imports last.
- Keep static imports for test fluency (RestAssured, Hamcrest).

### Formatting
- 4-space indentation.
- One class per file.
- Opening braces on the same line.
- Blank line between import groups and class definitions.
- Avoid trailing whitespace.

### Types and APIs
- Prefer interfaces for REST clients and use `@RegisterRestClient`.
- Use `Set`/`List` over arrays for collections.
- Use `String` for IDs unless a stronger type exists.

### Naming
- Packages are lowercase: `com.fvd`.
- Classes are PascalCase; methods are lowerCamelCase.
- Test classes end with `Test`.
- Test methods use descriptive verbs (e.g., `testHelloEndpoint`).

### Error handling
- Keep REST endpoints simple and explicit.
- Prefer clear status codes and error messages.
- Avoid swallowing exceptions; propagate or convert to meaningful HTTP
  responses when adding endpoints.

### REST resources
- Use `@Path`, `@GET`, and `@Produces` in REST endpoints.
- Prefer `MediaType` constants for content types.
- Keep endpoints small and side-effect free unless required.

### REST client usage
- Use `@RegisterRestClient` with a base URI or config key.
- Keep DTOs as simple public fields unless encapsulation is needed.

## Testing guidelines
- Use JUnit 5 (`org.junit.jupiter`).
- Use RestAssured for HTTP testing.
- Assert with Hamcrest or AssertJ; do not mix styles in the same test.
- One behavior per test method; keep tests readable.

## Configuration
- `src/main/resources/application.properties` is the default config.
- Prefer configuration keys in properties rather than hardcoding values.

## Dependencies of note
- Quarkus REST, REST client, Jackson.
- Quarkus Scheduler and SmallRye Health.
- AssertJ and RestAssured for testing.

## Repository rules
- No Cursor rules found in `.cursor/rules/` or `.cursorrules`.
- No Copilot rules found in `.github/copilot-instructions.md`.

## When adding new code
- Keep the public API stable unless a change is requested.
- Match existing error handling style and status codes.
- Update or add tests first (TDD), then implement.

## When editing tests
- Keep tests JVM-only with `@QuarkusTest` when HTTP/DI is required.
- Prefer unit tests for pure logic.
- Keep test data local to the test class.

## Notes for agents
- This is a Gradle project. Use `./gradlew` for all tasks.
- Java 25 is required for compilation; avoid language features not
  supported by the configured toolchain.
- Keep file encodings UTF-8; source files should be ASCII where possible.
