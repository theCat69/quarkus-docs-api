# quarkus-docs-api

REST API for caching, indexing, and searching Quarkus documentation (core and quarkiverse extensions).

## Overview

**quarkus-docs-api** is a Quarkus-based REST API that downloads, caches, and indexes Quarkus documentation from the [quarkusio.github.io](https://github.com/quarkusio/quarkusio.github.io) website repository. It builds keyword and code-sample indexes in SQLite and exposes them through a search API designed for consumption by AI agents, MCP servers, IDEs, and developer tooling.

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
4. Two SQLite-backed indexes are built: keyword index and code-sample index.
5. The REST API serves search queries and raw document content from these indexes and the cache.
6. A scheduled job periodically checks SHA hashes and refreshes only changed versions.

**Main packages:** `api`, `asciidocs`, `cache`, `common`, `docs`, `github`, `indexs`, `quarkiverse`, `search`, `subject`.

## API Endpoints

All endpoints return JSON. The `version` parameter is **optional** on every endpoint and defaults to `main` if omitted. The `fields` parameter is **optional** on all data endpoints and accepts a comma-separated list of field names to include in each result item (e.g., `fields=title,path,score`). When omitted, all fields are returned. Invalid field names return `400` with the list of available fields. Invalid `version` or `subject` values return `400` with a list of valid options.

### Documents

| Method | Path | Summary | Key Parameters |
|--------|------|---------|----------------|
| `GET` | `/api/documents` | Get document by path or search by keywords | `version`, `path`, `keywords` (at least one of `path`/`keywords` required), `subject`, `extension`, `brief`, `fields`, `limit`, `offset` |
| `GET` | `/api/documents/related` | Find documents related to a given document (ranked by keyword overlap similarity) | `version`, `path` (required), `fields`, `limit` |
| `POST` | `/api/documents/batch` | Retrieve multiple documents by path in a single request | JSON body: `version`, `paths` (required, max `app.batch.max-size`), `brief`, `fields` |

- **Path mode:** provide `path` to retrieve a single document with full structured content (sections, code blocks).
- **Search mode:** provide `keywords` to search documents by relevance. Keyword searches default to `brief=true` (lightweight metadata only — title, description, subject, score — no sections or code blocks). Set `brief=false` for full content, which is capped at 5 results; a `warning` field is included in the response when results are limited by this cap.

### Quick Search

| Method | Path | Summary | Key Parameters |
|--------|------|---------|----------------|
| `GET` | `/api/search` | Quick discovery search returning lightweight references | `version`, `keywords` (required), `subject`, `extension`, `fields`, `limit`, `offset` |
| `GET` | `/api/search/syntax` | Returns search syntax documentation (operators, examples, tips) | _(none)_ |

### Code Samples

| Method | Path | Summary | Key Parameters |
|--------|------|---------|----------------|
| `GET` | `/api/code-samples` | Search code samples by keywords | `version`, `keywords` (required), `language`, `subject`, `extension`, `fields`, `limit`, `offset` |

### Catalog

| Method | Path | Summary | Key Parameters |
|--------|------|---------|----------------|
| `GET` | `/api/catalog` | List available subjects, extensions, and versions | `version`, `fields` |

### Meta

| Method | Path | Summary | Key Parameters |
|--------|------|---------|----------------|
| `GET` | `/api/meta` | API capabilities and self-discovery for AI agents | _(none)_ |

### Status

| Method | Path | Summary | Key Parameters |
|--------|------|---------|----------------|
| `GET` | `/api/status` | Readiness and warmup status. Returns `200` when ready, `503` during warmup | _(none)_ |

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

The API starts at `http://localhost:8080`. On first startup, the cache warmup job downloads and indexes the configured versions. The API returns `503` at `/api/status` until warmup completes.

### OpenAPI UI

Browse the interactive API docs at: [http://localhost:8080/q/swagger-ui](http://localhost:8080/q/swagger-ui)

### Try it

```bash
# Search for documents about security (brief by default)
curl "http://localhost:8080/api/documents?keywords=security+oidc"

# Full-content search (capped at 5 results)
curl "http://localhost:8080/api/documents?keywords=security+oidc&brief=false"

# Get a specific document by path
curl "http://localhost:8080/api/documents?path=security-overview.adoc"

# Browse available subjects, extensions, and versions
curl "http://localhost:8080/api/catalog"
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
| `app.document-cache.enabled` | `true` | Enable in-memory caching of parsed document results |
| `app.batch.max-size` | `10` | Maximum number of document paths per batch request |
| `app.cache-warmup.full-reset` | _(unset)_ | Set to `true` to force full re-download on startup |
| `app.quarkiverse.enabled` | `true` | Enable quarkiverse extension doc ingestion |
| `app.quarkiverse.playbook-repo` | `quarkiverse/quarkiverse-docs` | Repository containing the Antora playbook |
| `app.quarkiverse.playbook-branch` | `main` | Branch for the playbook repository |
| `app.quarkiverse.download-concurrency` | `4` | Max concurrent extension downloads |
| `app.cache.http.max-age.versioned` | `3600` | HTTP Cache-Control max-age for versioned content (seconds) |
| `app.cache.http.max-age.main` | `900` | HTTP Cache-Control max-age for main/latest content (seconds) |
| `app.cache.http.max-age.catalog` | `1800` | HTTP Cache-Control max-age for catalog responses (seconds) |
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
| `search.related.default-limit` | `5` | Default number of related documents to return |
| `search.related.max-limit` | `20` | Maximum allowed limit for related documents |
| `search.related.min-similarity` | `0.05` | Minimum similarity score to include a related document |
| `search.related.max-shared-keywords` | `10` | Maximum shared keywords to consider for similarity |
| `search.annotation-boost` | _(unset)_ | Score boost for annotation matches |
| `search.annotation-packages` | _(unset)_ | Annotation packages to boost during indexing |

## Examples

### Search documents by keywords

```bash
curl "http://localhost:8080/api/documents?keywords=security+oidc&version=main"
```

```json
{
  "results": [
    {
      "path": "security-oidc-bearer-token-authentication.adoc",
      "title": "OIDC Bearer Token Authentication",
      "description": "How to protect service applications using OIDC Bearer Token Authentication...",
      "subject": "security",
      "extension": "quarkus-core",
      "matchedKeywords": ["security", "oidc"],
      "score": 42.5
    }
  ],
  "totalCount": 15,
  "returnedCount": 15,
  "limit": 20,
  "offset": 0,
  "hasMore": false,
  "queriedKeywords": ["security", "oidc"],
  "searchTimeMs": 12
}
```

Keyword searches default to `brief=true` — null fields like `sections` and `codeBlocks` are omitted from the JSON output.

### Full-content search (brief=false)

```bash
# Full content is capped at 5 results for performance
curl "http://localhost:8080/api/documents?keywords=security+oidc&brief=false"
```

```json
{
  "results": [
    {
      "path": "security-oidc-bearer-token-authentication.adoc",
      "title": "OIDC Bearer Token Authentication",
      "description": "How to protect service applications using OIDC Bearer Token Authentication...",
      "subject": "security",
      "extension": "quarkus-core",
      "matchedKeywords": ["security", "oidc"],
      "score": 42.5,
      "sections": [ "..." ],
      "codeBlocks": [ "..." ]
    }
  ],
  "totalCount": 15,
  "returnedCount": 5,
  "limit": 5,
  "offset": 0,
  "hasMore": true,
  "warning": "Full-content results limited to 5 for performance. Use brief=true (default) for more results.",
  "queriedKeywords": ["security", "oidc"],
  "searchTimeMs": 45
}
```

### Brief search (lightweight discovery)

```bash
# brief=true is now the default for keyword searches; this is equivalent to omitting the parameter
curl "http://localhost:8080/api/documents?keywords=security+oidc&brief=true"
```

```json
{
  "results": [
    {
      "path": "security-oidc-bearer-token-authentication.adoc",
      "title": "OIDC Bearer Token Authentication",
      "description": "How to protect service applications using OIDC Bearer Token Authentication...",
      "subject": "security",
      "extension": "quarkus-core",
      "matchedKeywords": ["security", "oidc"],
      "score": 42.5
    }
  ],
  "totalCount": 15,
  "returnedCount": 15,
  "limit": 20,
  "offset": 0,
  "hasMore": false,
  "queriedKeywords": ["security", "oidc"],
  "searchTimeMs": 8
}
```

### Quick discovery search

```bash
curl "http://localhost:8080/api/search?keywords=security+oidc"
```

### Search code samples

```bash
curl "http://localhost:8080/api/code-samples?keywords=rest+endpoint&language=java"
```

### Get document content by path

```bash
curl "http://localhost:8080/api/documents?path=security-overview.adoc&version=main"
```

### Batch retrieve multiple documents

```bash
curl -X POST "http://localhost:8080/api/documents/batch" \
  -H "Content-Type: application/json" \
  -d '{"paths": ["security-overview.adoc", "config-reference.adoc"], "version": "main", "brief": false}'
```

### Find related documents

```bash
curl "http://localhost:8080/api/documents/related?path=security-overview.adoc&limit=5"
```

### List catalog (subjects, extensions, versions)

```bash
curl "http://localhost:8080/api/catalog?version=main"
```

```json
{
  "subjects": [
    { "name": "security", "description": "Security-related guides" }
  ],
  "extensions": [
    { "name": "quarkus-core", "description": "Core Quarkus documentation" }
  ],
  "versions": ["main", "3.27", "3.21"]
}
```

### Field selection (reduce response size)

```bash
# Search returning only title and path (saves tokens for AI agents)
curl "http://localhost:8080/api/search?keywords=security&fields=title,path"

# Code samples returning only content and language
curl "http://localhost:8080/api/code-samples?keywords=rest+endpoint&fields=content,language"
```

Envelope fields (`results`, `totalCount`, `returnedCount`, `offset`, `limit`, `hasMore`) are always present in paginated responses — `fields` filters only the item-level properties inside each result.

### Get API capabilities and self-discovery information

```bash
curl "http://localhost:8080/api/meta"
```

### Get search syntax documentation

```bash
curl "http://localhost:8080/api/search/syntax"
```

### Check API readiness and warmup status

```bash
curl "http://localhost:8080/api/status"
```

When the API is ready:

```json
{
  "ready": true,
  "cachedVersions": ["main", "3.27"],
  "warmupProgress": {
    "completed": 2,
    "total": 2,
    "versionsCompleted": ["main", "3.27"]
  }
}
```

During warmup:

```json
{
  "ready": false,
  "cachedVersions": [],
  "warmupProgress": {
    "completed": 1,
    "total": 3,
    "versionsCompleted": ["3.20"],
    "currentVersion": "3.27"
  }
}
```

## HTTP Caching

The API adds `Cache-Control` and `ETag` headers to all `GET` responses. Clients can use conditional requests (`If-None-Match`) to avoid re-downloading unchanged data and save bandwidth.

### Cache duration tiers

| Tier | Max-Age | Applies to |
|------|---------|------------|
| Versioned | `3600s` (1 hour) | Requests with an explicit version (e.g., `version=3.27`) — content is immutable once released |
| Main | `900s` (15 minutes) | Requests for `main` or when `version` is omitted — content changes with upstream commits |
| Catalog | `1800s` (30 minutes) | `/api/catalog` responses — version/subject lists change infrequently |
| Status | `no-cache` | `/api/status` — always reflects real-time readiness |
| Meta | self-managed | `/api/meta` — static payload, long-lived ETag |

### Conditional GET with ETag

Responses include an `ETag` header. On subsequent requests, send `If-None-Match` with the previous ETag value to receive `304 Not Modified` when the content has not changed.

```bash
# 1. First request — returns 200 with an ETag
curl -i "http://localhost:8080/api/documents?keywords=security&version=3.27"
# HTTP/1.1 200 OK
# Cache-Control: public, max-age=3600
# ETag: "a1b2c3d4"

# 2. Conditional request — returns 304 if unchanged
curl -i -H 'If-None-Match: "a1b2c3d4"' \
  "http://localhost:8080/api/documents?keywords=security&version=3.27"
# HTTP/1.1 304 Not Modified
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
./gradlew test --tests "com.fvd.api.resources.DocumentResourceTest"

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
