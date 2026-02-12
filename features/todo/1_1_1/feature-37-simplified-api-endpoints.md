# Feature 37: Simplified API Endpoints

## Summary

Version 1.1.1 introduces 4 new REST endpoints that replace all existing v1 endpoints. These endpoints are optimized for AI agent consumption, returning structured JSON instead of raw AsciiDoc content.

## User Story

**As an** AI agent consuming Quarkus documentation via MCP  
**I want** structured, searchable documentation endpoints  
**So that** I can efficiently discover, search, and retrieve documentation content without parsing raw AsciiDoc

## Motivation

The existing v1 API returns raw AsciiDoc content which requires client-side parsing. AI agents benefit from pre-structured JSON responses with clear field semantics, enabling faster context building and more accurate search results.

---

## Endpoints

### GET /api/catalog

Lists available subjects (auto-derived categories), extensions, and versions.

#### Request

```
GET /api/catalog?version={version}
```

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `version` | string | No | `main` | Quarkus documentation version |

#### Response

```json
{
  "subjects": [
    {
      "name": "security",
      "displayName": "Security",
      "description": "Authentication, authorization, and security configurations",
      "docCount": 15,
      "keywords": ["oauth", "jwt", "oidc", "rbac", "ssl", "tls"]
    }
  ],
  "extensions": [
    {
      "name": "quarkus-resteasy-reactive",
      "displayName": "RESTEasy Reactive",
      "description": "Reactive REST framework",
      "docCount": 8
    }
  ],
  "versions": ["main", "3.8", "3.7", "3.6"]
}
```

#### OpenAPI Specification

```yaml
/api/catalog:
  get:
    summary: List catalog of subjects, extensions, and versions
    operationId: getCatalog
    tags:
      - Catalog
    parameters:
      - name: version
        in: query
        required: false
        schema:
          type: string
          default: main
    responses:
      '200':
        description: Catalog listing
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/CatalogResponse'
```

#### Acceptance Criteria

- [x] Returns list of subjects derived from document paths
- [x] Returns list of extensions from Antora playbook parsing
- [x] Returns list of available versions
- [x] Each subject includes name, displayName, description, docCount, keywords
- [x] Each extension includes name, displayName, description, docCount
- [x] Default version is `main` when not specified
- [x] Response is cached per version

---

### GET /api/documents

Retrieves full documents as structured JSON. Supports direct path lookup or keyword search.

#### Request

```
GET /api/documents?path={path}&version={version}
GET /api/documents?keywords={keywords}&version={version}&subject={subject}&extension={extension}&limit={limit}
```

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `path` | string | No* | - | Direct document path |
| `keywords` | string | No* | - | Search keywords (comma-separated) |
| `version` | string | No | `main` | Quarkus documentation version |
| `subject` | string | No | - | Filter by subject (search mode only) |
| `extension` | string | No | - | Filter by extension (search mode only) |
| `limit` | integer | No | 20 | Maximum results (search mode only) |
| `offset` | integer | No | 0 | Pagination offset (search mode only) |

*Either `path` or `keywords` must be provided. If both are provided, `path` takes precedence.

#### Response (Single Document - Path Mode)

```json
{
  "title": "Security Overview",
  "description": "Introduction to Quarkus security features",
  "path": "_guides/security-overview.adoc",
  "subject": "security",
  "extension": null,
  "sections": [
    {
      "title": "Authentication",
      "level": 2,
      "content": "Quarkus supports multiple authentication mechanisms...",
      "startLine": 45,
      "endLine": 120
    }
  ],
  "codeBlocks": [
    {
      "language": "java",
      "content": "@RolesAllowed(\"admin\")\npublic class AdminResource { ... }",
      "context": "Role-based access control example",
      "startLine": 78,
      "endLine": 85
    }
  ],
  "matchedKeywords": [],
  "score": null
}
```

#### Response (Search Mode)

```json
{
  "results": [
    {
      "title": "Security Overview",
      "description": "Introduction to Quarkus security features",
      "path": "_guides/security-overview.adoc",
      "subject": "security",
      "extension": null,
      "sections": [...],
      "codeBlocks": [...],
      "matchedKeywords": [
        { "keyword": "oauth", "source": "section", "weight": 5.0 },
        { "keyword": "authentication", "source": "title", "weight": 8.0 }
      ],
      "score": 0.95
    }
  ],
  "totalCount": 15,
  "returnedCount": 10
}
```

#### OpenAPI Specification

