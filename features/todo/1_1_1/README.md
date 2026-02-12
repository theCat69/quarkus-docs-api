# Quarkus Docs API v1.1.1 Feature Specifications

## Overview

Version 1.1.1 introduces a simplified, AI-agent-optimized API that replaces all existing v1 endpoints. This release focuses on structured JSON responses, improved search capabilities, and preparation for PostgreSQL migration in v1.2.0.

## Release Summary

| Aspect | Description |
|--------|-------------|
| Version | 1.1.1 |
| Type | Major refactor with breaking changes |
| Target | AI agents consuming Quarkus documentation via MCP |
| Database | SQLite (PostgreSQL preparation) |

## Features

### Core API Changes

| Feature | File | Description |
|---------|------|-------------|
| [Feature 37: Simplified API Endpoints](feature-37-simplified-api-endpoints.md) | `feature-37-simplified-api-endpoints.md` | 4 new endpoints replacing all existing v1 endpoints |
| [Feature 41: Breaking Changes & Migration](feature-41-breaking-changes-migration.md) | `feature-41-breaking-changes-migration.md` | Migration guide from v1 to v1.1.1 |

### Search and Indexing

| Feature | File | Description |
|---------|------|-------------|
| [Feature 38: Keyword Scoring Hierarchy](feature-38-keyword-scoring-hierarchy.md) | `feature-38-keyword-scoring-hierarchy.md` | Hierarchical keyword weighting system |
| [Feature 39: Subject Derivation](feature-39-subject-derivation.md) | `feature-39-subject-derivation.md` | Auto-categorization of documentation |

### Architecture

| Feature | File | Description |
|---------|------|-------------|
| [Feature 40: Repository Abstraction](feature-40-repository-abstraction.md) | `feature-40-repository-abstraction.md` | Database-agnostic repository pattern |

## New Endpoint Summary

| Endpoint | Purpose | Key Parameters |
|----------|---------|----------------|
| `GET /api/catalog` | List subjects, extensions, versions | `version` (optional) |
| `GET /api/documents` | Retrieve structured documents | `path` or `keywords`, `version` |
| `GET /api/code-samples` | Search code examples | `keywords` (required), `language`, `subject`, `extension` |
| `GET /api/search` | Quick discovery search | `keywords` (required), `subject`, `extension` |

## Migration Impact

This release contains **breaking changes**. All existing v1 endpoints are removed and replaced with the new simplified API. See [feature-41-breaking-changes-migration.md](feature-41-breaking-changes-migration.md) for detailed migration guidance.

## Dependencies

- Java 21
- Quarkus REST with OpenAPI annotations
- SQLite for indexing
- Jackson for JSON serialization

## Timeline

| Milestone | Status |
|-----------|--------|
| v1.1.1 | Simplified API, Repository Abstraction |
| v1.2.0 | PostgreSQL migration (planned) |
