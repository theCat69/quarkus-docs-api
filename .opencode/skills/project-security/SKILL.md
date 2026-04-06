---
name: project-security
description: Project-specific security guidelines for secrets, input validation, dependencies, auth, and common vulnerabilities
---

# Project Security Guidelines

This project is a **Quarkus REST API** that reads documentation files from a local filesystem cache and queries PostgreSQL. It has **no authentication/authorization** (designed for internal/trusted consumption). Filesystem safety and SQL injection prevention are the primary security concerns.

---

## Input Validation

All user-supplied input **must** be validated before use. The centralized validator is at `com.fvd.common.validators.InputValidator`.

### Validation Rules

| Parameter | Rules |
|-----------|-------|
| `version` | Non-empty; must match `[a-zA-Z0-9._/-]+`; must not contain `..` |
| `path` | Non-empty; must not contain `..` |
| `filePaths` | Non-empty; each comma-separated entry must not be empty or contain `..` |
| `keywords` | Non-empty |
| `sectionTitle` | Non-empty |
| `limit` | Must be `>= 1` and `<= configured max` |
| `offset` | Must be `>= 0` |

### Path Traversal Prevention

All file path parameters (`path`, `filePaths`, `version`) **reject `..` sequences**. Version strings are restricted to alphanumeric plus `.`, `_`, `/`, `-`.

This prevents directory traversal attacks against the local documentation cache at `${app.cache.dir}/_versions/`.

When adding new parameters that resolve to filesystem paths, **always validate through `InputValidator` before use**.

---

## SQL Injection Prevention

- **Always use `PreparedStatement` with `?` placeholders.** Never concatenate user input into SQL strings.
- This is mandatory for **all** queries that include any user-controlled value (`version`, `query`, `extension`, etc.).
- FTS input (`plainto_tsquery`): validate search terms are non-empty before passing to the query.
- `pg_trgm` fuzzy search: still safe as it operates on indexed data, not raw SQL construction.

```java
// Correct — parameterized query
try (PreparedStatement stmt = conn.prepareStatement(
        "SELECT * FROM doc_chunks WHERE version = ? AND content_tsv @@ plainto_tsquery('english', ?)")) {
    stmt.setString(1, version);
    stmt.setString(2, query);
    // ...
}

// WRONG — never do this
String sql = "SELECT * FROM doc_chunks WHERE version = '" + version + "'"; // SQL injection risk
```

---

## Error Handling and Information Disclosure

- Use domain-specific exceptions mapped via `@Provider` `ExceptionMapper` classes.
- Return structured `ProblemDetail` JSON (RFC 9457) — **never** expose raw stack traces, internal file paths, or system details.
- Exception-to-status mapping:

| Exception | HTTP Status |
|-----------|-------------|
| `InvalidInputException` | `400 Bad Request` |
| `DocNotFoundException` | `404 Not Found` |
| `UpstreamException` | `502 Bad Gateway` |

- Error messages must describe the problem for the caller **without** leaking implementation details (file system paths, SQL errors, internal class names).

---

## External API Communication

- GitHub API calls use Quarkus REST Client with `@RegisterRestClient`.
- Base URLs are configured in `application.properties` — **never hardcoded in source**.
- REST client URLs are overridden in the `%test` profile to point at WireMock stubs — no real external calls during tests.
- `follow-redirects=true` is enabled for the repository download client (needed for GitHub zip redirects).
- No API tokens or credentials are currently required. If added in the future:
  - Use environment variables or Quarkus config sources.
  - **Never commit credentials to source control or `application.properties`.**
  - Reference as `${ENV_VAR_NAME}` in `application.properties`.

---

## Data Storage

- PostgreSQL stores the full-text search index (managed by Quarkus Agroal datasource).
- Cached documentation files are stored on the local filesystem under `${app.cache.dir}/_versions/`.
- No user data or credentials are stored.
- No authentication or authorization is implemented.

### Database User Permissions

The application database user should have only:
- `SELECT`, `INSERT`, `UPDATE`, `DELETE` on required tables.
- No DDL permissions (`CREATE TABLE`, `DROP TABLE`, etc.) in production.
- Liquibase migrations may require a separate user with DDL permissions.

---

## Secrets Management

- Store the database password in an environment variable.
- Reference in `application.properties` as `${DB_PASSWORD}`.
- **Never commit secrets** to `application.properties` or any tracked file.
- `.env` is gitignored — may be used for local development only.

---

## Dependency Security

- Use the Quarkus BOM (`enforcedPlatform`) to manage transitive dependency versions consistently.
- Pin non-BOM dependencies to specific versions in `build.gradle`.
- Keep dependencies updated to avoid known CVEs.
- Verify Gradle wrapper integrity using `distributionSha256Sum` in `gradle-wrapper.properties`.
- Never commit credentials in build files; use `gradle.properties` for local overrides (gitignored).

---

## Common Vulnerabilities Checklist

| Vulnerability | Mitigation |
|--------------|------------|
| SQL Injection | `PreparedStatement` with `?` placeholders — mandatory |
| Path Traversal | `InputValidator` rejects `..` in all path/version params |
| Information Disclosure | `AbstractProblemDetailMapper` strips internal details; `@JsonInclude(NON_NULL)` on responses |
| Dependency vulnerabilities | Quarkus BOM + pinned versions; regular dependency review |
| Hardcoded secrets | Env var substitution in `application.properties`; `.env` gitignored |
| Open redirect / SSRF | REST client base URLs configured in `application.properties`; not derived from user input |

---

## Do Not

- Do not disable or weaken the `InputValidator` regex patterns for `version` or `path` parameters.
- Do not expose raw `SQLException` messages, stack traces, or internal file paths in API responses.
- Do not hardcode external URLs in source — use `application.properties` config keys.
- Do not store or log sensitive data (API keys, tokens, passwords).
- Do not commit secrets to any tracked file.