```yaml
/api/documents:
  get:
    summary: Retrieve documents by path or search by keywords
    operationId: getDocuments
    tags:
      - Documents
    parameters:
      - name: path
        in: query
        required: false
        schema:
          type: string
      - name: keywords
        in: query
        required: false
        schema:
          type: string
      - name: version
        in: query
        required: false
        schema:
          type: string
          default: main
      - name: subject
        in: query
        required: false
        schema:
          type: string
      - name: extension
        in: query
        required: false
        schema:
          type: string
      - name: limit
        in: query
        required: false
        schema:
          type: integer
          default: 20
      - name: offset
        in: query
        required: false
        schema:
          type: integer
          default: 0
    responses:
      '200':
        description: Document(s) found
        content:
          application/json:
            schema:
              oneOf:
                - $ref: '#/components/schemas/DocumentResponse'
                - $ref: '#/components/schemas/DocumentSearchResponse'
      '400':
        description: Neither path nor keywords provided
      '404':
        description: Document not found (path mode)
```

#### Acceptance Criteria

- [x] Path mode returns single document with full structure
- [x] Search mode returns array of matching documents with scores
- [x] Sections are extracted with title, level, content, startLine, endLine
- [x] Code blocks are extracted with language, content, context, startLine, endLine
- [x] matchedKeywords populated in search mode
- [x] score populated in search mode
- [x] If both `path` and `keywords` provided, `path` takes precedence
- [x] Returns 400 if neither path nor keywords provided
- [x] Returns 404 if path not found
- [x] Filters by subject and extension work correctly
- [x] Limit parameter respected

---

### GET /api/code-samples

Searches code examples by keywords with optional filters.

#### Request

```
GET /api/code-samples?keywords={keywords}&language={language}&subject={subject}&extension={extension}&version={version}&limit={limit}&offset={offset}
```

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `keywords` | string | **Yes** | - | Search keywords (comma-separated) |
| `language` | string | No | - | Filter by programming language |
| `subject` | string | No | - | Filter by subject |
| `extension` | string | No | - | Filter by extension |
| `version` | string | No | `main` | Quarkus documentation version |
| `limit` | integer | No | 20 | Maximum results |
| `offset` | integer | No | 0 | Pagination offset |

#### Response

```json
{
  "results": [
    {
      "language": "java",
      "content": "@Path(\"/hello\")\npublic class GreetingResource {\n    @GET\n    public String hello() {\n        return \"Hello\";\n    }\n}",
      "context": "Basic REST endpoint example",
      "documentPath": "_guides/rest-json.adoc",
      "documentTitle": "Writing REST Services",
      "subject": "rest-apis",
      "extension": "quarkus-resteasy-reactive",
      "matchedKeywords": [
        { "keyword": "rest", "source": "filename", "weight": 10.0 },
        { "keyword": "endpoint", "source": "body", "weight": 1.0 },
        { "keyword": "get", "source": "section", "weight": 5.0 }
      ],
      "score": 0.87,
      "startLine": 45,
      "endLine": 52
    }
  ],
  "totalCount": 45,
  "returnedCount": 20
}
```

#### OpenAPI Specification

```yaml
/api/code-samples:
  get:
    summary: Search code examples by keywords
    operationId: getCodeSamples
    tags:
      - Code Samples
    parameters:
      - name: keywords
        in: query
        required: true
        schema:
          type: string
      - name: language
        in: query
        required: false
        schema:
          type: string
      - name: subject
        in: query
        required: false
        schema:
          type: string
      - name: extension
        in: query
        required: false
        schema:
          type: string
      - name: version
        in: query
        required: false
        schema:
          type: string
          default: main
      - name: limit
        in: query
        required: false
        schema:
          type: integer
          default: 20
      - name: offset
        in: query
        required: false
        schema:
          type: integer
          default: 0
    responses:
      '200':
        description: Matching code samples
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/CodeSampleSearchResponse'
      '400':
        description: Keywords parameter required
```

#### Acceptance Criteria

- [x] Returns 400 if keywords not provided
- [x] Results include full code content
- [x] Results include document context (path, title, subject, extension)
- [x] Language filter works correctly
- [x] Subject filter works correctly
- [x] Extension filter works correctly
- [x] Results sorted by score descending
- [x] startLine and endLine indicate original position in document
- [x] matchedKeywords shows which keywords matched with source and weight

---

### GET /api/search

Quick discovery search returning references (not full content).

#### Request

