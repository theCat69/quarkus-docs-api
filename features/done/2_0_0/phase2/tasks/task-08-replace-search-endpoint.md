# Task 08: Replace Search Endpoint

> **Dependencies**: Task 06 (DocChunkSearchService), Task 07 (search DTOs).

## Summary

Update `SearchResource` to delegate to `DocChunkSearchService` instead of `QuickSearchService` (at `com.fvd.api.services.QuickSearchService`). This is a **breaking API change**: query param `keywords` → `q`, `subject` is intentionally dropped, `fields` is dropped. Keep and update `POST /api/search`. Delete `CodeSampleResource.java`.

## Changes

### `src/main/java/com/fvd/api/resources/SearchResource.java` *(modified)*

- Remove injection of `QuickSearchService` (at `com.fvd.api.services`)
- Remove injection of `SubjectDeriver` (no more `subject` param)
- Keep injection of `CacheService` (version validation)
- Inject `DocChunkSearchService`
- Update `GET /api/search`:
  - Rename query param `keywords` → `q` (required) — **breaking change**
  - Drop `subject` param — intentionally removed
  - Drop `fields` param — no longer applicable
  - Keep: `version` (optional, default `main`), `extension` (optional), `limit`, `offset`
  - Call `docChunkSearchService.search(q, version, extension, limit, offset)`
  - Return `ChunkSearchResponse` DTO
- Update `POST /api/search`:
  - Update `SearchRequest.java`: rename `keywords` → `q`, remove `subject`
  - Delegate to same `DocChunkSearchService.search()`
  - Return `ChunkSearchResponse` DTO
- Update OpenAPI annotations (`@Operation`, `@APIResponse`, `@Parameter`)

### `src/main/java/com/fvd/api/dto/SearchRequest.java` *(modified)*

- Rename field `keywords` → `q`
- Remove field `subject`

### `src/main/java/com/fvd/api/dto/SearchParams.java` *(modified)*

- Rename `keywords` field/param → `q` (String, not parsed to list)
- Remove `subject` field

### `src/main/java/com/fvd/api/resources/CodeSampleResource.java` *(deleted)*

Note: `QuickSearchService.java` and `CodeSampleService.java` (both at `com.fvd.api.services`) become dead code — deferred to Task 10 for deletion.

## Acceptance Criteria

- [ ] `GET /api/search?q=reactive` returns `ChunkSearchResponse` JSON
- [ ] `keywords` param no longer accepted (replaced by `q`)
- [ ] `subject` param is gone — documented as intentional removal
- [ ] `POST /api/search` accepts updated `SearchRequest` with `q` field
- [ ] `version`, `extension`, `limit`, `offset` query params work correctly
- [ ] `/api/code-samples` returns 404 (resource deleted)
- [ ] OpenAPI spec reflects new endpoint schema
- [ ] `./gradlew compileJava` succeeds

## Files

- `src/main/java/com/fvd/api/resources/SearchResource.java` *(modified)*
- `src/main/java/com/fvd/api/dto/SearchRequest.java` *(modified)*
- `src/main/java/com/fvd/api/dto/SearchParams.java` *(modified)*
- `src/main/java/com/fvd/api/resources/CodeSampleResource.java` *(deleted)*
