# Building Guidelines

## Project Context

Quarkus REST API (Java 21, Gradle wrapper) that caches and indexes Quarkus documentation from GitHub. SQLite-backed indexes. Lombok enabled.

## Build Tool

- Always use `./gradlew` (the Gradle wrapper). Never invoke a system-installed Gradle.
- Wrapper files live under `gradle/`.
- Do not modify wrapper files unless upgrading the wrapper version.

## Build Commands

| Task | Command |
|------|---------|
| Full build (with tests) | `./gradlew build` |
| Build without tests | `./gradlew build -x test` |
| Dev mode (live reload) | `./gradlew quarkusDev` |

## Java Version

- Java 21 is required. Source and target compatibility are both `VERSION_21`.
- Compiler encoding is UTF-8 with the `-parameters` flag enabled.

## Plugins

| Plugin | Version | Purpose |
|--------|---------|---------|
| `java` | — | Standard Java compilation |
| `io.quarkus` | BOM-managed | Quarkus framework |
| `io.freefair.lombok` | 9.2.0 | Lombok annotation processing |

## Dependencies

### Runtime

- Quarkus REST, REST Client, REST Client Jackson, REST Jackson.
- Quarkus Scheduler, SmallRye Health, SmallRye OpenAPI.
- Quarkus ARC (CDI), Agroal (connection pooling).
- Quarkiverse JDBC SQLite (`quarkus-jdbc-sqlite:3.0.11`).
- Jackson YAML (`jackson-dataformat-yaml`) for Antora playbook parsing.
- Commons IO (`commons-io:2.21.0`).
- Quarkus Container Image Docker.

### Test

- `quarkus-junit` — Quarkus test framework.
- RestAssured — HTTP integration tests.
- AssertJ (`3.27.7`) — fluent assertions.
- Mockito (`5.18.0`) + mockito-junit-jupiter — mocking.
- Quarkiverse WireMock (`1.5.3`) — HTTP stubbing.

## Configuration Profiles

| Profile | Purpose |
|---------|---------|
| Default | Production-like settings. Cache at `.cache/`, SQLite at `.cache/index.db`. |
| `%dev` | REST client logging enabled, WireMock dev services disabled, caches versions `3.20,3.27,main`. |
| `%test` | Cache at `build/test-cache`, REST clients pointed at WireMock, scheduler disabled, quarkiverse disabled. |

## Build Outputs

| Directory | Contents |
|-----------|----------|
| `build/` | Compiled classes and build artifacts |
| `build/test-cache/` | Test profile cache and SQLite database |
| `.cache/` | Runtime cache (docs, indexes, SQLite database) |

## Container Image

The `quarkus-container-image-docker` extension is included. Build Docker images via Quarkus config properties (e.g., `quarkus.container-image.build=true`).

## Do Not

- Do not run native tests (`./gradlew testNative`) unless explicitly requested.
- Do not call system Gradle.
- Do not modify Gradle wrapper files unless upgrading.