```
GET /api/search?keywords={keywords}&subject={subject}&extension={extension}&version={version}&limit={limit}&offset={offset}
```

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `keywords` | string | **Yes** | - | Search keywords (comma-separated) |
| `subject` | string | No | - | Filter by subject |
| `extension` | string | No | - | Filter by extension |
| `version` | string | No | `main` | Quarkus documentation version |
| `limit` | integer | No | 20 | Maximum results |
| `offset` | integer | No | 0 | Pagination offset |

#### Response

```json
{
  "results": [
    {
      "path": "_guides/security-overview.adoc",
      "title": "Security Overview",
      "subject": "security",
      "extension": null,
      "score": 0.95,
      "matchedKeywords": [
        { "keyword": "security", "source": "filename", "weight": 10.0 },
        { "keyword": "authentication", "source": "section", "weight": 5.0 },
        { "keyword": "oauth", "source": "body", "weight": 1.0 }
      ],
      "snippet": "...Quarkus provides comprehensive security features including OAuth2, JWT, and OIDC..."
    }
  ],
  "totalCount": 125,
  "returnedCount": 20
}
```

#### OpenAPI Specification

```yaml
/api/search:
  get:
    summary: Quick discovery search
    operationId: search
    tags:
      - Search
    parameters:
      - name: keywords
        in: query
        required: true
        schema:
          type: string
      - name: subject
        in: query
        required: false
        schema:
          type: string
      - name: extension
        in: query
        required: false
        schema:
          type: string
      - name: version
        in: query
        required: false
        schema:
          type: string
          default: main
      - name: limit
        in: query
        required: false
        schema:
          type: integer
          default: 20
      - name: offset
        in: query
        required: false
        schema:
          type: integer
          default: 0
    responses:
      '200':
        description: Search results
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/SearchResponse'
      '400':
        description: Keywords parameter required
```

#### Acceptance Criteria

- [x] Returns 400 if keywords not provided
- [x] Returns lightweight references (no full content)
- [x] Snippet provides contextual preview of match
- [x] Results sorted by score descending
- [x] Subject filter works correctly
- [x] Extension filter works correctly
- [x] Fast response time (< 100ms for typical queries)

---

## Technical Implementation Notes

### Response Structures

All responses use consistent JSON structure:
- Dates formatted as ISO 8601
- Null values omitted (using Jackson `@JsonInclude(NON_NULL)`)
- Arrays never null (empty array if no items)

### Field Derivation

| Field | Source | Notes |
|-------|--------|-------|
| `description` | First paragraph after document title (AsciiDoc preamble) | Extracted at indexing time |
| `subject` | Computed by `SubjectDeriver` from document path/content | Stored in `files.subject` column |

### Caching Strategy

- Catalog cached per version (invalidate on re-index)
- Search results cached with keywords+filters as key
- Document lookups cached by path+version

### Error Handling

| Status | Condition |
|--------|-----------|
| 200 | Success |
| 400 | Missing required parameters |
| 404 | Document not found (path mode only) |
| 500 | Internal server error |

### Performance Targets

| Endpoint | Target Latency (p95) |
|----------|---------------------|
| `/api/catalog` | < 50ms |
| `/api/documents` (path) | < 100ms |
| `/api/documents` (search) | < 200ms |
| `/api/code-samples` | < 200ms |
| `/api/search` | < 100ms |

---

## Dependencies

- Quarkus REST (jakarta.ws.rs)
- OpenAPI annotations (org.eclipse.microprofile.openapi)
- Jackson JSON serialization
- SQLite keyword/code-sample indexes

## Risks

| Risk | Mitigation |
|------|------------|
| Large response sizes | Pagination via limit parameter |
| Slow search on large indexes | Pre-computed keyword weights, caching |
| Breaking change impact | Clear migration documentation |

---

## Implementation Notes

**Implemented:** Thu Feb 12 2026

**Files Created:**

DTOs (com.fvd.api.dto):
- SubjectInfo, ExtensionInfo, CatalogResponse
- SectionInfo, CodeBlockInfo, DocumentResponse, DocumentSearchResponse
- CodeSampleResult, CodeSampleSearchResponse
- SearchResultRef, QuickSearchResponse

Services (com.fvd.api.services):
- CatalogService, DocumentService, CodeSampleService, QuickSearchService

Resources (com.fvd.api.resources):
- CatalogResource (/api/catalog)
- DocumentResource (/api/documents)
- CodeSampleResource (/api/code-samples)
- SearchResource (/api/search)

Tests:
- CatalogResourceTest, DocumentResourceTest, CodeSampleResourceTest, ApiSearchResourceTest

**Review Status:**
- Code Review: PASS (with minor improvements noted)
- Security Review: PASS
- All integration tests passing
