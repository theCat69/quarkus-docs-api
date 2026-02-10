# quarkus-docs-api

REST API for caching, indexing, and searching Quarkus documentation (core and quarkiverse extensions).

## Overview

**quarkus-docs-api** is a Quarkus-based REST API that downloads, caches, and indexes Quarkus documentation from the [quarkusio.github.io](https://github.com/quarkusio/quarkusio.github.io) website repository. It builds keyword, section, code-sample, and full-text indexes in SQLite and exposes them through a search API designed for consumption by AI agents, MCP servers, IDEs, and developer tooling.

The API supports **multi-version** documentation — multiple Quarkus releases can be cached and searched simultaneously. It also ingests **quarkiverse extension docs** by parsing the Antora playbook from [quarkiverse/quarkiverse-docs](https://github.com/quarkiverse/quarkiverse-docs), downloading extension repository zips, and extracting `.adoc` files. All indexes support stemming, prefix matching, fuzzy section title matching (Levenshtein + containment + word overlap), and pagination.

Background jobs handle cache warmup on startup and periodic refresh using SHA-based incremental updates, so only changed files trigger re-indexing.

## Architecture

```
GitHub website repo (quarkusio.github.io)
        │
        ▼
   Zip download ──► Cache extraction ──► SQLite indexing ──► REST API
        │                                     ▲
        │                                     │
Quarkiverse playbook ──► Extension zips ──► Merge & index
```

**Data flow:**
1. A single zip archive is downloaded per version from the GitHub website repository.
2. AsciiDoc files are extracted and cached on disk under `_versions/<version>/guides/`.
3. For the `main` version, the quarkiverse Antora playbook is parsed (YAML) to discover extension repositories. Each extension repo zip is downloaded and `.adoc` files are extracted under `quarkiverse/<ext-name>/`.
4. Three SQLite-backed indexes are built: keyword index, code-sample index, and content (full-text) index.
5. The REST API serves search queries and raw document content from these indexes and the cache.
6. A scheduled job periodically checks SHA hashes and refreshes only changed versions.

**Main packages:** `asciidocs`, `cache`, `common`, `docs`, `github`, `indexs`, `search`, `quarkiverse`.

## API Endpoints

All endpoints return JSON. The `version` parameter is **optional** on every endpoint and defaults to `main` if omitted. The `extension` parameter is an optional filter available on search and doc endpoints.

### Document retrieval

| Method | Path | Summary | Key Parameters |
|--------|------|---------|----------------|
| `GET` | `/api/doc` | Get raw document content | `version`, `path` (required), `extension` |
| `GET` | `/api/index` | List all doc files with SHA hashes | `version` |

### Search

| Method | Path | Summary | Key Parameters |
|--------|------|---------|----------------|
| `GET` | `/api/search/files` | Search files by keywords | `version`, `keywords` (required), `limit`, `offset`, `extension` |
| `GET` | `/api/search/sections` | Search sections by keywords | `version`, `keywords` (required), `filePaths`, `limit`, `offset`, `extension` |
| `GET` | `/api/search/section-content` | Get section content (fuzzy title match) | `version`, `filePath` (required), `sectionTitle` (required) |
| `GET` | `/api/search/code-samples` | Search code samples by keywords | `version`, `keywords` (required), `filePath`, `sectionTitle`, `limit`, `offset`, `extension` |
| `GET` | `/api/search/content` | Full-text search across documents | `version`, `keywords` (required), `filePaths`, `limit`, `offset`, `extension` |
| `GET` | `/api/search/versions` | List all cached versions | _(none)_ |

## Quick Start

### Prerequisites

- **Java 21** (required)
- Gradle wrapper is included — no separate Gradle installation needed

### Run

```bash
git clone <repo-url>
cd quarkus-docs-api
./gradlew quarkusDev
```

The API starts at `http://localhost:8080`. On first startup, the cache warmup job downloads and indexes the configured versions.

### OpenAPI UI

Browse the interactive API docs at: [http://localhost:8080/q/swagger-ui](http://localhost:8080/q/swagger-ui)

### Try it

```bash
# Search for files about security
curl "http://localhost:8080/api/search/files?keywords=security,oidc"

# Get a specific document
curl "http://localhost:8080/api/doc?path=security-overview.adoc"
```

## Configuration

### Core config keys

| Key | Default | Description |
|-----|---------|-------------|
| `app.cache.dir` | `.cache` | Local directory for cached docs and SQLite database |
| `app.github.owner` | `quarkusio` | GitHub organization for the docs repository |
| `app.github.repo` | `quarkusio.github.io` | GitHub repository name (website repo) |
| `app.github.branch` | `main` | Branch to fetch from the website repository |
| `app.versions` | `main` | Comma-separated list of versions to cache on startup |
| `app.refresh.interval` | `6h` | How often to check for doc updates (SHA-based) |
| `app.cache-warmup.full-reset` | _(unset)_ | Set to `true` to force full re-download on startup |
| `app.quarkiverse.enabled` | `true` | Enable quarkiverse extension doc ingestion |
| `app.quarkiverse.playbook-repo` | `quarkiverse/quarkiverse-docs` | Repository containing the Antora playbook |
| `app.quarkiverse.playbook-branch` | `main` | Branch for the playbook repository |
| `app.quarkiverse.download-concurrency` | `4` | Max concurrent extension downloads |
| `quarkus.datasource.db-kind` | `sqlite` | Database type (SQLite) |
| `quarkus.datasource.jdbc.url` | `jdbc:sqlite:.cache/index.db` | SQLite database file location |
| `quarkus.rest-client.github-api-client.url` | `https://api.github.com/repos` | GitHub API base URL |
| `quarkus.rest-client.github-repository-client.url` | `https://github.com` | GitHub repository download base URL |

### Advanced / Search Tuning

All `search.*` keys are configurable via `application.properties` or environment variables.

| Key | Default | Description |
|-----|---------|-------------|
| `search.boost.filename-boost` | `10` | Score boost for filename matches |
| `search.boost.title-boost` | `5` | Score boost for document title matches |
| `search.boost.import-boost` | `5` | Score boost for import statement matches |
| `search.boost.section-title-boost` | `5` | Score boost for section title matches |
| `search.boost.multi-keyword-boost` | `1.5` | Multiplier when multiple keywords match |
| `search.boost.prefix-match-multiplier` | `0.8` | Discount factor for prefix (partial) matches |
| `search.fuzzy.levenshtein-weight` | `0.4` | Weight for Levenshtein similarity in fuzzy matching |
| `search.fuzzy.containment-weight` | `0.35` | Weight for substring containment in fuzzy matching |
| `search.fuzzy.word-overlap-weight` | `0.25` | Weight for word overlap in fuzzy matching |
| `search.fuzzy.default-threshold` | `0.3` | Minimum fuzzy match score to accept |
| `search.fuzzy.containment-partial-threshold` | `0.5` | Threshold for partial containment matches |
| `search.fuzzy.word-overlap-keyword-threshold` | `0.3` | Threshold for word overlap keyword matches |
| `search.index.min-keyword-score` | `2` | Minimum keyword score to include in index |
| `search.index.min-token-length` | `3` | Minimum token length for indexing |
| `search.snippet.context-size` | `100` | Characters of context around content search matches |

## Examples

### Search files by keywords

```bash
curl "http://localhost:8080/api/search/files?keywords=security,oidc&version=3.27"
```

```json
{
  "results": [
    {
      "path": "security-oidc-bearer-token-authentication.adoc",
      "score": 42.5,
      "matchedKeywords": ["security", "oidc"],
      "extension": "quarkus-core"
    }
  ],
  "total": 15,
  "limit": 10,
  "offset": 0,
  "queriedKeywords": ["security", "oidc"],
  "searchTimeMs": 12
}
```

### Search with extension filter

```bash
curl "http://localhost:8080/api/search/files?keywords=openapi&extension=quarkus-openapi-generator"
```

```json
{
  "results": [
    {
      "path": "quarkiverse/quarkus-openapi-generator/docs/modules/ROOT/pages/index.adoc",
      "score": 18.0,
      "matchedKeywords": ["openapi"],
      "extension": "quarkus-openapi-generator"
    }
  ],
  "total": 3,
  "limit": 10,
  "offset": 0,
  "queriedKeywords": ["openapi"],
  "searchTimeMs": 5
}
```

### Get document content

```bash
curl "http://localhost:8080/api/doc?path=security-overview.adoc&version=3.27"
```

```json
{
  "path": "security-overview.adoc",
  "content": "= Quarkus Security overview\n...",
  "format": "asciidoc",
  "extension": "quarkus-core"
}
```

### List cached versions

```bash
curl "http://localhost:8080/api/search/versions"
```

```json
{
  "results": ["main", "3.27", "3.21"]
}
```

## Building & Testing

```bash
# Build (includes tests)
./gradlew build

# Build without tests
./gradlew build -x test

# Run all tests
./gradlew test

# Run a single test class
./gradlew test --tests "com.fvd.search.resources.SearchResourceTest"

# Run in dev mode (live reload)
./gradlew quarkusDev
```

## Technology Stack

- **Quarkus** — Supersonic Subatomic Java Framework
- **Quarkus REST** (Jakarta REST) — HTTP endpoints
- **Quarkus REST Client** — GitHub API integration
- **Quarkus Jackson** — JSON serialization/deserialization
- **Jackson YAML** — Antora playbook parsing
- **Quarkus Scheduler** — Background cache warmup and refresh
- **Quarkus ARC** (CDI) — Dependency injection
- **Quarkus Agroal + SQLite** (quarkiverse JDBC) — Index storage
- **SmallRye Health** — Health checks
- **SmallRye OpenAPI** — API documentation and Swagger UI
- **Lombok** — Boilerplate reduction
- **JUnit 5** — Unit and integration tests
- **Quarkus WireMock** — HTTP stubbing for tests
- **RestAssured** — HTTP test assertions
- **AssertJ** — Fluent test assertions
- **Mockito** — Mocking framework
- **Java 21** — Language level

## License

This project is licensed under the terms specified in the repository.
