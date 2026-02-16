# Security Guidelines

## Project Context

Quarkus REST API (Java 21, Gradle wrapper) that caches and indexes Quarkus documentation from GitHub. PostgreSQL-backed indexes. No authentication/authorization -- designed for internal/trusted consumption. The API reads files from a local cache on disk, so filesystem safety is critical.

## Input Validation

All user-supplied input must be validated before use. The project uses a centralized `InputValidator` utility class at `com.fvd.common.validators.InputValidator`.

### Validation Rules

| Parameter | Rules |
|-----------|-------|
| `version` | Non-empty; must match `[a-zA-Z0-9._/-]+`; must not contain `..` |
| `path` | Non-empty; must not contain `..` |
| `filePaths` | Non-empty; each comma-separated entry must not be empty or contain `..` |
| `keywords` | Non-empty |
| `sectionTitle` | Non-empty |
| `limit` | Must be >= 1 and <= configured max |
| `offset` | Must be >= 0 |

### Path Traversal Prevention

- All file path parameters (`path`, `filePaths`, `version`) reject `..` sequences.
- Version strings are restricted to alphanumeric characters plus `.`, `_`, `/`, and `-`.
- These checks prevent directory traversal attacks against the cache filesystem.
- When adding new parameters that resolve to filesystem paths, always validate through `InputValidator` before use.

## Error Handling and Information Disclosure

- Use domain-specific exceptions mapped via `@Provider` `ExceptionMapper` classes.
- Return structured `ProblemDetail` JSON (RFC 9457) -- never expose raw stack traces, internal file paths, or system details.
- Exception-to-status mapping:

| Exception | HTTP Status |
|-----------|-------------|
| `InvalidInputException` | `400 Bad Request` |
| `DocNotFoundException` | `404 Not Found` |
| `UpstreamException` | `502 Bad Gateway` |

- Error messages should describe the problem for the caller without leaking implementation details.

## External API Communication

- GitHub API calls use Quarkus REST Client with `@RegisterRestClient`.
- Base URLs are configured in `application.properties` -- never hardcoded in source.
- REST client URLs are overridden in the `%test` profile to point at WireMock stubs, ensuring no real external calls during tests.
- Follow redirects are enabled for the repository download client (`follow-redirects=true`).
- No API tokens or credentials are currently required; if added in the future, use environment variables or Quarkus config sources -- never commit secrets.

## Data Storage

- PostgreSQL database stores indexes (managed by Quarkus Agroal datasource).
- Cached documentation files are stored on the local filesystem under `${app.cache.dir}/_versions/`.
- No user data or credentials are stored.
- No authentication or authorization is implemented.

## Dependency Management

- Use Quarkus BOM (`enforcedPlatform`) to manage transitive dependency versions consistently.
- Pin non-BOM dependencies to specific versions in `build.gradle`.
- Keep dependencies updated to avoid known vulnerabilities.

## Do Not

- Do not disable or weaken input validation regex patterns on version/path parameters.
- Do not expose raw exception messages, stack traces, or internal paths in API responses.
- Do not hardcode external URLs in source -- use `application.properties` config keys.
- Do not store or log sensitive data (API keys, tokens).
- Do not commit secrets to `application.properties` -- use environment variables.
