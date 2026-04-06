---
name: project-build
description: Project-specific build commands, prerequisites, environment setup, and CI/CD pipeline
---

# Project Build Guidelines

This project uses **Quarkus 3.31.2** with **Gradle wrapper only** (Groovy DSL). Java 21 is required.

---

## Prerequisites

| Requirement | Version | Notes |
|-------------|---------|-------|
| Java | 21 (LTS) | Source and target compatibility `VERSION_21` |
| Gradle | via wrapper only | Never use a system-installed Gradle |
| PostgreSQL | 14+ | Managed automatically by Quarkus DevServices in dev/test |
| Docker | Any recent version | Required for Quarkus DevServices (PostgreSQL Testcontainers in tests) |

---

## Build Tool

**Always use `./gradlew`** (the Gradle wrapper in the project root). **Never invoke a system-installed `gradle`.**

Wrapper files:
- `gradlew` / `gradlew.bat` — wrapper scripts (committed to VCS)
- `gradle/wrapper/gradle-wrapper.jar` — wrapper bootstrap JAR (committed to VCS)
- `gradle/wrapper/gradle-wrapper.properties` — pinned Gradle version (committed to VCS)

Do not modify wrapper files unless explicitly upgrading the Gradle wrapper version.

---

## Build Commands

| Task | Command |
|------|---------|
| Full build (compile + test) | `./gradlew build` |
| Build without tests | `./gradlew build -x test` |
| Dev mode (live reload) | `./gradlew quarkusDev` |
| Run all tests | `./gradlew test` |
| Run single test class | `./gradlew test --tests "com.fvd.api.resources.DocumentResourceTest"` |
| Run single test method | `./gradlew test --tests "com.fvd.api.resources.DocumentResourceTest.testMethod"` |

**Do not run native tests** (`./gradlew testNative`) unless explicitly requested.

---

## Plugins

| Plugin | Version | Purpose |
|--------|---------|---------|
| `java` | — | Standard Java compilation |
| `io.quarkus` | BOM-managed (3.31.2) | Quarkus framework tasks: `quarkusDev`, `quarkusBuild`, `quarkusTest` |
| `io.freefair.lombok` | 9.2.0 | Lombok annotation processing |

The Quarkus plugin version **must match** the `io.quarkus:quarkus-bom` version in `dependencies`. Version mismatch causes mysterious classpath issues.

---

## Key Runtime Dependencies

- `io.quarkus:quarkus-bom:3.31.2` (BOM via `enforcedPlatform`)
- `io.quarkus:quarkus-rest` — JAX-RS REST endpoints
- `io.quarkus:quarkus-rest-client` + `quarkus-rest-client-jackson` — MicroProfile REST clients
- `io.quarkus:quarkus-rest-jackson` — Jackson JSON serialization
- `io.quarkus:quarkus-smallrye-openapi` — OpenAPI / Swagger UI
- `io.quarkus:quarkus-scheduler` — Periodic cache refresh jobs
- `io.quarkus:quarkus-smallrye-health` — Health probes (`/q/health`)
- `io.quarkus:quarkus-arc` — CDI container
- `io.quarkus:quarkus-agroal` — JDBC connection pooling
- `io.quarkus:quarkus-jdbc-postgresql` — PostgreSQL JDBC driver
- `io.quarkus:quarkus-liquibase` — Schema migrations (runs automatically at startup)
- `io.quarkus:quarkus-container-image-docker` — Docker image build support
- `com.fasterxml.jackson.dataformat:jackson-dataformat-yaml` — Antora playbook parsing
- `commons-io:commons-io:2.21.0` — File utilities

## Key Test Dependencies

- `io.quarkus:quarkus-junit` — Quarkus test framework (`@QuarkusTest`)
- `io.rest-assured:rest-assured` — HTTP integration testing
- `org.assertj:assertj-core:3.27.7` — Fluent assertions
- `org.mockito:mockito-core:5.18.0` + `mockito-junit-jupiter:5.18.0` — Mocking
- `io.quarkiverse.wiremock:quarkus-wiremock:1.5.3` + `quarkus-wiremock-test:1.5.3` — HTTP stubbing

---

## Configuration Profiles

| Profile | Activation | Purpose |
|---------|------------|---------|
| Default | Production | Cache at `.cache/`, PostgreSQL via Agroal datasource |
| `%dev` | `./gradlew quarkusDev` | REST client logging enabled; versions `3.20,3.27,main` cached |
| `%test` | `./gradlew test` | Cache at `build/test-cache`; REST clients → WireMock; scheduler disabled; quarkiverse disabled |

---

## Build Outputs

| Directory | Contents |
|-----------|----------|
| `build/` | Compiled classes and build artifacts (gitignored) |
| `build/test-cache/` | Test profile cache directory |
| `.cache/` | Runtime doc cache (gitignored) |
| `src/main/docker/` | Docker build context |

---

## Container Image

The `quarkus-container-image-docker` extension is present. Build Docker images via Quarkus config:

```
./gradlew build -Dquarkus.container-image.build=true
```

---

## CI/CD

No CI/CD pipeline is currently configured (no `.github/` workflows detected). When adding CI:
- Use `./gradlew build` as the primary build step.
- Ensure Docker is available for Testcontainers (DevServices) to start PostgreSQL in test runs.
- Never run `testNative` in CI unless a GraalVM runner is available.

---

## Do Not

- Do not call system `gradle` — always use `./gradlew`.
- Do not modify Gradle wrapper files unless upgrading.
- Do not run `./gradlew testNative` unless explicitly requested.
- Do not hardcode dependency versions in `build.gradle` that are already managed by the Quarkus BOM.
