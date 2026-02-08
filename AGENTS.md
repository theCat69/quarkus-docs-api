# AGENTS.md

This file guides agentic coding in this repository. Follow it exactly.

## Critical rules
** CRITICAL RULES ** 
Those RULES are CRITICAL you must follow them.
- ALWAYS use the question tool to interact with the user 
- NEVER return directly unless you can't use the question tool or if ALL tasks and features are done, reviewed and accepted.
** END CRITICAL RULES **

## Project summary
- Quarkus REST API using Gradle (wrapper only), with OpenAPI annotations.
- Java 21 source/target compatibility.
- Caches Quarkus docs by version and provides search across keyword indexes.
- SQLite-backed keyword and code sample indexes.
- Tests are JUnit 5 with QuarkusTest, RestAssured, AssertJ, Mockito.
- Lombok is available; use it to reduce boilerplate when possible.

## Repository rules
- No Cursor rules found in `.cursor/rules/` or `.cursorrules`.
- No Copilot rules found in `.github/copilot-instructions.md`.

## File navigation
Agent-friendly map of where to look first.

- App entrypoints and resources live in `src/main/java`.
- HTTP tests live in `src/test/java` and use `@QuarkusTest`.
- WireMock stubs and test fixtures live in `src/test/resources`.
- Quarkus config defaults live in `src/main/resources/application.properties`.
- Dev scripts or helpers may exist under `bin/`.
- Feature planning docs are under `features/`.

## File structure (folder guide)
Short descriptions of every folder at repo root and under `src/`.

### Repo root
- `bin`: build/test helper outputs (Gradle default output).
- `build`: Gradle build outputs (generated, reports, classes).
- `features`: planning notes; `done/` and `todo/` track work items.
- `gradle`: Gradle wrapper files and supporting scripts.
- `src`: application and test source sets.
- `.cache`: local tooling cache.
- `.gradle`: local Gradle cache/work state.
- `.idea`: IntelliJ project config.
- `.settings`: IDE/tooling settings.
- `.tmp`: local scratch data.
- `.git`: git metadata.

### src/
- `src/main/java`: production Java code (REST resources, services, DTOs).
- `src/main/resources`: app config and resource files.
- `src/main/docker`: container artifacts (if any).
- `src/main/bin`: main runtime binaries/resources (if any).
- `src/test/java`: JVM unit and Quarkus tests.
- `src/test/resources`: test configs and fixtures.
- `src/test/resources/mappings`: WireMock mappings.
- `src/test/resources/__files`: WireMock response bodies.
- `src/native-test/java`: native-image tests (avoid unless asked).

### src/main/java package map
- `src/main/java/com/fvd/asciidocs`: Asciidoc parsing utilities for tokenization and section extraction.
- `src/main/java/com/fvd/cache`: Cache management and scheduled refresh jobs.
- `src/main/java/com/fvd/common`: Shared exceptions, validators, and error response DTOs.
- `src/main/java/com/fvd/docs`: Docs API resources, doc services, and doc storage.
- `src/main/java/com/fvd/github`: GitHub API client, zip download, and upstream error mapping.
- `src/main/java/com/fvd/indexs`: Indexing services, index stores, and keyword/code-sample index models.
- `src/main/java/com/fvd/search`: Search services and response DTOs for file/section/code-sample searches.

### Common subpackages (generic guide)
- `.../resources`: JAX-RS endpoints and response DTOs.
- `.../services`: Core business logic and orchestration.
- `.../stores`: Persistence/IO layers (cache, filesystem, or external storage).
- `.../clients`: External API clients.
- `.../exceptions`: Domain exceptions and exception mappers.
- `.../validators`: Input validation helpers.
- `.../indexers`: Index builders and indexing helpers.

## Build, lint, and test commands
Use the Gradle wrapper. Do not call system Gradle.

### Build
- Build (includes tests): `./gradlew build`
- Build without tests: `./gradlew build -x test`
- Dev mode: `./gradlew quarkusDev`

### Tests (TDD workflow)
This project is built using TDD. Prefer tight feedback loops.
Make:
- unit tests with AssertJ assertions
- integration tests with `@QuarkusTest` and RestAssured

- All unit tests: `./gradlew test`
- Single test class: `./gradlew test --tests "com.fvd.GreetingResourceTest"`
- Single test method: `./gradlew test --tests "com.fvd.GreetingResourceTest.testHelloEndpoint"`

### Quarkus JVM tests
Use `@QuarkusTest` for JVM integration-style tests only.

- All `@QuarkusTest` tests (same as test task): `./gradlew test`
- Single `@QuarkusTest` class: `./gradlew test --tests "com.fvd.GreetingResourceTest"`

