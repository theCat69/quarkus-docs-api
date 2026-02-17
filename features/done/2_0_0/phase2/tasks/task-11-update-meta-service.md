# Task 11: Update MetaResource / MetaService

> **Dependencies**: Task 08 (search endpoint replaced, CodeSampleResource deleted).

## Summary

Update `MetaService` to reflect the new unified search endpoint. Document the `keywords` → `q` rename. Update POST endpoint description (kept, with new params). Update `buildSearchSyntax()` and `SearchSyntaxResource` to describe PostgreSQL FTS behavior instead of SQLite matching.

## Changes

### `src/main/java/com/fvd/api/services/MetaService.java` *(modified)*

- Remove `buildCodeSamplesEndpoint()` method (or equivalent) that describes `/api/code-samples`
- Update `buildSearchEndpoint()` to describe unified `/api/search`:
  - Query params: `q` (was `keywords`), `version`, `extension`, `limit`, `offset`
  - Note: `subject` param removed, `fields` param removed
  - Response: `ChunkSearchResponse` with `results[]` containing `id`, `page`, `title`, `section`, `summary`, `extensions`, `topics`, `score`, `url`
- Update POST `/api/search` description: same params as GET, via JSON body with `q` field
- Update endpoint count / capability list if hardcoded
- Note: `/api/documents/related` endpoint description may need updating after Task 10b

### `src/main/java/com/fvd/api/resources/MetaResource.java` *(modified if needed)*

- Update response structure if `MetaService` changes affect the return type
- Verify OpenAPI annotations are accurate

### `src/main/java/com/fvd/api/services/MetaService.java` — `buildSearchSyntax()` *(modified)*

Update the search syntax description to reflect PostgreSQL FTS:
- `plainto_tsquery` for full-text search (no special operators needed)
- `pg_trgm` fuzzy fallback for typo tolerance
- Extension filtering via `extensions @> ARRAY[?]`

### `src/main/java/com/fvd/api/resources/SearchSyntaxResource.java` *(modified)*

- Update the `/api/search/syntax` endpoint response to describe PostgreSQL FTS behavior
- Remove references to SQLite keyword matching / in-memory scoring

## Acceptance Criteria

- [ ] `GET /api/meta` does not mention `/api/code-samples`
- [ ] `GET /api/meta` describes `/api/search` with `q` param (not `keywords`)
- [ ] `GET /api/meta` describes POST `/api/search` with updated params
- [ ] `GET /api/search/syntax` describes PostgreSQL FTS behavior
- [ ] No references to `CodeSampleResource` or `CodeSampleService` in meta classes
- [ ] No references to SQLite matching in syntax description
- [ ] `./gradlew compileJava` succeeds

## Files

- `src/main/java/com/fvd/api/services/MetaService.java` *(modified)*
- `src/main/java/com/fvd/api/resources/MetaResource.java` *(modified if needed)*
- `src/main/java/com/fvd/api/resources/SearchSyntaxResource.java` *(modified)*
