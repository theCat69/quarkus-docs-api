# Quarkus Docs API v1.2.0 Feature Specifications

## Overview

Version 1.2.0 focuses on **new API capabilities, performance optimization, and AI/MCP consumer ergonomics**. The 7 features span HTTP error handling fixes, document parsing performance, new discovery/navigation endpoints, batch retrieval, and response customization. Together they deliver correct HTTP status codes for all error cases, sub-millisecond cached document retrieval, machine-readable API discovery, single-request multi-document fetching, keyword-based document navigation, and bandwidth-efficient field selection.

## Release Summary

| Aspect | Description |
|--------|-------------|
| Version | 1.2.0 |
| Type | Bug fixes, performance optimization, new endpoints, API ergonomics |
| Goal | Fix HTTP exception mapping; cache parsed documents; add search syntax, meta/capabilities, batch retrieval, related documents, and field selection endpoints |
| Database | SQLite (unchanged) |
| Breaking Changes | None structural. Behavioral: previously-500 error responses now return correct 4xx status codes |

## Features

### Bug Fixes

| Feature | File | Priority | Description |
|---------|------|----------|-------------|
| [Feature 68: Fix HTTP Exception Mapper Bugs](feature-68-fix-http-exception-mapper-bugs.md) | `feature-68-*.md` | HIGH | Add three new `@Provider` exception mappers for `NotAllowedException` (405), `NotAcceptableException` (406), and `ParamException` (400) so Jakarta RS exceptions return correct 4xx instead of 500 |

### Performance

| Feature | File | Priority | Description |
|---------|------|----------|-------------|
| [Feature 69: Cache Document Parsing Results](feature-69-cache-document-parsing-results.md) | `feature-69-*.md` | HIGH | Add in-memory cache of fully-parsed `DocumentResponse` objects so repeated requests return instantly instead of re-parsing AsciiDoc from disk (3–7s → <50ms) |

### New Endpoints

| Feature | File | Priority | Description |
|---------|------|----------|-------------|
| [Feature 70: Search Syntax Documentation Endpoint](feature-70-search-syntax-documentation-endpoint.md) | `feature-70-*.md` | MEDIUM | Add `GET /api/search/syntax` returning machine-readable JSON describing search capabilities, tokenization rules, scoring behavior, stop words, and query examples |
| [Feature 71: API Meta/Capabilities Endpoint](feature-71-api-meta-capabilities-endpoint.md) | `feature-71-*.md` | MEDIUM | Add `GET /api/meta` returning structured JSON description of the entire API surface: endpoints, parameters, search syntax, filter values, and pagination rules |
| [Feature 73: Related Documents Endpoint](feature-73-related-documents-endpoint.md) | `feature-73-*.md` | MEDIUM | Add `GET /api/documents/related` returning ranked related documents computed from shared keyword overlap for graph-like navigation across the documentation corpus |

### API Ergonomics

| Feature | File | Priority | Description |
|---------|------|----------|-------------|
| [Feature 72: Batch Document Retrieval](feature-72-batch-document-retrieval.md) | `feature-72-*.md` | MEDIUM | Add `POST /api/documents/batch` to fetch multiple documents in a single HTTP request, reducing round-trips from N to 1 with concurrent processing |
| [Feature 74: Response Field Selection](feature-74-response-field-selection.md) | `feature-74-*.md` | MEDIUM | Add optional `fields` query parameter to all endpoints for JSON field filtering, reducing bandwidth and MCP context window token usage |

## Implementation Order

Feature 69 should precede Feature 72 for optimal batch performance (cached documents return in <50ms vs 3–7s per document). Feature 70 should precede Feature 71 since the meta endpoint references search syntax. All other features are independent.

```
Batch 1 — Bug Fixes (implement first):
  Feature 68: Fix HTTP Exception Mapper Bugs (independent)

Batch 2 — Performance (enables batch retrieval):
  Feature 69: Cache Document Parsing Results (should precede 72)

Batch 3 — New Endpoints (coordinate ordering):
  Feature 70: Search Syntax Documentation (should precede 71)
     └── Feature 71: API Meta/Capabilities (references search syntax)
  Feature 73: Related Documents (independent)

Batch 4 — API Ergonomics (independent):
  Feature 72: Batch Document Retrieval (benefits from 69)
  Feature 74: Response Field Selection (independent, cross-cutting)
```

## Impact Summary

| Metric | Change |
|--------|--------|
| Production files modified | ~15 files |
| Production files created | ~15 new files (exception mappers, cache service, new endpoints, DTOs, filter, validator, customizer) |
| Test files modified/created | ~15–20 files |
| API surface changes | 4 new endpoints (`/api/search/syntax`, `/api/meta`, `/api/documents/related`, `/api/documents/batch`), 1 new query parameter (`fields` on all endpoints) |
| Behavioral changes | HTTP error codes corrected (500 → 4xx), document retrieval cached, field selection filtering |

## Testing Strategy

- All existing tests must pass after each feature (`./gradlew test`)
- New unit tests for validators, filters, parsers, and cache logic
- Integration tests verify end-to-end behavior via HTTP endpoints
- Exception mapper tests verify correct status codes and ProblemDetail responses
- Field selection tests verify filtered JSON output and error handling

## Dependencies

- No new external dependencies
- Uses existing: Jackson (`@JsonFilter`, `ObjectMapper`), Lombok (`@UtilityClass`, `@Slf4j`, `@Data`), Jakarta RS (`ContainerResponseFilter`, `@Provider`)
- Feature 72 benefits from Feature 69 for performance
- Feature 71 references Feature 70 for search syntax details

---

END OF FILE
