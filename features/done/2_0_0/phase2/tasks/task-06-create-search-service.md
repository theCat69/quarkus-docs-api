# Task 06: Create DocChunkSearchService

> **Dependencies**: Task 03 (UrlBuilder), Task 04 (DocChunkStore).

## Summary

Create `DocChunkSearchService` in `com.fvd.search.services` — a thin orchestrator that delegates to `DocChunkStore.search()` for full-text search and falls back to `DocChunkStore.fuzzySearch()` when FTS returns zero results. Returns a new `PaginatedChunkResult` to avoid breaking the existing `PaginatedResult` (still used by other services until Task 10b).

## Changes

### `src/main/java/com/fvd/search/services/ChunkSearchResult.java` *(created)*

Record in `com.fvd.search.services` (matching existing pattern — search result types live here). Fields: `id`, `page`, `title`, `section`, `summary`, `extensions` (List\<String\>), `topics` (List\<String\>), `score` (double), `url`.

### `src/main/java/com/fvd/search/services/PaginatedChunkResult.java` *(created)*

New record in `com.fvd.search.services` with fields: `results` (List\<ChunkSearchResult\>), `total` (int), `limit` (int), `offset` (int). Does **not** replace existing `PaginatedResult<T>` — that stays alive until Task 10b rewrites its consumers.

### `src/main/java/com/fvd/search/services/DocChunkSearchService.java` *(created)*

- `@ApplicationScoped`, `@RequiredArgsConstructor`, injects `DocChunkStore`, `UrlBuilder`
- **search(String query, String version, String extension, int limit, int offset)** → `PaginatedChunkResult`
  1. Call `docChunkStore.search(query, version, extension, limit, offset)`
  2. If results empty and offset == 0, call `docChunkStore.fuzzySearch(query, version, limit)`
  3. Map `ChunkSearchRow` → `ChunkSearchResult` (attach URL via `UrlBuilder`)
  4. Wrap in `PaginatedChunkResult`

## Acceptance Criteria

- [ ] FTS results are returned ranked by `ts_rank` score
- [ ] Fuzzy fallback triggers only when FTS returns 0 results on page 1
- [ ] Each result includes a fully-formed `url` field
- [ ] `PaginatedChunkResult` is a separate type from existing `PaginatedResult`
- [ ] No references to `com.fvd.search.models` (package does not exist)
- [ ] Pagination parameters (`limit`, `offset`) are passed through correctly
- [ ] `./gradlew compileJava` succeeds

## Files

- `src/main/java/com/fvd/search/services/DocChunkSearchService.java` *(created)*
- `src/main/java/com/fvd/search/services/ChunkSearchResult.java` *(created)*
- `src/main/java/com/fvd/search/services/PaginatedChunkResult.java` *(created)*