### Native tests
Do not run native tests unless explicitly requested.

- Native tests: `./gradlew testNative`

### Lint/format
No dedicated lint or formatter tasks are configured. Keep formatting
consistent with existing code and Java conventions.

## Required testing scope
- Unit tests and `@QuarkusTest` (JVM) only.
- Do not add or run native tests unless explicitly requested.

## Code style guidelines
Follow existing patterns in this codebase. Keep changes minimal and
consistent.

### Imports
- Use explicit imports; no wildcard imports.
- Order imports by groups: Java, javax/jakarta, third-party, project.
- Keep static imports last.
- Keep static imports for test fluency (RestAssured, Hamcrest).

### Formatting
- 4-space indentation.
- One class per file.
- Opening braces on the same line.
- Blank line between import groups and class definitions.
- Avoid trailing whitespace.

### Lombok
- Use Lombok to reduce boilerplate where possible.
- Prefer `@Value` or `@Data` for DTOs when appropriate.
- Prefer `@Builder` for complex constructors.
- Prefer `@RequiredArgsConstructor` for dependency injection.
- Use `@Slf4j` for logging rather than manual logger fields.

### Types and APIs
- Prefer interfaces for REST clients and use `@RegisterRestClient`.
- Use `Set`/`List` over arrays for collections.
- Use `String` for IDs unless a stronger type exists.
- Favor immutable DTOs when possible.

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
- Quarkus Scheduler, SmallRye Health, OpenAPI.
- Quarkus ARC (CDI).
- Quarkus Agroal + SQLite (quarkiverse JDBC).
- Lombok (io.freefair.lombok plugin).
- WireMock (test support).
- AssertJ, RestAssured, Mockito for testing.

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
- Java 21 is required for compilation; avoid language features not
  supported by the configured toolchain.
- Keep file encodings UTF-8; source files should be ASCII where possible.

## Code examples (from this codebase)

Use these examples as reference for structure, imports, and Lombok usage.

### POJO/DTO example

```java
package com.fvd.search.resources;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
public class SearchResponse<T> {

    public List<T> results;

}
```

### Store example

```java
package com.fvd.docs.stores;

import com.fvd.cache.services.CacheService;
import com.fvd.common.validators.InputValidator;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@ApplicationScoped
@RequiredArgsConstructor
public class DocStore {

    private final CacheService cacheService;

    public Optional<String> read(String version, String filePath) {
        InputValidator.validateVersion(version);
        InputValidator.validatePath(filePath);
        Path docFile = cacheService.versionDir(version).resolve("docs").resolve(filePath);
        if (!Files.exists(docFile)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readString(docFile));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read doc: " + filePath, e);
        }
    }
}
```

### Service example

```java
package com.fvd.search.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fvd.docs.stores.DocStore;
import com.fvd.github.services.ZipDownloadService;
import com.fvd.indexs.indexers.KeywordIndex;
import com.fvd.indexs.indexers.KeywordIndexer;
import com.fvd.indexs.stores.KeywordIndexStore;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class SearchService {

    private final KeywordIndexStore keywordIndexStore;
    private final ObjectMapper objectMapper;
    private final ZipDownloadService zipDownloadService;
    private final KeywordIndexer keywordIndexer;
    private final DocStore docStore;

    public List<FileSearchResult> searchFiles(String version, List<String> keywords) {
        ensureIndex(version);
        KeywordIndex index = loadIndex(version);
        if (index == null) {
            return List.of();
        }
        return List.of();
    }

    private void ensureIndex(String version) {
        Optional<String> existing = keywordIndexStore.read(version);
        if (existing.isPresent()) {
            return;
        }
        if (zipDownloadService == null || keywordIndexer == null || docStore == null) {
            return;
        }
    }

    private KeywordIndex loadIndex(String version) {
        Optional<String> json = keywordIndexStore.read(version);
        if (json.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(json.get(), KeywordIndex.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse keyword index for version: " + version, e);
        }
    }
}
```

### Resource example

```java
package com.fvd.search.resources;

import com.fvd.common.validators.InputValidator;
import com.fvd.search.services.FileSearchResult;
import com.fvd.search.services.SearchService;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.List;

@Path("/api/search")
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class SearchResource {
    private final SearchService searchService;

    @GET
    @Path("/files")
    public SearchResponse<FileSearchResult> searchFiles(@QueryParam("version") String version,
                                                        @QueryParam("keywords") String keywords) {
        InputValidator.validateVersion(version);
        InputValidator.validateKeywords(keywords);
        List<String> keywordList = Arrays.asList(keywords.split(","));
        List<FileSearchResult> results = searchService.searchFiles(version, keywordList);
        return new SearchResponse<>(results);
    }
}
```
