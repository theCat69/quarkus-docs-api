# Feature 41: Breaking Changes & Migration

## Summary

Version 1.1.1 introduces breaking changes to simplify the API and improve AI agent consumption. All existing v1 endpoints are removed and replaced with 4 new endpoints. Response formats change from raw AsciiDoc to structured JSON.

## User Story

**As an** API consumer  
**I want** clear migration guidance  
**So that** I can update my integration to use the new v1.1.1 API

## Motivation

The original v1 API evolved organically, resulting in:
- Too many endpoints with overlapping functionality
- Raw AsciiDoc responses requiring client-side parsing
- Inconsistent response structures
- Complex endpoint discovery

Version 1.1.1 addresses these issues with a clean, purpose-built API.

---

## Removed Endpoints

The following v1 endpoints are **removed** in v1.1.1:

### Documentation Endpoints

| Removed Endpoint | Description |
|------------------|-------------|
| `GET /api/doc` | Get document content by path |

### Search Endpoints

| Removed Endpoint | Description |
|------------------|-------------|
| `GET /api/search/versions` | List available versions |
| `GET /api/search/files` | Search files by keywords |
| `GET /api/search/sections` | Search sections by keywords |
| `GET /api/search/section-content` | Get section content |
| `GET /api/search/code-samples` | Search code samples |

### Index Endpoints

| Removed Endpoint | Description |
|------------------|-------------|
| `GET /api/index` | Trigger index rebuild |

---

## Endpoint Mapping

### Migration Table

| Old Endpoint | New Endpoint | Notes |
|--------------|--------------|-------|
| `GET /api/search/versions` | `GET /api/catalog` | Versions in catalog response |
| `GET /api/doc?path={path}` | `GET /api/documents?path={path}` | Returns structured JSON, not raw AsciiDoc |
| `GET /api/search/files?keywords={kw}` | `GET /api/search?keywords={keywords}` | Returns references with snippets |
| `GET /api/search/sections?keywords={kw}` | `GET /api/documents?keywords={keywords}` | Sections included in document response |
| `GET /api/search/section-content` | `GET /api/documents?path={path}` | Full documents with sections |
| `GET /api/search/code-samples?keywords={kw}` | `GET /api/code-samples?keywords={keywords}` | Structured code sample results |
| `GET /api/index` | Removed | Use scheduled indexing or admin endpoint |

### Detailed Mappings

#### Listing Versions → Catalog

**Before (v1):**
```http
GET /api/search/versions
```
```json
{
  "versions": ["main", "3.8", "3.7"]
}
```

**After (v1.1.1):**
```http
GET /api/catalog?version=3.8
```
```json
{
  "subjects": [
    { "name": "security", "displayName": "Security", "docCount": 15, ... }
  ],
  "extensions": [
    { "name": "quarkus-resteasy", "displayName": "RESTEasy", "docCount": 8, ... }
  ],
  "versions": ["main", "3.8", "3.7"]
}
```

#### Getting Document Content

**Before (v1):**
```http
GET /api/doc?path=_guides/security.adoc&version=3.8
```
```
= Security Overview

This guide covers...

== Authentication

Quarkus supports...
```

**After (v1.1.1):**
```http
GET /api/documents?path=_guides/security.adoc&version=3.8
```
```json
{
  "title": "Security Overview",
  "description": "This guide covers...",
  "path": "_guides/security.adoc",
  "subject": "security",
  "sections": [
    {
      "title": "Authentication",
      "level": 2,
      "content": "Quarkus supports...",
      "startLine": 10,
      "endLine": 45
    }
  ],
  "codeBlocks": [...]
}
```

#### Searching Files

**Before (v1):**
```http
GET /api/search/files?keywords=authentication&version=3.8
```
```json
{
  "results": [
    { "path": "_guides/security.adoc", "score": 0.95 }
  ]
}
```

**After (v1.1.1):**
```http
GET /api/search?keywords=authentication&version=3.8
```
```json
{
  "results": [
    {
      "path": "_guides/security.adoc",
      "title": "Security Overview",
      "subject": "security",
      "score": 0.95,
      "matchedKeywords": [
        { "keyword": "authentication", "source": "section", "weight": 5.0 }
      ],
      "snippet": "...Quarkus supports multiple authentication mechanisms..."
    }
  ],
  "totalCount": 15,
  "returnedCount": 10
}
```

#### Searching Code Samples

**Before (v1):**
```http
GET /api/search/code-samples?keywords=entity,repository&language=java&version=3.8
```
```json
{
  "samples": [
    { "code": "@Entity...", "file": "hibernate.adoc" }
  ]
}
```

**After (v1.1.1):**
```http
GET /api/code-samples?keywords=entity,repository&language=java&version=3.8
```
```json
{
  "results": [
    {
      "language": "java",
      "content": "@Entity\npublic class Person {...}",
      "context": "JPA entity example",
      "documentPath": "_guides/hibernate-orm-panache.adoc",
      "documentTitle": "Simplified Hibernate ORM with Panache",
      "subject": "data-persistence",
      "matchedKeywords": [
        { "keyword": "entity", "source": "body", "weight": 1.0 },
        { "keyword": "repository", "source": "section", "weight": 5.0 }
      ],
      "score": 0.92,
      "startLine": 45,
      "endLine": 58
    }
  ],
  "totalCount": 23,
  "returnedCount": 20
}
```

---

## Response Format Changes

### Raw AsciiDoc → Structured JSON

| Aspect | Before (v1) | After (v1.1.1) |
|--------|-------------|----------------|
| Content format | Raw AsciiDoc text | Parsed JSON structure |
| Sections | Not extracted | Extracted with title, level, content |
| Code blocks | Embedded in AsciiDoc | Extracted with language, context |
| Metadata | Minimal | Rich (subject, extension, keywords) |
| Parsing | Client responsibility | Server-side |

### Score and Match Information

| Aspect | Before (v1) | After (v1.1.1) |
|--------|-------------|----------------|
| Score | Simple relevance | Weighted by keyword location |
| Matched keywords | Not provided | Explicit list with sources |
| Snippets | Not provided | Context snippets in search |

---

## Query Parameter Changes

### Renamed Parameters

| Before | After | Endpoint |
|--------|-------|----------|
| `q` | `keywords` | All search endpoints |
| `lang` | `language` | Code samples |
| `ext` | `extension` | All endpoints |

### New Parameters

| Parameter | Endpoints | Description |
|-----------|-----------|-------------|
| `subject` | documents, code-samples, search | Filter by subject category |
| `limit` | documents, code-samples, search | Result limit (was implicit) |

### Removed Parameters

| Parameter | Reason |
|-----------|--------|
| `format` | Always JSON now |
| `raw` | No raw mode |
| `include-content` | Always included in documents endpoint |

---

## Error Response Changes

### Before (v1)

```json
{
  "error": "Document not found",
  "status": 404
}
```

### After (v1.1.1)

```json
{
  "title": "Not Found",
  "status": 404,
  "detail": "Document not found: _guides/nonexistent.adoc",
  "instance": "/api/documents",
  "timestamp": "2024-01-15T10:30:00Z"
}
```

Error responses now follow RFC 7807 Problem Details format.

---

## Migration Checklist

### For API Consumers

- [ ] Update endpoint URLs per mapping table
- [ ] Change `q` parameter to `keywords`
- [ ] Update response parsing for structured JSON
- [ ] Handle new `subject` field in responses
- [ ] Update error handling for RFC 7807 format
- [ ] Remove any AsciiDoc parsing logic (now server-side)
- [ ] Update tests for new response structures

### For MCP Integrations

- [ ] Update tool definitions for new endpoints
- [ ] Leverage `subject` filtering for targeted queries
- [ ] Use `matchedKeywords` for result explanation
- [ ] Update prompt templates for structured content

---

## Deprecation Timeline

| Version | Status |
|---------|--------|
| v1.0.x | Deprecated (not removed) |
| v1.1.0 | Last version with v1 endpoints |
| v1.1.1 | v1 endpoints removed |

---

## Rollback Considerations

If you need to rollback to v1 API behavior:

1. Pin to version `1.1.0` or earlier
2. v1.1.1 is **not backward compatible**
3. Consider running both versions during transition (different ports)

---

## Support

For migration assistance:
- Review the [Feature 37: Simplified API Endpoints](feature-37-simplified-api-endpoints.md) specification
- Check [example integrations](../examples/) (if available)
- Open an issue for specific migration questions

---

## Acceptance Criteria

- [ ] All v1 endpoints return 404 in v1.1.1
- [ ] New endpoints functional per specifications
- [ ] Migration documentation complete
- [ ] Error responses follow RFC 7807
- [ ] OpenAPI spec updated for new endpoints
